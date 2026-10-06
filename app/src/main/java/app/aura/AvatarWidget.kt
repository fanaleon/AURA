package app.aura

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/**
 * Widget de la modelo.
 *
 * La animación está definida en res/layout/widget_avatar.xml y corre sola dentro del launcher
 * (no hace falta que la app esté abierta). Acá solo se elige el encuadre de la foto según el
 * tamaño del widget, se ponen los textos en el idioma elegido y se conecta el toque.
 */
class AvatarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?
    ) {
        // Se llama al cambiarle el tamaño: puede hacer falta otro encuadre de la foto.
        update(context, manager, id)
    }

    companion object {
        /** Redibuja todos los widgets (después de cambiar idioma o animación en ajustes). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AvatarWidget::class.java)) ?: return
            for (id in ids) update(context, manager, id)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id)))
        }

        private fun build(context: Context, options: Bundle?): RemoteViews {
            val animated = Prefs.widgetAnimated(context)
            val captions = Texts.of(Prefs.lang(context)).widgetCaptions
            val views = RemoteViews(
                context.packageName,
                if (animated) R.layout.widget_avatar else R.layout.widget_avatar_static
            )

            // Tamaño en dp con el celu en vertical: ancho mínimo x alto máximo.
            val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            views.setImageViewResource(R.id.w_photo, photoFor(widthDp, heightDp))

            if (animated) {
                views.setTextViewText(R.id.w_cap0, captions[0])
                views.setTextViewText(R.id.w_cap1, captions[1])
                views.setTextViewText(R.id.w_cap2, captions[2])
                // En widgets muy angostos el texto no entra: queda solo el micrófono latiendo.
                val narrow = widthDp in 1 until NARROW_DP
                views.setViewVisibility(R.id.w_captions, if (narrow) View.GONE else View.VISIBLE)
            } else {
                views.setTextViewText(R.id.w_cap0, captions[1])
                val narrow = widthDp in 1 until NARROW_DP
                views.setViewVisibility(R.id.w_cap0, if (narrow) View.GONE else View.VISIBLE)
            }

            views.setOnClickPendingIntent(android.R.id.background, talkIntent(context))
            return views
        }

        /** Elige el recorte de la foto que mejor calza con la forma del widget. */
        private fun photoFor(widthDp: Int, heightDp: Int): Int {
            if (widthDp <= 0 || heightDp <= 0) return R.drawable.w_tall
            val aspect = widthDp.toFloat() / heightDp
            return when {
                aspect <= 0.62f -> R.drawable.w_tall      // alto: de la cabeza a la pollera
                aspect <= 0.92f -> R.drawable.w_mid       // intermedio: de la cabeza a la cintura
                aspect <= 1.45f -> R.drawable.w_square    // cuadrado: cabeza y hombros
                else -> R.drawable.w_wide                 // apaisado: cabeza y hombros con aire a los lados
            }
        }

        private fun talkIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_TALK)
                .putExtra(MainActivity.EXTRA_TALK, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private const val NARROW_DP = 120
    }
}
