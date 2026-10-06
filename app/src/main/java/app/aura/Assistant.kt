package app.aura

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

    /** Últimos mensajes para mandar a Claude, empezando siempre por uno del usuario. */
    fun forApi(): List<ChatMsg> = messages.takeLast(24).dropWhile { !it.fromUser }
}

/**
 * El "cerebro" de la asistente: escucha -> piensa (Claude) -> habla.
 * Todos los métodos y avisos corren en el hilo principal.
 */
class Assistant(context: Context, private val ui: Ui) {

    interface Ui {
        fun onPhase(phase: Phase)
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onMessage(msg: ChatMsg)
        fun onError(text: String?)
        fun onWord()
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

    private val output = VoiceOutput(ctx) { if (!released) ui.onWord() }

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
                // En manos libres, "chau" o "gracias" cortan la ronda en vez de ir a Claude.
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
            showError(s.noKey)
            return
        }
        input.stop()
        showError(null)
        say(s.greeting, thenListen = true)
    }

    /** Manda un texto (dictado o escrito) a Claude y dice la respuesta. */
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

        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) {
            setPhase(Phase.IDLE)
            showError(s.noKey)
            return
        }
        showError(voiceNotice())
        setPhase(Phase.THINKING)

        val web = Prefs.web(ctx)
        val request = ClaudeApi.Request(
            apiKey = key,
            model = if (Prefs.fastModel(ctx)) ClaudeApi.MODEL_FAST else ClaudeApi.MODEL_SMART,
            system = Prompts.system(lang, Prefs.name(ctx), web, nowText(), TimeZone.getDefault().id),
            history = Conversation.forApi(),
            web = web,
            country = Locale.getDefault().country.takeIf { it.matches(COUNTRY) },
            timeZone = TimeZone.getDefault().id.takeIf { it.contains('/') }
        )
        val myTurn = ++turn
        io.execute {
            var reply: ClaudeApi.Reply? = null
            var failure: Exception? = null
            try {
                reply = ClaudeApi.ask(request)
            } catch (e: Exception) {
                failure = e
            }
            main.post {
                if (!released && myTurn == turn) onAnswer(reply, failure)
            }
        }
    }

    private fun onAnswer(reply: ClaudeApi.Reply?, failure: Exception?) {
        if (reply == null) {
            val text = describe(failure)
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
            note = reply.webError?.let { s.webFailed + it }
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

    private fun describe(e: Exception?): String {
        val api = e as? ApiException ?: return s.apiError + (e?.message ?: e?.javaClass?.simpleName ?: "?")
        return when (api.kind) {
            ApiException.Kind.NO_NETWORK -> s.noConnection
            ApiException.Kind.TIMEOUT -> s.timeout
            ApiException.Kind.AUTH -> s.badKey
            ApiException.Kind.FORBIDDEN -> s.forbidden
            ApiException.Kind.RATE_LIMIT -> s.rateLimited
            ApiException.Kind.OVERLOADED -> s.overloaded
            ApiException.Kind.SERVER -> s.serverError
            ApiException.Kind.NO_CREDIT -> s.noCredit
            ApiException.Kind.WORKSPACE -> s.workspaceKey
            ApiException.Kind.BAD_REQUEST,
            ApiException.Kind.OTHER -> s.apiError + api.detail
        }
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
