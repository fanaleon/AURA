package app.slop

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente de la API de Gemini (generateContent), que tiene plan gratuito.
 *
 * El plan gratis no incluye la búsqueda de Google, así que internet se resuelve con una función
 * propia: Gemini pide "web_search", la app busca en Tavily y le devuelve los resultados.
 * Solo usa HttpURLConnection y org.json (vienen con Android).
 */
object GeminiApi {
    const val MODEL_SMART = "gemini-3.8-flash"
    const val MODEL_FAST = "gemini-3.5-flash-lite"

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    /** Dirección de la API. Solo se cambia en pruebas. */
    @Volatile
    var baseUrl: String = BASE

    /** Reloj en milisegundos. Solo se cambia en pruebas. */
    @Volatile
    var clock: () -> Long = { System.nanoTime() / 1_000_000L }

    private const val TOOL = "web_search"
    private const val MAX_SEARCHES = 3
    private const val MAX_REQUESTS = 4          // hasta 3 con búsquedas + la respuesta final
    private const val MAX_TOKENS = 2048         // incluye lo que el modelo gasta en razonar
    private const val THINKING = "low"          // respuestas rápidas: es una charla por voz
    private const val LIMIT_PAUSE_MS = 2 * 60_000L
    private const val GONE = Long.MAX_VALUE

    private const val SEARCH_DOWN =
        "Web search is unavailable right now. Answer with what you know and say you couldn't check online."
    private const val SEARCH_LIMIT =
        "Search limit reached for this question. Answer now with what you already have."

    private class Candidate(val id: String, val lite: Boolean)

    // Los alias "-latest" son el plan B si Google da de baja un modelo antes de que se actualice la app.
    private val FLASH = listOf(Candidate(MODEL_SMART, false), Candidate("gemini-flash-latest", false))
    private val LITE = listOf(Candidate(MODEL_FAST, true), Candidate("gemini-flash-lite-latest", true))

    /** Modelos a saltear y hasta cuándo: los que dieron "límite alcanzado" o ya no existen. */
    private val paused = ConcurrentHashMap<String, Long>()
    private val noThinking: MutableSet<String> = ConcurrentHashMap.newKeySet()

    class Request(
        val apiKey: String,
        /** Key de Tavily. Vacía = sin internet. */
        val searchKey: String,
        /** true = Flash-Lite; false = Flash. */
        val fast: Boolean,
        val system: String,
        val history: List<ChatMsg>,
        val web: Boolean
    )

    /** Bloqueante: llamar desde un hilo de fondo. */
    fun ask(req: Request): Reply {
        val web = req.web && req.searchKey.isNotBlank()
        val searches = Searches(req.searchKey)
        return withModel(req.fast) { model ->
            if (!web) return@withModel turn(req, model, false, searches).reply
            val first = try {
                turn(req, model, true, searches)
            } catch (e: ApiException) {
                // Un 400 con la función de búsqueda declarada: se prueba una vez sin ella. Si así
                // funciona, se responde sin internet y se avisa; si falla igual, el problema era
                // otro y se informa el error original.
                if (e.kind != ApiException.Kind.BAD_REQUEST) throw e
                val plain = try {
                    turn(req, model, false, searches).reply
                } catch (second: ApiException) {
                    throw e
                }
                return@withModel Reply(plain.text, plain.sources, plain.searched, plain.refused, webError = e.detail)
            }
            if (!first.toolTrouble) return@withModel first.reply
            // El modelo armó mal el pedido de búsqueda: se responde sin internet.
            val plain = turn(req, model, false, searches).reply
            Reply(plain.text, plain.sources, plain.searched, plain.refused, webError = first.finish)
        }
    }

    /** Solo para pruebas: olvida qué modelos estaban en pausa. */
    fun forgetLimits() {
        paused.clear()
        noThinking.clear()
    }

    // ------------------------------------------------------------------ elección de modelo

    /**
     * Prueba con el modelo elegido y, si llegó a su límite gratuito o ya no existe, con el
     * siguiente. Los límites son por modelo, así que Flash y Flash-Lite se cubren entre sí.
     */
    private fun <T> withModel(fast: Boolean, block: (String) -> T): T {
        val chain = if (fast) LITE + FLASH else FLASH + LITE
        var limited: ApiException? = null
        var gone: ApiException? = null
        for (candidate in chain) {
            if ((paused[candidate.id] ?: 0L) > clock()) continue
            try {
                return block(candidate.id)
            } catch (e: ApiException) {
                when (e.kind) {
                    ApiException.Kind.MODEL_GONE -> {
                        paused[candidate.id] = GONE
                        gone = e
                    }
                    ApiException.Kind.RATE_LIMIT -> {
                        // El alias de la misma familia apunta al mismo modelo: se saltea también.
                        val until = clock() + LIMIT_PAUSE_MS
                        for (other in chain) {
                            if (other.lite == candidate.lite && paused[other.id] != GONE) paused[other.id] = until
                        }
                        limited = e
                    }
                    else -> throw e
                }
            }
        }
        if (limited != null) throw limited
        if (gone != null) throw gone
        // Estaban todos en pausa: se intenta igual con el preferido, por si el límite ya se liberó.
        val retry = chain.firstOrNull { paused[it.id] != GONE }
            ?: throw ApiException(ApiException.Kind.MODEL_GONE)
        return block(retry.id)
    }

    // ------------------------------------------------------------------ una respuesta

    private class Turn(val reply: Reply, val toolTrouble: Boolean, val finish: String)

    private fun turn(req: Request, model: String, withWeb: Boolean, searches: Searches): Turn {
        val contents = JSONArray()
        for (m in req.history) {
            if (m.text.isBlank()) continue
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
            )
        }
        val generation = JSONObject().put("maxOutputTokens", MAX_TOKENS)
        if (model !in noThinking) {
            generation.put("thinkingConfig", JSONObject().put("thinkingLevel", THINKING))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", req.system))))
            .put("contents", contents)
            .put("generationConfig", generation)
        if (withWeb) body.put("tools", JSONArray().put(searchTool()))

        var requests = 0
        while (true) {
            requests++
            // En el último pedido se le prohíbe seguir buscando, para que cierre con una respuesta.
            if (withWeb && requests == MAX_REQUESTS) {
                body.put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "NONE")))
            }
            val resp = call(req.apiKey, model, body)
            val candidate = resp.optJSONArray("candidates")?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()
            val finish = candidate?.str("finishReason").orEmpty()

            val said = StringBuilder()
            val calls = ArrayList<JSONObject>()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought")) continue   // resumen del razonamiento: no se dice
                part.optJSONObject("functionCall")?.let { calls.add(it) }
                said.append(part.str("text"))
            }

            if (calls.isEmpty() || !withWeb || requests >= MAX_REQUESTS || content == null) {
                val text = said.toString().trim()
                val blocked = resp.optJSONObject("promptFeedback")?.str("blockReason").orEmpty()
                val refused = text.isEmpty() && (blocked.isNotEmpty() || finish in REFUSALS)
                val reply = Reply(
                    text = text,
                    sources = searches.sources(),
                    searched = searches.done,
                    refused = refused,
                    webError = if (searches.issue == WebIssue.SEARCH_FAILED) searches.detail else null,
                    webIssue = searches.issue
                )
                return Turn(reply, text.isEmpty() && finish in TOOL_TROUBLE, finish)
            }

            // Lo que respondió el modelo se reenvía tal cual: trae las "firmas de pensamiento"
            // que Gemini exige recibir de vuelta junto con el resultado de la función.
            if (content.isNull("role")) content.put("role", "model")
            contents.put(content)
            val results = JSONArray()
            for (call in calls) {
                val name = call.str("name")
                val result = if (name == TOOL) {
                    searches.run(call.optJSONObject("args")?.str("query").orEmpty())
                } else {
                    JSONObject().put("error", "Unknown function.")
                }
                val response = JSONObject().put("name", name).put("response", result)
                if (!call.isNull("id")) response.put("id", call.get("id"))
                results.put(JSONObject().put("functionResponse", response))
            }
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
    }

    private val REFUSALS = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")
    private val TOOL_TROUBLE = setOf("MALFORMED_FUNCTION_CALL", "UNEXPECTED_TOOL_CALL", "TOO_MANY_TOOL_CALLS")

    private fun searchTool(): JSONObject {
        val query = JSONObject()
            .put("type", "string")
            .put(
                "description",
                "What to search for, written the way you'd type it into a search engine. " +
                    "Include the place and the date when they matter."
            )
        val declaration = JSONObject()
            .put("name", TOOL)
            .put(
                "description",
                "Searches the web and returns a short answer plus the top results. Use it for anything " +
                    "current or that may have changed: news, weather, prices, exchange rates, scores, schedules."
            )
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("query", query))
                    .put("required", JSONArray().put("query"))
            )
        return JSONObject().put("functionDeclarations", JSONArray().put(declaration))
    }

    // ------------------------------------------------------------------ búsquedas de una respuesta

    /** Las búsquedas hechas para contestar una pregunta: tope, resultados y fallas. */
    private class Searches(private val key: String) {
        private val cache = HashMap<String, JSONObject>()
        private val batches = ArrayList<List<Tavily.Hit>>()
        private var used = 0

        var issue: WebIssue? = null
            private set
        var detail: String? = null
            private set

        /** true si al menos una búsqueda trajo resultados. */
        val done: Boolean
            get() = batches.isNotEmpty()

        fun run(rawQuery: String): JSONObject {
            val query = rawQuery.trim()
            if (query.isEmpty()) return JSONObject().put("error", "Empty query.")
            val cacheKey = query.lowercase()
            cache[cacheKey]?.let { return it }
            if (issue != null) return JSONObject().put("error", SEARCH_DOWN)
            if (used >= MAX_SEARCHES) return JSONObject().put("error", SEARCH_LIMIT)
            used++

            val result = try {
                Tavily.search(key, query)
            } catch (e: ApiException) {
                issue = when (e.kind) {
                    ApiException.Kind.AUTH -> WebIssue.SEARCH_KEY
                    ApiException.Kind.NO_CREDIT -> WebIssue.SEARCH_QUOTA
                    else -> WebIssue.SEARCH_FAILED
                }
                detail = e.detail.ifBlank { e.kind.name.lowercase().replace('_', ' ') }
                return JSONObject().put("error", SEARCH_DOWN)
            }

            batches.add(result.hits)
            val out = JSONObject()
            if (result.answer.isNotEmpty()) out.put("answer", result.answer)
            val list = JSONArray()
            for (hit in result.hits) {
                list.put(JSONObject().put("title", hit.title).put("url", hit.url).put("content", hit.content))
            }
            out.put("results", list)
            if (result.answer.isEmpty() && result.hits.isEmpty()) out.put("note", "No results found.")
            cache[cacheKey] = out
            return out
        }

        /** Hasta tres páginas para mostrar: primero las de la última búsqueda. */
        fun sources(): List<Source> {
            val unique = LinkedHashMap<String, Source>()
            for (batch in batches.asReversed()) {
                for (hit in batch) {
                    if (!unique.containsKey(hit.url)) unique[hit.url] = Source(hit.title, hit.url)
                }
            }
            return unique.values.take(3)
        }
    }

    // ------------------------------------------------------------------ red

    private fun call(apiKey: String, model: String, body: JSONObject): JSONObject {
        try {
            return post(apiKey, model, body)
        } catch (e: ApiException) {
            // Si un modelo no admite el ajuste de razonamiento, se repite sin él y se recuerda.
            val generation = body.optJSONObject("generationConfig")
            val aboutThinking = e.kind == ApiException.Kind.BAD_REQUEST &&
                e.detail.contains("thinking", ignoreCase = true)
            if (!aboutThinking || generation == null || !generation.has("thinkingConfig")) throw e
            generation.remove("thinkingConfig")
            noThinking.add(model)
            return post(apiKey, model, body)
        }
    }

    private fun post(apiKey: String, model: String, body: JSONObject): JSONObject {
        val headers = mapOf("x-goog-api-key" to Net.cleanKey(apiKey))
        val resp = Net.postJson(baseUrl + model + ":generateContent", headers, body)
        if (!resp.ok) throw httpError(resp.code, resp.body)
        return Net.parse(resp.body)
    }

    private fun httpError(code: Int, raw: String): ApiException {
        var message = ""
        try {
            message = JSONObject(raw).optJSONObject("error")?.str("message").orEmpty()
        } catch (e: JSONException) {
            // cuerpo no JSON: nos quedamos con el código HTTP
        }
        // Una key mal escrita llega como 400 "API key not valid", no como 401.
        val badKey = raw.contains("API_KEY_INVALID") ||
            message.contains("API key not valid", ignoreCase = true) ||
            message.contains("API key expired", ignoreCase = true) ||
            message.contains("API key is missing", ignoreCase = true) ||
            message.contains("reported as leaked", ignoreCase = true) ||
            message.contains("unregistered callers", ignoreCase = true)
        val kind = when {
            code == 401 || badKey -> ApiException.Kind.AUTH
            code == 402 -> ApiException.Kind.NO_CREDIT
            code == 403 -> ApiException.Kind.FORBIDDEN
            code == 404 -> ApiException.Kind.MODEL_GONE
            code == 429 -> ApiException.Kind.RATE_LIMIT        // límite del plan gratis (por minuto o por día)
            code == 503 -> ApiException.Kind.OVERLOADED
            code == 504 -> ApiException.Kind.TIMEOUT
            code >= 500 -> ApiException.Kind.SERVER
            code in 400..499 -> ApiException.Kind.BAD_REQUEST
            else -> ApiException.Kind.OTHER
        }
        return ApiException(kind, message.ifBlank { "HTTP $code" })
    }
}
