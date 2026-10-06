package app.slop

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados en el almacenamiento privado de la app. */
object Prefs {
    private const val FILE = "slop"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_CLAUDE = "api_key"
    private const val KEY_API_GEMINI = "gemini_key"
    private const val KEY_SEARCH = "search_key"
    private const val KEY_NAME = "name"
    private const val KEY_LANG = "lang"
    private const val KEY_WEB = "web"
    private const val KEY_FAST = "fast_model"
    private const val KEY_ANIM = "widget_animated"
    private const val KEY_HANDS = "hands_free"

    const val DEFAULT_NAME = "Slop"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Con qué IA piensa: Gemini (gratis) si no se eligió otra. */
    fun provider(ctx: Context): Provider = Provider.fromCode(sp(ctx).getString(KEY_PROVIDER, null))

    fun keyFor(ctx: Context, provider: Provider): String {
        val name = if (provider == Provider.CLAUDE) KEY_API_CLAUDE else KEY_API_GEMINI
        return sp(ctx).getString(name, "").orEmpty().trim()
    }

    /** La API key de la IA elegida. */
    fun apiKey(ctx: Context): String = keyFor(ctx, provider(ctx))

    /** La key de Tavily, que Gemini necesita para buscar en internet. */
    fun searchKey(ctx: Context): String = sp(ctx).getString(KEY_SEARCH, "").orEmpty().trim()

    fun name(ctx: Context): String =
        sp(ctx).getString(KEY_NAME, DEFAULT_NAME).orEmpty().trim().ifBlank { DEFAULT_NAME }

    fun lang(ctx: Context): Lang = Lang.fromCode(sp(ctx).getString(KEY_LANG, null))

    /** Búsqueda en internet pedida por el usuario (por defecto sí). */
    fun web(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_WEB, true)

    fun setWeb(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_WEB, on).apply()

    /** Con Gemini no hay internet hasta cargar la key de Tavily. */
    fun needsSearchKey(ctx: Context): Boolean =
        provider(ctx) == Provider.GEMINI && searchKey(ctx).isBlank()

    /** Búsqueda en internet realmente disponible para la próxima consulta. */
    fun webActive(ctx: Context): Boolean = web(ctx) && !needsSearchKey(ctx)

    /** true = modelo rápido; false = el más inteligente. */
    fun fastModel(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_FAST, false)

    fun widgetAnimated(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ANIM, true)

    fun handsFree(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_HANDS, false)

    fun setHandsFree(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_HANDS, on).apply()

    fun save(
        ctx: Context,
        provider: Provider,
        geminiKey: String,
        claudeKey: String,
        searchKey: String,
        name: String,
        lang: Lang,
        web: Boolean,
        fastModel: Boolean,
        widgetAnimated: Boolean
    ) {
        sp(ctx).edit()
            .putString(KEY_PROVIDER, provider.code)
            .putString(KEY_API_GEMINI, geminiKey.trim())
            .putString(KEY_API_CLAUDE, claudeKey.trim())
            .putString(KEY_SEARCH, searchKey.trim())
            .putString(KEY_NAME, name.trim())
            .putString(KEY_LANG, lang.code)
            .putBoolean(KEY_WEB, web)
            .putBoolean(KEY_FAST, fastModel)
            .putBoolean(KEY_ANIM, widgetAnimated)
            .apply()
    }
}
