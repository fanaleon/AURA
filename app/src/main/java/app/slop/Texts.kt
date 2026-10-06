package app.slop

/**
 * Todos los textos de la app en un idioma.
 * El idioma se elige dentro de la app (no depende del idioma del celu), por eso
 * no usamos strings.xml para esto.
 *
 * En los textos, {ai} es el nombre de la IA elegida y {site} la página donde se saca su key.
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
    val providerLabel: String,
    val providerFree: String,
    val providerPaid: String,
    val providerHelpGemini: String,
    val providerHelpClaude: String,
    val keyLabel: String,
    val keyHelpGemini: String,
    val keyHelpClaude: String,
    val searchKeyLabel: String,
    val searchKeyHelp: String,
    val webLabel: String,
    val webHelpGemini: String,
    val webHelpClaude: String,
    val nameLabel: String,
    val modelLabel: String,
    val modelSmart: String,
    val modelFast: String,
    val modelHelpGemini: String,
    val modelHelpClaude: String,
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
    val freeLimit: String,
    val overloaded: String,
    val serverError: String,
    val noCredit: String,
    val workspaceKey: String,
    val modelGone: String,
    val apiError: String,
    val webFailed: String,
    val searchKeyNeeded: String,
    val searchBadKey: String,
    val searchQuota: String,
    val refusal: String,
    val emptyReply: String,
    val searched: String,
    // Manos libres
    val stopAck: String,
    val stopPhrases: List<String>
)

/** Completa {ai} y {site} con los datos de la IA elegida. */
fun String.forAi(provider: Provider): String =
    replace("{ai}", provider.label).replace("{site}", provider.site)

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
        providerLabel = "Cerebro",
        providerFree = "Gemini · gratis",
        providerPaid = "Claude · pago",
        providerHelpGemini = "Gemini es gratis, con un tope de consultas por día. " +
            "Google puede usar lo que le decís para mejorar sus productos.",
        providerHelpClaude = "Claude se paga por consulta. Es más capaz y trae su propia búsqueda en internet.",
        keyLabel = "API key de {ai}",
        keyHelpGemini = "Se guarda solo en este celu. Creala gratis en aistudio.google.com › Get API key, " +
            "con tu cuenta de Google.",
        keyHelpClaude = "Se guarda solo en este celu. Creala en platform.claude.com › API keys, " +
            "para un solo workspace. Cada consulta se cobra a esa cuenta.",
        searchKeyLabel = "API key de Tavily (internet)",
        searchKeyHelp = "Opcional. Con esta key puede buscar en internet: son 1.000 búsquedas gratis " +
            "por mes. Creala en app.tavily.com.",
        webLabel = "Buscar en internet",
        webHelpGemini = "Le permite consultar datos actuales: clima, noticias, precios, resultados. " +
            "Necesita la key de Tavily.",
        webHelpClaude = "Le permite consultar datos actuales: clima, noticias, precios, resultados. " +
            "Cada búsqueda suma un pequeño costo a tu cuenta de Claude.",
        nameLabel = "Nombre de la asistente",
        modelLabel = "Modelo",
        modelSmart = "Inteligente",
        modelFast = "Rápida",
        modelHelpGemini = "Inteligente usa Gemini Flash. Rápida usa Gemini Flash-Lite, que responde antes. " +
            "Si una llega a su límite gratis, sigue con la otra.",
        modelHelpClaude = "Inteligente usa Claude Sonnet. Rápida usa Claude Haiku: responde antes y cuesta menos.",
        animLabel = "Widget animado",
        animHelp = "Si lo apagás, el widget queda fijo y gasta menos batería.",
        save = "Guardar",
        noKey = "Primero cargá tu API key de {ai} en ajustes.",
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
        badKey = "La API key de {ai} no es válida o venció. Creá una nueva en {site} y cargala en ajustes.",
        forbidden = "Tu API key de {ai} no tiene permiso para usar este modelo.",
        rateLimited = "Demasiadas consultas seguidas. Probá en unos segundos.",
        freeLimit = "Llegaste al límite gratis de Gemini por ahora. Probá de nuevo en un rato.",
        overloaded = "{ai} está saturado en este momento. Probá en unos segundos.",
        serverError = "El servidor de {ai} tuvo un problema. Probá de nuevo.",
        noCredit = "Tu cuenta de {ai} no tiene saldo o llegó a su límite de gasto. Revisalo en {site}.",
        workspaceKey = "Esta API key sirve para varios workspaces y la app necesita una de un solo workspace. " +
            "Creá otra en platform.claude.com eligiendo un workspace (por ejemplo, Default).",
        modelGone = "El modelo de {ai} que usa la app ya no está disponible. Hay que actualizar la app.",
        apiError = "{ai} respondió con un error: ",
        webFailed = "No pude usar internet en esta respuesta: ",
        searchKeyNeeded = "Para buscar en internet cargá tu key gratuita de Tavily.",
        searchBadKey = "No busqué en internet: la key de Tavily no es válida. Revisala en ajustes.",
        searchQuota = "No busqué en internet: se acabaron las búsquedas gratis de Tavily de este mes.",
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
        providerLabel = "Brain",
        providerFree = "Gemini · free",
        providerPaid = "Claude · paid",
        providerHelpGemini = "Gemini is free, with a daily cap on requests. " +
            "Google may use what you say to improve its products.",
        providerHelpClaude = "Claude is pay-per-use. It's more capable and has its own web search.",
        keyLabel = "{ai} API key",
        keyHelpGemini = "Stored only on this phone. Create it for free at aistudio.google.com › Get API key, " +
            "with your Google account.",
        keyHelpClaude = "Stored only on this phone. Create it at platform.claude.com › API keys, " +
            "for a single workspace. Every request is billed to that account.",
        searchKeyLabel = "Tavily API key (web)",
        searchKeyHelp = "Optional. This key lets her search the web: 1,000 free searches a month. " +
            "Create it at app.tavily.com.",
        webLabel = "Search the web",
        webHelpGemini = "Lets her look up current info: weather, news, prices, scores. " +
            "Needs the Tavily key.",
        webHelpClaude = "Lets her look up current info: weather, news, prices, scores. " +
            "Each search adds a small cost to your Claude account.",
        nameLabel = "Assistant name",
        modelLabel = "Model",
        modelSmart = "Smart",
        modelFast = "Fast",
        modelHelpGemini = "Smart uses Gemini Flash. Fast uses Gemini Flash-Lite, which answers sooner. " +
            "If one hits its free limit, she carries on with the other.",
        modelHelpClaude = "Smart uses Claude Sonnet. Fast uses Claude Haiku: quicker and cheaper.",
        animLabel = "Animated widget",
        animHelp = "Turn it off for a static widget that uses less battery.",
        save = "Save",
        noKey = "First add your {ai} API key in settings.",
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
        badKey = "The {ai} API key isn't valid or has expired. Create a new one at {site} and add it in settings.",
        forbidden = "Your {ai} API key isn't allowed to use this model.",
        rateLimited = "Too many requests in a row. Try again in a few seconds.",
        freeLimit = "You've hit Gemini's free limit for now. Try again in a while.",
        overloaded = "{ai} is overloaded right now. Try again in a few seconds.",
        serverError = "The {ai} server had a problem. Try again.",
        noCredit = "Your {ai} account is out of credit or reached its spend limit. Check it at {site}.",
        workspaceKey = "This API key works across several workspaces and the app needs a single-workspace key. " +
            "Create another one at platform.claude.com and pick one workspace (for example, Default).",
        modelGone = "The {ai} model this app uses is no longer available. The app needs an update.",
        apiError = "{ai} returned an error: ",
        webFailed = "Couldn't use the web for this answer: ",
        searchKeyNeeded = "To search the web, add your free Tavily key.",
        searchBadKey = "I didn't search the web: the Tavily key isn't valid. Check it in settings.",
        searchQuota = "I didn't search the web: this month's free Tavily searches have run out.",
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

/** Instrucciones de sistema para la IA, en el idioma elegido. */
object Prompts {
    /** [country] es el nombre del país del usuario, o vacío si no se sabe. */
    fun system(
        lang: Lang,
        name: String,
        web: Boolean,
        dateTime: String,
        timeZone: String,
        country: String = ""
    ): String =
        if (lang == Lang.EN) {
            english(name, web, dateTime, timeZone, country.trim())
        } else {
            spanish(name, web, dateTime, timeZone, country.trim())
        }

    private fun spanish(name: String, web: Boolean, dateTime: String, timeZone: String, country: String): String {
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
            "Fecha y hora del usuario: $dateTime. Zona horaria: $timeZone." +
            (if (country.isEmpty()) "" else " País: $country.")
    }

    private fun english(name: String, web: Boolean, dateTime: String, timeZone: String, country: String): String {
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
            "User's date and time: $dateTime. Time zone: $timeZone." +
            (if (country.isEmpty()) "" else " Country: $country.")
    }
}
