package app.aura

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados en el almacenamiento privado de la app. */
object Prefs {
    private const val FILE = "aura"
    private const val KEY_API = "api_key"
    private const val KEY_NAME = "name"
    private const val KEY_LANG = "lang"
    private const val KEY_WEB = "web"
    private const val KEY_FAST = "fast_model"
    private const val KEY_ANIM = "widget_animated"
    private const val KEY_HANDS = "hands_free"

    const val DEFAULT_NAME = "Aura"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(ctx: Context): String = sp(ctx).getString(KEY_API, "").orEmpty().trim()

    fun name(ctx: Context): String =
        sp(ctx).getString(KEY_NAME, DEFAULT_NAME).orEmpty().trim().ifBlank { DEFAULT_NAME }

    fun lang(ctx: Context): Lang = Lang.fromCode(sp(ctx).getString(KEY_LANG, null))

    /** Búsqueda en internet activada (por defecto sí). */
    fun web(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_WEB, true)

    fun setWeb(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_WEB, on).apply()

    /** true = modelo rápido y económico; false = el más inteligente. */
    fun fastModel(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_FAST, false)

    fun widgetAnimated(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ANIM, true)

    fun handsFree(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_HANDS, false)

    fun setHandsFree(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_HANDS, on).apply()

    fun save(
        ctx: Context,
        apiKey: String,
        name: String,
        lang: Lang,
        web: Boolean,
        fastModel: Boolean,
        widgetAnimated: Boolean
    ) {
        sp(ctx).edit()
            .putString(KEY_API, apiKey.trim())
            .putString(KEY_NAME, name.trim())
            .putString(KEY_LANG, lang.code)
            .putBoolean(KEY_WEB, web)
            .putBoolean(KEY_FAST, fastModel)
            .putBoolean(KEY_ANIM, widgetAnimated)
            .apply()
    }
}
