package app.slop

import org.json.JSONException
import org.json.JSONObject

/**
 * Búsqueda en internet con Tavily (plan gratis: 1.000 búsquedas por mes).
 * La usa Gemini, que en su plan gratuito no trae búsqueda propia.
 */
object Tavily {
    private const val ENDPOINT = "https://api.tavily.com/search"

    /** Dirección de la API. Solo se cambia en pruebas. */
    @Volatile
    var endpoint: String = ENDPOINT

    private const val MAX_RESULTS = 5
    private const val MAX_QUERY = 380      // Tavily rechaza consultas de más de 400 caracteres
    private const val MAX_SNIPPET = 700

    class Hit(val title: String, val url: String, val content: String)

    class Result(val answer: String, val hits: List<Hit>)

    /** Bloqueante: llamar desde un hilo de fondo. Una búsqueda básica gasta un crédito. */
    fun search(apiKey: String, query: String): Result {
        val body = JSONObject()
            .put("query", query.trim().take(MAX_QUERY))
            .put("search_depth", "basic")
            .put("max_results", MAX_RESULTS)
            .put("include_answer", true)
        val headers = mapOf("authorization" to "Bearer " + Net.cleanKey(apiKey))
        val resp = Net.postJson(endpoint, headers, body, readTimeoutMs = 25_000)
        if (!resp.ok) throw httpError(resp.code, resp.body)

        val json = Net.parse(resp.body)
        val hits = ArrayList<Hit>()
        val results = json.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val url = item.str("url")
                if (!url.startsWith("http")) continue
                hits.add(
                    Hit(
                        title = item.str("title").ifBlank { Net.hostOf(url) },
                        url = url,
                        content = item.str("content").trim().take(MAX_SNIPPET)
                    )
                )
            }
        }
        return Result(json.str("answer").trim(), hits)
    }

    private fun httpError(code: Int, raw: String): ApiException {
        // El cuerpo de error es {"detail": {"error": "..."}}; a veces "detail" es un texto suelto.
        var message = ""
        try {
            val json = JSONObject(raw)
            message = json.optJSONObject("detail")?.str("error").orEmpty()
            if (message.isBlank()) message = json.optString("detail").takeIf { !json.isNull("detail") }.orEmpty()
        } catch (e: JSONException) {
            // cuerpo no JSON: nos quedamos con el código HTTP
        }
        val kind = when {
            code == 401 || code == 403 -> ApiException.Kind.AUTH   // key mal escrita o dada de baja
            code == 432 || code == 433 -> ApiException.Kind.NO_CREDIT // se acabaron las búsquedas del plan
            code == 429 -> ApiException.Kind.RATE_LIMIT
            code >= 500 -> ApiException.Kind.SERVER
            code in 400..499 -> ApiException.Kind.BAD_REQUEST
            else -> ApiException.Kind.OTHER
        }
        return ApiException(kind, message.ifBlank { "HTTP $code" })
    }
}
