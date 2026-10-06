package app.slop

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

/** La conversación vive en el proceso, así sobrevive si Android recrea la pantalla. */
object Conversation {
    val messages = ArrayList<ChatMsg>()

    /** Últimos mensajes para mandar a la IA, empezando siempre por uno del usuario. */
    fun forApi(): List<ChatMsg> = messages.takeLast(24).dropWhile { !it.fromUser }
}

/**
 * El "cerebro" de la asistente: escucha -> piensa (Gemini o Claude) -> habla.
 * Todos los métodos y avisos corren en el hilo principal.
 */
class Assistant(context: Context, private val ui: Ui) {

    interface Ui {
        fun onPhase(phase: Phase)
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onMessage(msg: ChatMsg)
        fun onError(text: String?)
        /** Empezó a decir [text] (ya sin markdown); los tiempos son de SystemClock.uptimeMillis(). */
        fun onSpeechStart(text: String, english: Boolean, atMs: Long)

        /** Está por decir las letras [start, end) de ese texto. */
        fun onSpeechRange(start: Int, end: Int, atMs: Long)

        fun onSpeechEnd()
    }

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var lang = Prefs.lang(ctx)
    private var s = Texts.of(lang)
    private var turn = 0
    private var released = false
    private var foreground = true
    private var autoListening = false
    private var shownError: String? = null

    var phase = Phase.IDLE
        private set

    private val output = VoiceOutput(ctx, object : VoiceOutput.Listener {
        override fun onSpeechStart(text: String, atMs: Long) {
            if (!released) ui.onSpeechStart(text, lang == Lang.EN, atMs)
        }

        override fun onSpeechRange(start: Int, end: Int, atMs: Long) {
            if (!released) ui.onSpeechRange(start, end, atMs)
        }

        override fun onSpeechEnd() {
            if (!released) ui.onSpeechEnd()
        }
    })

    private val input = VoiceInput(ctx, object : VoiceInput.Callback {
        override fun onPartial(text: String) {
            if (!released) ui.onPartial(text)
        }

        override fun onLevel(level: Float) {
            if (!released) ui.onLevel(level)
        }

        override fun onResult(text: String) {
            if (released) return
            ui.onPartial("")
            ui.onLevel(0f)
            if (Prefs.handsFree(ctx) && SpeechText.isStopPhrase(text, s.stopPhrases)) {
                // En manos libres, "chau" o "gracias" cortan la ronda en vez de ir a la IA.
                say(s.stopAck, thenListen = false)
            } else {
                send(text)
            }
        }

        override fun onFail(reason: VoiceInput.Fail, code: Int) {
            if (released) return
            ui.onPartial("")
            ui.onLevel(0f)
            setPhase(Phase.IDLE)
            val silentEnd = autoListening && reason == VoiceInput.Fail.NO_SPEECH
            showError(
                if (silentEnd) voiceNotice() else when (reason) {
                    VoiceInput.Fail.NO_SPEECH -> s.noSpeech
                    VoiceInput.Fail.PERMISSION -> s.micPermission
                    VoiceInput.Fail.NETWORK -> s.sttNetwork
                    VoiceInput.Fail.BUSY -> s.sttBusy
                    VoiceInput.Fail.LANGUAGE -> s.sttLanguage
                    VoiceInput.Fail.OTHER -> String.format(Locale.US, s.sttOther, code)
                }
            )
        }
    })

    init {
        output.setLanguage(lang)
    }

    /** Vuelve a leer idioma y demás ajustes. */
    fun applySettings() {
        lang = Prefs.lang(ctx)
        s = Texts.of(lang)
        output.setLanguage(lang)
    }

    /** La pantalla pasó a segundo plano o volvió. En segundo plano no escucha ni habla. */
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            input.stop()
            output.stop()
            main.removeCallbacksAndMessages(LISTEN_TOKEN)
            ui.onPartial("")
            ui.onLevel(0f)
            if (phase != Phase.THINKING) setPhase(Phase.IDLE)
        }
    }

    private fun showError(text: String?) {
        shownError = text
        ui.onError(text)
    }

    private fun setPhase(value: Phase) {
        if (phase != value) {
            phase = value
            ui.onPhase(value)
        }
    }

    /** Empieza a escuchar (si estaba hablando, se calla). */
    fun startListening(auto: Boolean = false) {
        if (released || !foreground || phase == Phase.THINKING) return
        output.stop()
        showError(voiceNotice())
        if (!input.available()) {
            setPhase(Phase.IDLE)
            showError(s.sttUnavailable)
            return
        }
        autoListening = auto
        ui.onPartial("")
        setPhase(Phase.LISTENING)
        input.start(lang.sttTag)
    }

    fun stopListening() {
        input.stop()
        ui.onPartial("")
        ui.onLevel(0f)
        if (phase == Phase.LISTENING) setPhase(Phase.IDLE)
    }

    /** Lo que pasa al tocar el widget: pregunta qué necesitás y se queda escuchando. */
    fun greetAndListen() {
        if (released || phase == Phase.THINKING || phase == Phase.LISTENING) return
        if (Prefs.apiKey(ctx).isBlank()) {
            showError(s.noKey.forAi(Prefs.provider(ctx)))
            return
        }
        input.stop()
        showError(null)
        say(s.greeting, thenListen = true)
    }

    /** Manda un texto (dictado o escrito) a la IA elegida y dice la respuesta. */
    fun send(raw: String) {
        val text = raw.trim()
        if (released || text.isEmpty()) return
        input.stop()
        output.stop()
        main.removeCallbacksAndMessages(LISTEN_TOKEN)
        ui.onPartial("")
        ui.onLevel(0f)

        val mine = ChatMsg(true, text)
        Conversation.messages.add(mine)
        ui.onMessage(mine)

        val provider = Prefs.provider(ctx)
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) {
            setPhase(Phase.IDLE)
            showError(s.noKey.forAi(provider))
            return
        }
        showError(voiceNotice())
        setPhase(Phase.THINKING)

        val web = Prefs.webActive(ctx)
        val fast = Prefs.fastModel(ctx)
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault().id
        val system = Prompts.system(
            lang, Prefs.name(ctx), web, nowText(), zone, locale.getDisplayCountry(lang.formatLocale())
        )
        val history = Conversation.forApi()
        val ask: () -> Reply = when (provider) {
            Provider.GEMINI -> {
                val request = GeminiApi.Request(
                    apiKey = key,
                    searchKey = Prefs.searchKey(ctx),
                    fast = fast,
                    system = system,
                    history = history,
                    web = web
                )
                ({ GeminiApi.ask(request) })
            }
            Provider.CLAUDE -> {
                val request = ClaudeApi.Request(
                    apiKey = key,
                    model = if (fast) ClaudeApi.MODEL_FAST else ClaudeApi.MODEL_SMART,
                    system = system,
                    history = history,
                    web = web,
                    country = locale.country.takeIf { it.matches(COUNTRY) },
                    timeZone = zone.takeIf { it.contains('/') }
                )
                ({ ClaudeApi.ask(request) })
            }
        }
        val myTurn = ++turn
        io.execute {
            var reply: Reply? = null
            var failure: Exception? = null
            try {
                reply = ask()
            } catch (e: Exception) {
                failure = e
            }
            main.post {
                if (!released && myTurn == turn) onAnswer(reply, failure, provider)
            }
        }
    }

    private fun onAnswer(reply: Reply?, failure: Exception?, provider: Provider) {
        if (reply == null) {
            val text = describe(failure, provider)
            showError(text)
            // Los avisos propios (sin conexión, key vencida, etc.) también los dice en voz alta;
            // los errores crudos de la API quedan solo en pantalla.
            val kind = (failure as? ApiException)?.kind
            val sayIt = foreground && kind != null &&
                kind != ApiException.Kind.BAD_REQUEST && kind != ApiException.Kind.OTHER
            if (sayIt) say(text, thenListen = false) else setPhase(Phase.IDLE)
            return
        }
        val text = when {
            reply.text.isNotBlank() -> reply.text
            reply.refused -> s.refusal
            else -> s.emptyReply
        }
        val msg = ChatMsg(
            fromUser = false,
            text = text,
            sources = reply.sources,
            searched = reply.searched,
            note = when (reply.webIssue) {
                WebIssue.SEARCH_KEY -> s.searchBadKey
                WebIssue.SEARCH_QUOTA -> s.searchQuota
                else -> reply.webError?.let { s.webFailed + it }
            }
        )
        Conversation.messages.add(msg)
        ui.onMessage(msg)
        if (foreground) {
            say(text, thenListen = Prefs.handsFree(ctx))
        } else {
            setPhase(Phase.IDLE)
        }
    }

    private fun say(text: String, thenListen: Boolean) {
        setPhase(Phase.SPEAKING)
        output.speak(text) {
            if (released) return@speak
            if (phase == Phase.SPEAKING) setPhase(Phase.IDLE)
            if (!output.languageOk && shownError == null) showError(s.ttsUnavailable)
            if (thenListen && foreground) {
                // Pequeña pausa para no captar la cola de su propia voz.
                main.postAtTime({
                    if (!released && foreground && phase == Phase.IDLE) startListening(auto = true)
                }, LISTEN_TOKEN, SystemClock.uptimeMillis() + 350L)
            }
        }
    }

    /** Si el celu no tiene voz para el idioma elegido, el aviso queda a la vista; si no, nada. */
    private fun voiceNotice(): String? = if (output.languageOk) null else s.ttsUnavailable

    private fun describe(e: Exception?, provider: Provider): String {
        val api = e as? ApiException
            ?: return s.apiError.forAi(provider) + (e?.message ?: e?.javaClass?.simpleName ?: "?")
        val text = when (api.kind) {
            ApiException.Kind.NO_NETWORK -> s.noConnection
            ApiException.Kind.TIMEOUT -> s.timeout
            ApiException.Kind.AUTH -> s.badKey
            ApiException.Kind.FORBIDDEN -> s.forbidden
            // En Gemini el "demasiadas consultas" es el tope del plan gratis, no un apuro momentáneo.
            ApiException.Kind.RATE_LIMIT -> if (provider == Provider.GEMINI) s.freeLimit else s.rateLimited
            ApiException.Kind.OVERLOADED -> s.overloaded
            ApiException.Kind.SERVER -> s.serverError
            ApiException.Kind.NO_CREDIT -> s.noCredit
            ApiException.Kind.WORKSPACE -> s.workspaceKey
            ApiException.Kind.MODEL_GONE -> s.modelGone
            // El detalle viene del servicio: va tal cual, sin pasar por forAi.
            ApiException.Kind.BAD_REQUEST,
            ApiException.Kind.OTHER -> return s.apiError.forAi(provider) + api.detail
        }
        return text.forAi(provider)
    }

    private fun nowText(): String {
        val pattern = if (lang == Lang.EN) "EEEE, MMMM d, yyyy, h:mm a" else "EEEE d 'de' MMMM 'de' yyyy, HH:mm"
        return SimpleDateFormat(pattern, lang.formatLocale()).format(Date())
    }

    fun release() {
        released = true
        turn++
        main.removeCallbacksAndMessages(LISTEN_TOKEN)
        input.stop()
        output.shutdown()
        io.shutdownNow()
    }

    private companion object {
        val COUNTRY = Regex("[A-Z]{2}")
        val LISTEN_TOKEN = Any()
    }
}
