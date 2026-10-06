package app.slop

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Servicio de IA con el que piensa la asistente. */
enum class Provider(val code: String, val label: String, val site: String, val keyHint: String) {
    /** Gratis, con tope diario. Para buscar en internet usa Tavily. */
    GEMINI("gemini", "Gemini", "aistudio.google.com", ""),

    /** Pago por uso. Trae su propia búsqueda web. */
    CLAUDE("claude", "Claude", "platform.claude.com", "sk-ant-api…");

    companion object {
        fun fromCode(code: String?): Provider = entries.firstOrNull { it.code == code } ?: GEMINI
    }
}

/** Página web que se usó para responder. */
class Source(val title: String, val url: String)

/** Un mensaje de la conversación. */
class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val searched: Boolean = false,
    val note: String? = null
)

/** Motivos por los que no se pudo buscar en internet aunque la respuesta sí salió. */
enum class WebIssue { SEARCH_KEY, SEARCH_QUOTA, SEARCH_FAILED }

/** Respuesta de la IA, venga de Gemini o de Claude. */
class Reply(
    val text: String,
    val sources: List<Source>,
    val searched: Boolean,
    val refused: Boolean,
    /** Si no se pudo usar internet, el motivo tal como lo devolvió el servicio. */
    val webError: String? = null,
    /** Si no se pudo usar internet por un motivo conocido, cuál. */
    val webIssue: WebIssue? = null
)

class ApiException(val kind: Kind, val detail: String = "") : Exception(detail) {
    enum class Kind {
        NO_NETWORK, TIMEOUT, AUTH, FORBIDDEN, RATE_LIMIT, OVERLOADED, SERVER, NO_CREDIT, WORKSPACE,
        MODEL_GONE, BAD_REQUEST, OTHER
    }
}

/** optString devuelve "null" para un null de JSON; esto devuelve cadena vacía. */
internal fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

/** Pedidos HTTP con lo que trae Android: HttpURLConnection y org.json. */
internal object Net {

    class Response(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    /**
     * Bloqueante: llamar desde un hilo de fondo.
     * Solo lanza ApiException por fallas de red; los códigos HTTP los interpreta quien llama.
     */
    fun postJson(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        readTimeoutMs: Int = 120_000
    ): Response {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("accept", "application/json")
            for ((name, value) in headers) conn.setRequestProperty(name, value)

            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(code, text)
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

    fun parse(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ApiException(ApiException.Kind.OTHER, "invalid response")
    }

    /** La key tal como se manda: sin espacios ni saltos de línea de un pegado descuidado. */
    fun cleanKey(key: String): String = key.filterNot { it.isWhitespace() }

    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.")
}
