package app.slop

import kotlin.math.max
import kotlin.math.min

/**
 * Convierte lo que la asistente está diciendo en formas de boca, al ritmo de la voz.
 *
 * Al empezar una frase se arma una línea de tiempo con la forma de boca de cada letra (en
 * español se escribe casi como suena). El motor de voz avisa palabra por palabra por dónde va,
 * y con cada aviso el reloj interno se reacomoda y afina la velocidad. Si el motor no avisa,
 * la boca igual se mueve con el ritmo estimado.
 *
 * No usa nada de Android, así se puede probar en una PC.
 */
class LipSync {

    class Shape {
        /** 0 boca cerrada, 1 bien abierta. */
        var open = 0f

        /** -1 labios redondeados ("o", "u"), +1 estirados ("i", "e"). */
        var wide = 0f
    }

    private var keyPos = FloatArray(64)
    private var keyOpen = FloatArray(64)
    private var keyWide = FloatArray(64)
    private var keys = 0
    private var unitAt = FloatArray(1)      // unidades de tiempo transcurridas antes de cada letra
    private var length = 0
    private var total = 0f

    private var active = false
    private var anchorMs = 0L
    private var anchorPos = 0f
    private var limit = Float.MAX_VALUE
    private var gotRange = false
    private var lastRangePos = -1f
    private var lastRangeMs = 0L

    /** Velocidad estimada de la voz, en unidades por milisegundo. Se va afinando sola. */
    var rate = DEFAULT_RATE
        private set

    /** Cantidad de palabras empezadas desde que arrancó la app: sirve para marcar el ritmo con la cabeza. */
    var words = 0
        private set

    /** Largo de la última palabra empezada, en unidades. */
    var lastWordUnits = 0f
        private set

    val speaking: Boolean
        get() = active

    /** Empieza una frase. [english] cambia cómo suenan algunas letras. */
    fun start(text: String, english: Boolean, nowMs: Long) {
        build(text, english)
        active = true
        anchorMs = nowMs
        anchorPos = 0f
        limit = Float.MAX_VALUE
        gotRange = false
        lastRangePos = -1f
        lastRangeMs = nowMs
        words++
        lastWordUnits = 3f
    }

    /** El motor de voz está por decir las letras [start, end) de la frase. */
    fun range(start: Int, end: Int, nowMs: Long) {
        if (!active) return
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        val pos = unitAt[s]
        val dt = nowMs - lastRangeMs
        if (gotRange && pos > lastRangePos + 0.5f && dt in 60L..3000L) {
            val measured = ((pos - lastRangePos) / dt).coerceIn(MIN_RATE, MAX_RATE)
            rate += (measured - rate) * 0.3f
        }
        anchorPos = pos
        anchorMs = nowMs
        limit = unitAt[e] + 0.4f
        gotRange = true
        lastRangePos = pos
        lastRangeMs = nowMs
        words++
        lastWordUnits = unitAt[e] - pos
    }

    fun stop() {
        active = false
    }

    /** Forma de la boca en este instante. */
    fun sample(nowMs: Long, out: Shape) {
        out.open = 0f
        out.wide = 0f
        if (!active || keys == 0) return
        var pos = anchorPos + rate * (nowMs - anchorMs + LEAD_MS)
        // Con avisos del motor no se pasa del final de la palabra en curso: espera el próximo aviso.
        if (gotRange) pos = min(pos, limit)
        if (pos <= keyPos[0] || pos >= keyPos[keys - 1]) return
        var lo = 0
        var hi = keys - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (keyPos[mid] <= pos) lo = mid else hi = mid
        }
        val span = keyPos[hi] - keyPos[lo]
        val f = if (span <= 0f) 0f else (pos - keyPos[lo]) / span
        val ease = f * f * (3f - 2f * f)
        out.open = keyOpen[lo] + (keyOpen[hi] - keyOpen[lo]) * ease
        out.wide = keyWide[lo] + (keyWide[hi] - keyWide[lo]) * ease
    }

    /** Duración estimada de la frase cargada, en milisegundos, a la velocidad actual. */
    fun estimatedMs(): Long = (total / rate).toLong()

    // ------------------------------------------------------------------ línea de tiempo

    private fun build(text: String, english: Boolean) {
        length = text.length
        if (unitAt.size < length + 1) unitAt = FloatArray(length + 1)
        keys = 0
        var pos = 0f
        key(0f, 0f, 0f)
        for (i in 0 until length) {
            unitAt[i] = pos
            val c = text[i].lowercaseChar()
            val prev = if (i > 0) text[i - 1].lowercaseChar() else ' '
            val next = if (i + 1 < length) text[i + 1].lowercaseChar() else ' '
            var dur: Float
            when (c) {
                'a', 'á', 'à', 'â', 'ä' -> { dur = 1f; key(pos + 0.5f, 1f, 0.15f) }
                'e', 'é', 'è', 'ê', 'ë' -> { dur = 1f; key(pos + 0.5f, 0.58f, 0.6f) }
                'i', 'í', 'ì', 'î', 'ï' -> { dur = 0.9f; key(pos + 0.45f, 0.34f, 0.9f) }
                'o', 'ó', 'ò', 'ô', 'ö' -> { dur = 1f; key(pos + 0.5f, 0.72f, -0.8f) }
                'u', 'ú', 'ù', 'û', 'ü' -> {
                    // En "que", "qui", "gue", "gui" la u no suena.
                    val silent = !english && c == 'u' && (prev == 'q' || (prev == 'g' && (next == 'e' || next == 'i' || next == 'é' || next == 'í')))
                    if (silent) dur = 0f else { dur = 0.9f; key(pos + 0.45f, 0.36f, -1f) }
                }
                'y' -> {
                    // Sola o al final de palabra suena como "i".
                    if (!next.isLetter()) { dur = 0.8f; key(pos + 0.4f, 0.32f, 0.8f) } else { dur = 0.6f; key(pos + 0.3f, 0.24f, 0.4f) }
                }
                'h' -> dur = if (english) 0.4f else 0f
                'm', 'b', 'p' -> { dur = 0.8f; key(pos + 0.4f, 0f, 0f) }
                'v' -> { dur = 0.75f; if (english) key(pos + 0.38f, 0.1f, 0.3f) else key(pos + 0.38f, 0f, 0f) }
                'f' -> { dur = 0.75f; key(pos + 0.38f, 0.1f, 0.3f) }
                'w' -> { dur = 0.7f; key(pos + 0.35f, 0.3f, -0.9f) }
                in '0'..'9' -> {
                    // Un número se dice con varias sílabas que acá no se conocen: se imita el vaivén.
                    dur = 3.2f
                    key(pos + 0.8f, 0.62f, 0.2f)
                    key(pos + 1.6f, 0.14f, 0f)
                    key(pos + 2.4f, 0.56f, -0.3f)
                }
                ' ' -> { dur = 0.25f; key(pos + 0.12f, 0.07f, 0f) }
                ',', ';', ':' -> { dur = 2.6f; key(pos + 0.35f, 0f, 0f); key(pos + 2.25f, 0f, 0f) }
                '.', '!', '?', '…', '\n' -> { dur = 4.2f; key(pos + 0.4f, 0f, 0f); key(pos + 3.8f, 0f, 0f) }
                else -> {
                    if (c.isLetter()) {
                        // Resto de las consonantes: la boca queda entreabierta. La segunda de "rr" o "ll" casi no suma.
                        dur = if (c == prev) 0.3f else 0.65f
                        key(pos + dur * 0.5f, 0.22f, 0.25f)
                    } else {
                        dur = 0.2f
                    }
                }
            }
            // Vocal acentuada: un poco más larga.
            if (c == 'á' || c == 'é' || c == 'í' || c == 'ó' || c == 'ú') dur *= 1.2f
            pos += dur
        }
        unitAt[length] = pos
        key(pos + 0.6f, 0f, 0f)
        total = pos + 0.6f
    }

    private fun key(pos: Float, open: Float, wide: Float) {
        if (keys == keyPos.size) {
            keyPos = keyPos.copyOf(keys * 2)
            keyOpen = keyOpen.copyOf(keys * 2)
            keyWide = keyWide.copyOf(keys * 2)
        }
        // Las posiciones tienen que ir en orden: si dos letras quedan pegadas, la nueva va apenas después.
        val p = if (keys > 0) max(pos, keyPos[keys - 1] + 0.01f) else pos
        keyPos[keys] = p
        keyOpen[keys] = open
        keyWide[keys] = wide
        keys++
    }

    companion object {
        /** Unidades por milisegundo: una vocal es 1 unidad. Arranca en unas 11,5 por segundo. */
        const val DEFAULT_RATE = 0.0115f
        private const val MIN_RATE = 0.005f
        private const val MAX_RATE = 0.03f

        /** La boca se adelanta un poco al sonido, como al hablar de verdad. */
        private const val LEAD_MS = 30L
    }
}
