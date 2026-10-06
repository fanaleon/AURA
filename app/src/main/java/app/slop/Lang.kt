package app.slop

import java.util.Locale

/** Idiomas en los que la asistente escucha, habla y muestra la interfaz. */
enum class Lang(val code: String, val sttTag: String, val label: String) {
    ES("es", "es-AR", "Español"),
    EN("en", "en-US", "English");

    /** Voces a probar, en orden, hasta encontrar una instalada en el celu. */
    fun ttsLocales(): List<Locale> = when (this) {
        ES -> listOf(
            Locale("es", "AR"),
            Locale("es", "US"),
            Locale("es", "MX"),
            Locale("es", "ES"),
            Locale("es")
        )
        EN -> listOf(Locale.US, Locale.UK, Locale.ENGLISH)
    }

    fun formatLocale(): Locale = if (this == EN) Locale.US else Locale("es", "AR")

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: deviceDefault()

        fun deviceDefault(): Lang = if (Locale.getDefault().language == "en") EN else ES
    }
}
