package app.aura

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.Normalizer

/** Funciones de texto sin dependencias de Android (se pueden probar en una PC). */
object SpeechText {
    private val mdLink = Regex("\\[([^\\]]+)]\\((?:https?://)[^)]*\\)")
    private val url = Regex("https?://\\S+")
    private val markup = Regex("[*_#`>|~]")
    private val spaces = Regex("\\s+")
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+")
    private val accents = Regex("\\p{Mn}+")
    private val notWord = Regex("[^a-z0-9 ]")

    /** Saca markdown y direcciones web para que la voz no los lea. */
    fun clean(text: String): String = text
        .replace(mdLink, "\$1")
        .replace(url, "")
        .replace(markup, "")
        .replace(spaces, " ")
        .trim()

    /** Corta un texto largo en tramos que el motor de voz acepte, respetando oraciones. */
    fun chunks(text: String, max: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= max) return listOf(text)
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in text.split(sentenceEnd)) {
            var piece = sentence
            while (piece.length > max) {            // una "oración" más larga que el máximo
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.setLength(0)
                }
                out.add(piece.substring(0, max))
                piece = piece.substring(max)
            }
            if (current.isNotEmpty() && current.length + 1 + piece.length > max) {
                out.add(current.toString())
                current.setLength(0)
            }
            if (piece.isNotEmpty()) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    /** Minúsculas, sin tildes ni signos: "¡Nada más!" -> "nada mas". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(accents, "")
            .replace(notWord, " ")
            .replace(spaces, " ")
            .trim()

    fun isStopPhrase(heard: String, phrases: List<String>): Boolean {
        val h = normalize(heard)
        return h.isNotEmpty() && phrases.any { normalize(it) == h }
    }
}

/** Escucha por el micrófono con el reconocimiento de voz del sistema. */
class VoiceInput(context: Context, private val callback: Callback) {

    enum class Fail { NO_SPEECH, PERMISSION, NETWORK, BUSY, LANGUAGE, OTHER }

    interface Callback {
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onResult(text: String)
        fun onFail(reason: Fail, code: Int)
    }

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var session = 0

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Debe llamarse desde el hilo principal. */
    fun start(languageTag: String) {
        stop()
        val mine = session
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } catch (e: Exception) {
            null
        }
        if (r == null) {
            callback.onFail(Fail.OTHER, -1)
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onRmsChanged(rmsdB: Float) {
                if (mine == session) callback.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onEndOfSpeech() {
                if (mine == session) callback.onLevel(0f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (mine != session) return
                val text = best(partialResults)
                if (text.isNotBlank()) callback.onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                if (mine != session) return
                val text = best(results)
                stop()
                if (text.isBlank()) callback.onFail(Fail.NO_SPEECH, 0) else callback.onResult(text)
            }

            override fun onError(error: Int) {
                if (mine != session) return
                stop()
                callback.onFail(reasonOf(error), error)
            }
        })
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            stop()
            callback.onFail(Fail.OTHER, -2)
        }
    }

    /** Corta la escucha. Los avisos de la sesión anterior se ignoran. */
    fun stop() {
        session++
        val old = recognizer ?: return
        recognizer = null
        // Se destruye en el próximo ciclo: algunos motores fallan si se los destruye desde su propio aviso.
        main.post {
            try {
                old.destroy()
            } catch (e: Exception) {
                // ya estaba destruido
            }
        }
    }

    private fun best(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    private fun reasonOf(code: Int): Fail = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Fail.NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Fail.PERMISSION
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> Fail.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Fail.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Fail.LANGUAGE
        else -> Fail.OTHER
    }
}

/** Lee las respuestas en voz alta con el motor de texto a voz del sistema. */
class VoiceOutput(context: Context, private val onWord: () -> Unit) {

    private enum class State { STARTING, READY, FAILED }

    private class Queued(val text: String, val onFinished: () -> Unit)

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()

    private var tts: TextToSpeech? = null
    private var state = State.STARTING
    private var lang = Lang.ES
    private var queued: Queued? = null
    private var onFinished: (() -> Unit)? = null
    private var lastUtterance = ""
    private var counter = 0
    private var hasFocus = false

    /** false si el celu no tiene una voz instalada para el idioma elegido. */
    var languageOk = true
        private set

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finish(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            main.post { if (onFinished != null) onWord() }
        }
    }

    init {
        tts = TextToSpeech(ctx) { status -> main.post { onInit(status) } }
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            state = State.FAILED
            languageOk = false
            val q = queued
            queued = null
            q?.onFinished?.invoke()
            return
        }
        engine.setOnUtteranceProgressListener(progress)
        state = State.READY
        applyLanguage()
        val q = queued
        queued = null
        if (q != null) speak(q.text, q.onFinished)
    }

    fun setLanguage(newLang: Lang) {
        lang = newLang
        if (state == State.READY) applyLanguage()
    }

    private fun applyLanguage() {
        val engine = tts ?: return
        languageOk = false
        for (locale in lang.ttsLocales()) {
            val result = try {
                engine.setLanguage(locale)
            } catch (e: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                languageOk = true
                return
            }
        }
    }

    /** Dice el texto y avisa (en el hilo principal) cuando termina. Siempre avisa, aunque no haya voz. */
    fun speak(text: String, onFinished: () -> Unit) {
        stop()
        when (state) {
            State.FAILED -> {
                main.post { onFinished() }
                return
            }
            State.STARTING -> {
                queued = Queued(text, onFinished)
                return
            }
            State.READY -> Unit
        }
        val engine = tts
        val parts = SpeechText.chunks(SpeechText.clean(text), MAX_CHARS)
        if (engine == null || parts.isEmpty()) {
            main.post { onFinished() }
            return
        }
        this.onFinished = onFinished
        hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        for ((i, part) in parts.withIndex()) {
            val id = "aura-" + (++counter)
            if (i == parts.lastIndex) lastUtterance = id
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(part, mode, null, id) != TextToSpeech.SUCCESS) {
                lastUtterance = id
                finish(id)
                return
            }
        }
    }

    private fun finish(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == lastUtterance) {
                val callback = onFinished
                onFinished = null
                lastUtterance = ""
                releaseFocus()
                callback?.invoke()
            }
        }
    }

    /** Corta lo que esté diciendo, sin avisar. */
    fun stop() {
        queued = null
        onFinished = null
        lastUtterance = ""
        try {
            tts?.stop()
        } catch (e: Exception) {
            // motor no disponible
        }
        releaseFocus()
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // nada
        }
        tts = null
        state = State.FAILED
    }

    private fun releaseFocus() {
        if (hasFocus) {
            audio.abandonAudioFocusRequest(focus)
            hasFocus = false
        }
    }

    private companion object {
        /** El motor acepta hasta TextToSpeech.getMaxSpeechInputLength() (4000); dejamos margen. */
        const val MAX_CHARS = 3500
    }
}
