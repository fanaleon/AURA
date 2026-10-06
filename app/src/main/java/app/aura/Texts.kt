package app.aura

/**
 * Todos los textos de la app en un idioma.
 * El idioma se elige dentro de la app (no depende del idioma del celu), por eso
 * no usamos strings.xml para esto.
 */
class Strings(
    // Voz y estados
    val greeting: String,
    val widgetCaptions: List<String>,
    val listening: String,
    val thinking: String,
    val speaking: String,
    val idleHint: String,
    val inputHint: String,
    // Barra superior
    val handsFree: String,
    val web: String,
    val webOn: String,
    val webOff: String,
    val handsFreeOn: String,
    val handsFreeOff: String,
    val settings: String,
    val send: String,
    val talk: String,
    // Ajustes
    val languageLabel: String,
    val webLabel: String,
    val webHelp: String,
    val nameLabel: String,
    val keyLabel: String,
    val keyHelp: String,
    val modelLabel: String,
    val modelSmart: String,
    val modelFast: String,
    val modelHelp: String,
    val animLabel: String,
    val animHelp: String,
    val save: String,
    // Avisos y errores
    val noKey: String,
    val noSpeech: String,
    val micPermission: String,
    val sttUnavailable: String,
    val sttNetwork: String,
    val sttBusy: String,
    val sttLanguage: String,
    val sttOther: String,
    val ttsUnavailable: String,
    val noConnection: String,
    val timeout: String,
    val badKey: String,
    val forbidden: String,
    val rateLimited: String,
    val overloaded: String,
    val serverError: String,
    val noCredit: String,
    val workspaceKey: String,
    val apiError: String,
    val webFailed: String,
    val refusal: String,
    val emptyReply: String,
    val searched: String,
    // Manos libres
    val stopAck: String,
    val stopPhrases: List<String>
)

object Texts {
    fun of(lang: Lang): Strings = if (lang == Lang.EN) en else es

    val es = Strings(
        greeting = "¿Qué necesitás?",
        widgetCaptions = listOf("Tocame", "¿Qué necesitás?", "Hablemos"),
        listening = "Te escucho…",
        thinking = "Pensando…",
        speaking = "Hablando…",
        idleHint = "Tocame o usá el micrófono para hablar",
        inputHint = "Escribile…",
        handsFree = "Manos libres",
        web = "Web",
        webOn = "Búsqueda en internet activada",
        webOff = "Búsqueda en internet desactivada",
        handsFreeOn = "Manos libres: después de responder te vuelve a escuchar",
        handsFreeOff = "Manos libres desactivado",
        settings = "Ajustes",
        send = "Enviar",
        talk = "Hablar",
        languageLabel = "Idioma",
        webLabel = "Buscar en internet",
        webHelp = "Le permite consultar datos actuales: clima, noticias, precios, resultados. " +
            "Cada búsqueda suma un pequeño costo a tu cuenta de Claude.",
        nameLabel = "Nombre de la asistente",
        keyLabel = "API key de Claude",
        keyHelp = "Se guarda solo en este celu. Creala en platform.claude.com › API keys, " +
            "para un solo workspace. Cada consulta se cobra a esa cuenta.",
        modelLabel = "Modelo",
        modelSmart = "Inteligente",
        modelFast = "Rápida",
        modelHelp = "Inteligente usa Claude Sonnet. Rápida usa Claude Haiku: responde antes y cuesta menos.",
        animLabel = "Widget animado",
        animHelp = "Si lo apagás, el widget queda fijo y gasta menos batería.",
        save = "Guardar",
        noKey = "Primero cargá tu API key de Claude en ajustes.",
        noSpeech = "No te escuché. Probá de nuevo.",
        micPermission = "Necesito el permiso de micrófono para escucharte. Mientras tanto podés escribirle.",
        sttUnavailable = "Este celu no tiene reconocimiento de voz activo. " +
            "Instalá o activá «Servicios de voz de Google», o escribile.",
        sttNetwork = "El reconocimiento de voz necesita conexión a internet.",
        sttBusy = "El micrófono está ocupado. Probá otra vez.",
        sttLanguage = "El reconocimiento de voz no tiene este idioma disponible en el celu.",
        sttOther = "No pude escucharte (código %d).",
        ttsUnavailable = "No encontré una voz instalada para este idioma. " +
            "Revisá «Salida de texto a voz» en los ajustes del celu.",
        noConnection = "No hay conexión a internet.",
        timeout = "La respuesta tardó demasiado. Probá de nuevo.",
        badKey = "La API key no es válida o venció. Creá una nueva en platform.claude.com y cargala en ajustes.",
        forbidden = "Tu API key no tiene permiso para usar este modelo.",
        rateLimited = "Demasiadas consultas seguidas. Probá en unos segundos.",
        overloaded = "Claude está saturado en este momento. Probá en unos segundos.",
        serverError = "El servidor de Claude tuvo un problema. Probá de nuevo.",
        noCredit = "Tu cuenta de Claude no tiene saldo o llegó a su límite de gasto. Revisalo en platform.claude.com.",
        workspaceKey = "Esta API key sirve para varios workspaces y la app necesita una de un solo workspace. " +
            "Creá otra en platform.claude.com eligiendo un workspace (por ejemplo, Default).",
        apiError = "Claude respondió con un error: ",
        webFailed = "No pude usar internet en esta respuesta: ",
        refusal = "Con eso no te puedo ayudar.",
        emptyReply = "No supe qué responderte. ¿Me lo decís de otra forma?",
        searched = "Buscó en internet",
        stopAck = "Dale, acá estoy cuando me necesites.",
        stopPhrases = listOf(
            "chau", "chao", "nada", "nada mas", "eso es todo", "listo", "listo gracias",
            "gracias", "muchas gracias", "basta", "silencio", "dejalo", "ya esta"
        )
    )

    val en = Strings(
        greeting = "What do you need?",
        widgetCaptions = listOf("Tap me", "What do you need?", "Let's talk"),
        listening = "Listening…",
        thinking = "Thinking…",
        speaking = "Speaking…",
        idleHint = "Tap me or use the mic to talk",
        inputHint = "Type a message…",
        handsFree = "Hands-free",
        web = "Web",
        webOn = "Web search on",
        webOff = "Web search off",
        handsFreeOn = "Hands-free: she listens again after each answer",
        handsFreeOff = "Hands-free off",
        settings = "Settings",
        send = "Send",
        talk = "Talk",
        languageLabel = "Language",
        webLabel = "Search the web",
        webHelp = "Lets her look up current info: weather, news, prices, scores. " +
            "Each search adds a small cost to your Claude account.",
        nameLabel = "Assistant name",
        keyLabel = "Claude API key",
        keyHelp = "Stored only on this phone. Create it at platform.claude.com › API keys, " +
            "for a single workspace. Every request is billed to that account.",
        modelLabel = "Model",
        modelSmart = "Smart",
        modelFast = "Fast",
        modelHelp = "Smart uses Claude Sonnet. Fast uses Claude Haiku: quicker and cheaper.",
        animLabel = "Animated widget",
        animHelp = "Turn it off for a static widget that uses less battery.",
        save = "Save",
        noKey = "First add your Claude API key in settings.",
        noSpeech = "I didn't catch that. Try again.",
        micPermission = "I need microphone permission to hear you. Meanwhile you can type.",
        sttUnavailable = "Speech recognition isn't available on this phone. " +
            "Install or enable “Speech Services by Google”, or type instead.",
        sttNetwork = "Speech recognition needs an internet connection.",
        sttBusy = "The microphone is busy. Try again.",
        sttLanguage = "Speech recognition doesn't have this language available on the phone.",
        sttOther = "Couldn't listen (code %d).",
        ttsUnavailable = "No voice is installed for this language. " +
            "Check “Text-to-speech output” in the phone settings.",
        noConnection = "No internet connection.",
        timeout = "The reply took too long. Try again.",
        badKey = "The API key isn't valid or has expired. Create a new one at platform.claude.com and add it in settings.",
        forbidden = "Your API key isn't allowed to use this model.",
        rateLimited = "Too many requests in a row. Try again in a few seconds.",
        overloaded = "Claude is overloaded right now. Try again in a few seconds.",
        serverError = "Claude's server had a problem. Try again.",
        noCredit = "Your Claude account is out of credit or reached its spend limit. Check it at platform.claude.com.",
        workspaceKey = "This API key works across several workspaces and the app needs a single-workspace key. " +
            "Create another one at platform.claude.com and pick one workspace (for example, Default).",
        apiError = "Claude returned an error: ",
        webFailed = "Couldn't use the web for this answer: ",
        refusal = "I can't help with that.",
        emptyReply = "I'm not sure what to say. Could you rephrase?",
        searched = "Searched the web",
        stopAck = "Okay, I'm here when you need me.",
        stopPhrases = listOf(
            "bye", "goodbye", "nothing", "never mind", "nevermind", "that's all", "that is all",
            "thanks", "thank you", "no thanks", "stop", "be quiet"
        )
    )
}

/** Instrucciones de sistema para Claude, en el idioma elegido. */
object Prompts {
    fun system(lang: Lang, name: String, web: Boolean, dateTime: String, timeZone: String): String =
        if (lang == Lang.EN) english(name, web, dateTime, timeZone) else spanish(name, web, dateTime, timeZone)

    private fun spanish(name: String, web: Boolean, dateTime: String, timeZone: String): String {
        val internet = if (web) {
            "Tenés búsqueda web. Usala siempre que te pregunten por algo actual o que pueda haber cambiado: " +
                "noticias, clima, precios, cotizaciones, resultados, horarios. No anuncies que vas a buscar " +
                "ni nombres las fuentes salvo que te las pidan: contestá directo con el dato."
        } else {
            "En este momento no tenés acceso a internet. Si te preguntan por algo actual, avisá que no " +
                "podés consultarlo y respondé con lo que sepas."
        }
        return "Sos $name, una asistente personal que conversa por voz desde el celular de tu usuario. " +
            "Hablás en español rioplatense, de vos, con tono cálido, directo y con algo de chispa. " +
            "Todo lo que escribís se lee en voz alta, así que respondé corto: de una a tres oraciones, " +
            "sin markdown, sin listas, sin emojis y sin direcciones web. " +
            "Respondé siempre en español, aunque te hablen en otro idioma.\n\n" +
            internet + "\n\n" +
            "Si te falta un dato para ayudar, preguntá una sola cosa. Si no sabés algo, decilo sin vueltas.\n\n" +
            "Fecha y hora del usuario: $dateTime. Zona horaria: $timeZone."
    }

    private fun english(name: String, web: Boolean, dateTime: String, timeZone: String): String {
        val internet = if (web) {
            "You have web search. Use it whenever you're asked about something current or that may have " +
                "changed: news, weather, prices, exchange rates, scores, schedules. Don't announce that " +
                "you're searching and don't name sources unless asked: just answer with the facts."
        } else {
            "You don't have internet access right now. If asked about something current, say you can't " +
                "look it up and answer with what you know."
        }
        return "You are $name, a personal assistant who talks by voice from your user's phone. " +
            "You speak natural, warm, direct English with a bit of spark. " +
            "Everything you write is read aloud, so keep it short: one to three sentences, " +
            "no markdown, no lists, no emojis and no web addresses. " +
            "Always answer in English, even if you're spoken to in another language.\n\n" +
            internet + "\n\n" +
            "If you need one more detail to help, ask a single question. If you don't know something, say so plainly.\n\n" +
            "User's date and time: $dateTime. Time zone: $timeZone."
    }
}
