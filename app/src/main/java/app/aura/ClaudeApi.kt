package app.aura

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Página web que Claude usó para responder. */
class Source(val title: String, val url: String)

/** Un mensaje de la conversación. */
class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val searched: Boolean = false,
    val note: String? = null
)

class ApiException(val kind: Kind, val detail: String = "") : Exception(detail) {
    enum class Kind {
        NO_NETWORK, TIMEOUT, AUTH, FORBIDDEN, RATE_LIMIT, OVERLOADED, SERVER, NO_CREDIT, WORKSPACE, BAD_REQUEST, OTHER
    }
}

/**
 * Cliente de la Messages API de Anthropic con la herramienta de búsqueda web.
 * Solo usa HttpURLConnection y org.json (vienen con Android).
 */
object ClaudeApi {
    const val MODEL_SMART = "claude-sonnet-5-5"
    const val MODEL_FAST = "claude-haiku-4-5-20251001"

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    /** Dirección de la API. Solo se cambia en pruebas. */
    @Volatile
    var endpoint: String = ENDPOINT
    private const val API_VERSION = "2023-06-01"
    private const val WEB_TOOL = "web_search_20250305"
    private const val MAX_SEARCHES = 3
    private const val MAX_PAUSE_ROUNDS = 3
    private const val MAX_TOKENS = 1024

    class Request(
        val apiKey: String,
        val model: String,
        val system: String,
        val history: List<ChatMsg>,
        val web: Boolean,
        val country: String?,
        val timeZone: String?
    )

    class Reply(
        val text: String,
        val sources: List<Source>,
        val searched: Boolean,
        val refused: Boolean,
        /** Si no se pudo usar la búsqueda web, el motivo que devolvió la API. */
        val webError: String?
    )

    /** Bloqueante: llamar desde un hilo de fondo. */
    fun ask(req: Request): Reply {
        if (!req.web) return run(req, withWeb = false)
        return try {
            run(req, withWeb = true)
        } catch (e: ApiException) {
            // Un 400 con la búsqueda activada suele significar que la cuenta o el modelo no la
            // admiten. Se prueba una vez sin ella: si así funciona, se responde sin internet y
            // se avisa; si falla igual, el problema era otro y se informa el error original.
            if (e.kind != ApiException.Kind.BAD_REQUEST) throw e
            val plain = try {
                run(req, withWeb = false)
            } catch (second: ApiException) {
                throw e
            }
            Reply(plain.text, plain.sources, plain.searched, plain.refused, e.detail)
        }
    }

    private fun run(req: Request, withWeb: Boolean): Reply {
        val messages = JSONArray()
        for (m in req.history) {
            if (m.text.isBlank()) continue
            messages.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "assistant")
                    .put("content", m.text)
            )
        }
        val body = JSONObject()
            .put("model", req.model)
            .put("max_tokens", MAX_TOKENS)
            .put("system", req.system)
            .put("messages", messages)
        if (withWeb) body.put("tools", JSONArray().put(webTool(req)))

        val blocks = ArrayList<JSONObject>()
        var stop: String
        var round = 0
        while (true) {
            val resp = post(req.apiKey, body)
            val content = resp.optJSONArray("content") ?: JSONArray()
            for (i in 0 until content.length()) {
                content.optJSONObject(i)?.let { blocks.add(it) }
            }
            stop = resp.optString("stop_reason")
            // "pause_turn": la búsqueda sigue en curso; se reenvía lo recibido tal cual para continuar.
            if (stop == "pause_turn" && round < MAX_PAUSE_ROUNDS && content.length() > 0) {
                round++
                messages.put(JSONObject().put("role", "assistant").put("content", content))
                continue
            }
            break
        }
        return toReply(blocks, stop)
    }

    private fun webTool(req: Request): JSONObject {
        val tool = JSONObject()
            .put("type", WEB_TOOL)
            .put("name", "web_search")
            .put("max_uses", MAX_SEARCHES)
        val location = JSONObject().put("type", "approximate")
        req.country?.let { location.put("country", it) }
        req.timeZone?.let { location.put("timezone", it) }
        if (location.length() > 1) tool.put("user_location", location)
        return tool
    }

    /** Arma el texto a decir y las fuentes a partir de los bloques de la respuesta. */
    private fun toReply(blocks: List<JSONObject>, stop: String): Reply {
        val all = StringBuilder()   // todo el texto
        val tail = StringBuilder()  // solo lo escrito después de la última búsqueda
        val cited = LinkedHashMap<String, Source>()
        val found = LinkedHashMap<String, Source>()
        var searched = false
        var gap = false
        for (block in blocks) {
            when (block.optString("type")) {
                "text" -> {
                    val t = block.str("text")
                    if (t.isNotEmpty()) {
                        if (gap && all.isNotEmpty() && !all.last().isWhitespace()) all.append(' ')
                        gap = false
                        all.append(t)
                        tail.append(t)
                        val citations = block.optJSONArray("citations")
                        if (citations != null) {
                            for (i in 0 until citations.length()) addSource(cited, citations.optJSONObject(i))
                        }
                    }
                }
                "server_tool_use" -> {
                    searched = true
                    gap = true
                }
                "web_search_tool_result" -> {
                    searched = true
                    gap = true
                    tail.setLength(0)
                    // Si la búsqueda falló, "content" es un objeto de error y no una lista.
                    val results = block.optJSONArray("content")
                    if (results != null) {
                        for (i in 0 until results.length()) addSource(found, results.optJSONObject(i))
                    }
                }
                else -> gap = true
            }
        }
        val text = (if (tail.isNotBlank()) tail else all).toString().trim()
        val sources = if (!searched) emptyList() else (if (cited.isNotEmpty()) cited else found).values.take(3)
        return Reply(text, sources, searched, stop == "refusal", null)
    }

    private fun addSource(into: LinkedHashMap<String, Source>, item: JSONObject?) {
        if (item == null) return
        val url = item.str("url")
        if (!url.startsWith("http") || into.containsKey(url)) return
        into[url] = Source(item.str("title").ifBlank { hostOf(url) }, url)
    }

    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.")

    /** optString devuelve "null" para un null de JSON; esto devuelve cadena vacía. */
    private fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

    private fun post(apiKey: String, body: JSONObject): JSONObject {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("accept", "application/json")
            conn.setRequestProperty("authorization", "Bearer " + apiKey.filterNot { it.isWhitespace() })
            conn.setRequestProperty("anthropic-version", API_VERSION)

            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw httpError(code, text)
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException(ApiException.Kind.OTHER, "invalid response")
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ApiException(ApiException.Kind.NO_NETWORK)
        } catch (e: ConnectException) {
            throw ApiException(ApiException.Kind.NO_NETWORK)
        } catch (e: SocketTimeoutException) {
            throw ApiException(ApiException.Kind.TIMEOUT)
        } catch (e: IOException) {
            throw ApiException(ApiException.Kind.NO_NETWORK, e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            // Caracteres inválidos en la key (por ejemplo, pegada con símbolos raros).
            throw ApiException(ApiException.Kind.AUTH)
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpError(code: Int, raw: String): ApiException {
        var type = ""
        var message = ""
        try {
            val err = JSONObject(raw).optJSONObject("error")
            if (err != null) {
                type = err.str("type")
                message = err.str("message")
            }
        } catch (e: JSONException) {
            // cuerpo no JSON: nos quedamos con el código HTTP
        }
        val kind = when {
            code == 401 -> ApiException.Kind.AUTH                      // key mal escrita, revocada o vencida
            code == 402 || type == "billing_error" -> ApiException.Kind.NO_CREDIT
            code == 403 -> ApiException.Kind.FORBIDDEN
            code == 429 -> ApiException.Kind.RATE_LIMIT
            code == 504 || type == "timeout_error" -> ApiException.Kind.TIMEOUT
            code == 529 || type == "overloaded_error" -> ApiException.Kind.OVERLOADED
            code >= 500 -> ApiException.Kind.SERVER
            // Una key que sirve para varios workspaces exige indicar cuál usar.
            message.contains("anthropic-workspace-id", ignoreCase = true) -> ApiException.Kind.WORKSPACE
            message.contains("credit balance", ignoreCase = true) ||
                message.contains("spend limit", ignoreCase = true) -> ApiException.Kind.NO_CREDIT
            code in 400..499 -> ApiException.Kind.BAD_REQUEST
            else -> ApiException.Kind.OTHER
        }
        return ApiException(kind, message.ifBlank { "HTTP $code" })
    }
}
