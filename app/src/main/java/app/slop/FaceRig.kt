package app.slop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Dónde está cada cosa en avatar_full.jpg (850 x 1915 px). Todo el motor de animación trabaja en
 * estas coordenadas; si cambiás la foto, estos son los números a ajustar.
 */
object FaceMap {
    const val IMG_W = 850
    const val IMG_H = 1915

    // Recorte de la cabeza: head.jpg es esta zona al doble de resolución y sin el micrófono
    // (con la piel y el labio de atrás reconstruidos); head_mic.png es el micrófono recortado.
    const val HEAD_X = 318f
    const val HEAD_Y = 294f
    const val HEAD_W = 222f
    const val HEAD_H = 264f
    const val HEAD_ZOOM = 2

    const val CENTER_X = 427f        // eje del cuerpo
    const val HAIR_TOP = 305f        // borde superior del pelo
    const val FACE_X = 428f          // centro de la cara
    const val FACE_Y = 420f
    const val CHIN_Y = 512f
    const val HEAD_WIDTH = 215f      // ancho de la cabeza con el pelo
    const val NECK_Y = 540f          // donde pivotea la cabeza

    // Unión de los labios, de comisura a comisura (x, y).
    val SEAM = floatArrayOf(
        407.5f, 478.1f, 410f, 478.6f, 416f, 479.0f, 423f, 478.6f, 430f, 477.6f,
        436f, 477.0f, 442f, 477.4f, 448f, 478.2f, 453f, 478.4f
    )
    const val MOUTH_X = 430.25f      // centro y medio ancho de la boca (de comisura a comisura)
    const val MOUTH_HALF = 22.75f
    const val SEAM_TO_CHIN = 32f

    /**
     * Un ojo. [upper] es el borde de abajo de las pestañas de arriba (por ahí se "corta" el párpado)
     * y [lower] es adónde llega ese borde con el ojo cerrado. Las dos son líneas (x, y) de comisura
     * a comisura, con x creciente.
     */
    class Eye(val upper: FloatArray, val lower: FloatArray, val centerX: Float, val centerY: Float) {
        val x0: Float get() = upper[0]
        val x1: Float get() = upper[upper.size - 2]
    }

    val EYE_L = Eye(   // el de la izquierda de la foto
        floatArrayOf(
            385.5f, 416.2f, 388f, 415.9f, 391f, 415.1f, 394f, 414.7f, 397f, 414.3f, 400f, 414.0f,
            403f, 415.0f, 406f, 416.5f, 408f, 418.4f, 410f, 419.7f, 412f, 420.4f, 413.2f, 420.6f
        ),
        floatArrayOf(
            385.5f, 416.2f, 388f, 419.3f, 391f, 421.3f, 394f, 422.4f, 398f, 423.0f, 402f, 423.0f,
            406f, 422.6f, 409f, 421.9f, 411.5f, 421.0f, 413.2f, 420.6f
        ),
        398.6f, 417.4f
    )
    val EYE_R = Eye(
        floatArrayOf(
            444.3f, 419.8f, 446f, 419.3f, 448f, 417.0f, 450f, 415.8f, 452f, 414.9f, 455f, 414.1f, 458f, 413.8f,
            461f, 414.0f, 464f, 414.7f, 466f, 415.9f, 468f, 417.0f, 470f, 417.6f, 472.2f, 417.4f
        ),
        floatArrayOf(
            444.3f, 419.8f, 446f, 420.8f, 448f, 421.8f, 451f, 422.6f, 455f, 423.0f, 459f, 423.0f,
            463f, 422.3f, 466f, 421.4f, 469f, 419.8f, 472.2f, 417.4f
        ),
        457.6f, 417.0f
    )
    const val LID_TOP = 401f         // arriba de esta línea el párpado no se mueve
    const val LID_RIGID = 4.5f       // alto de la franja de pestañas y delineado, que baja sin estirarse
    const val BROW_Y = 399f
}

/** Aspecto del interior de la boca, que se dibuja en el hueco entre los labios. */
object MouthLook {
    const val MIN_GAP = 0.25f        // con menos apertura que esto no se dibuja nada
    const val TEETH_FROM = 3f        // apertura a partir de la cual asoman los dientes
    const val DARK = 0xFF2A0F14.toInt()
    const val TEETH = 0xB8D8CEC6.toInt()

    /** Cuánto se mete el hueco bajo el labio de arriba, para no dejar rendijas. Nada en las comisuras. */
    fun overTop(gap: Float): Float = min(0.3f, gap * 0.5f)

    /** Ídem bajo el labio de abajo. */
    fun overBottom(gap: Float): Float = min(0.8f, gap)

    /** Alto de la franja de dientes en el punto [i] de [n], para un hueco de [gap] px. */
    fun teeth(i: Int, n: Int, gap: Float): Float {
        val s = i / (n - 1f)
        val window = FaceRig.smooth(0.15f, 0.36f, s) * (1f - FaceRig.smooth(0.64f, 0.85f, s))
        return min(gap * 0.3f, 1.7f) * window
    }
}

/** La pose de un instante: qué tan abierta está la boca, hacia dónde mira, etc. */
class Pose {
    var breath = 0f      // 0 = sin aire, 1 = pecho lleno
    var sway = 0f        // balanceo del cuerpo, -1..1
    var roll = 0f        // inclinación de la cabeza hacia un hombro, -1..1
    var headX = 0f       // desplazamiento de la cabeza, en px de la foto
    var headY = 0f
    var yaw = 0f         // giro de la cabeza a los lados, -1..1
    var pitch = 0f       // cabeza arriba (-) o abajo (+), -1..1
    var jaw = 0f         // mandíbula: 0 cerrada, 1 bien abierta
    var lipUp = 0f       // labio de arriba levantado, 0..1
    var wide = 0f        // comisuras: -1 boca redonda ("o", "u"), +1 estirada ("i", "e")
    var smile = 0f       // 0..1
    var browL = 0f       // cejas: + arriba, - abajo
    var browR = 0f
    var gazeX = 0f       // mirada, -1..1
    var gazeY = 0f
    var blinkL = 0f      // 0 ojo abierto, 1 cerrado
    var blinkR = 0f
}

/**
 * Convierte una [Pose] en la forma de las mallas con que se dibuja la foto.
 *
 * La foto se dibuja en capas, de atrás hacia adelante:
 *  1. el cuerpo entero (malla gruesa);
 *  2. la cara sin el micrófono ("base");
 *  3. el interior de la boca, en el hueco que se abre entre los labios;
 *  4. la mandíbula: todo lo que está debajo de la unión de los labios, que baja al hablar;
 *  5. los párpados, que bajan sobre los ojos al parpadear;
 *  6. el micrófono, que va fijo a la cabeza y no acompaña a la mandíbula.
 *
 * Cada movimiento es un campo de desplazamientos calculado una sola vez por vértice; en cada
 * cuadro solo se suman. No usa nada de Android, así se puede probar en una PC.
 */
class FaceRig {
    val bodyCols = 68
    val bodyRows = 153
    val headCols = 74
    val headRows = 88

    val bodyVerts = FloatArray((bodyCols + 1) * (bodyRows + 1) * 2)
    val headBase = FloatArray((headCols + 1) * (headRows + 1) * 2)
    val headJaw = FloatArray(headBase.size)
    val headLids = FloatArray(headBase.size)
    val headMic = FloatArray(headBase.size)

    /** Borde de arriba y de abajo del hueco de la boca: [LENS_N] puntos (x, y) cada uno. */
    val lensTop = FloatArray(LENS_N * 2)
    val lensBottom = FloatArray(LENS_N * 2)

    /** Alto máximo del hueco de la boca, en px de la foto. Si es ~0 no hay nada que dibujar. */
    var lensGap = 0f
        private set

    private val bodyRest = FloatArray(bodyVerts.size)
    private val headRest = FloatArray(headBase.size)
    private val bodyGlobal = Array(GLOBALS) { FloatArray(bodyVerts.size) }
    private val headGlobal = Array(GLOBALS) { FloatArray(headBase.size) }
    private val headLocal = Array(LOCALS) { FloatArray(headBase.size) }

    private val lensRest = FloatArray(LENS_N * 2)
    private val lensGlobal = Array(GLOBALS) { FloatArray(LENS_N * 2) }
    private val lensLocal = Array(LOCALS) { FloatArray(LENS_N * 2) }

    private val g = FloatArray(GLOBALS)
    private val l = FloatArray(LOCALS)

    init {
        val tmpG = FloatArray(GLOBALS * 2)
        val tmpL = FloatArray(LOCALS * 2)

        var i = 0
        for (row in 0..bodyRows) {
            for (col in 0..bodyCols) {
                val x = FaceMap.IMG_W * col / bodyCols.toFloat()
                val y = FaceMap.IMG_H * row / bodyRows.toFloat()
                bodyRest[i] = x
                bodyRest[i + 1] = y
                globalAt(x, y, tmpG)
                for (k in 0 until GLOBALS) {
                    bodyGlobal[k][i] = tmpG[k * 2]
                    bodyGlobal[k][i + 1] = tmpG[k * 2 + 1]
                }
                i += 2
            }
        }

        i = 0
        for (row in 0..headRows) {
            for (col in 0..headCols) {
                val x = FaceMap.HEAD_X + FaceMap.HEAD_W * col / headCols
                val y = FaceMap.HEAD_Y + FaceMap.HEAD_H * row / headRows
                headRest[i] = x
                headRest[i + 1] = y
                globalAt(x, y, tmpG)
                localAt(x, y, tmpL)
                for (k in 0 until GLOBALS) {
                    headGlobal[k][i] = tmpG[k * 2]
                    headGlobal[k][i + 1] = tmpG[k * 2 + 1]
                }
                for (k in 0 until LOCALS) {
                    headLocal[k][i] = tmpL[k * 2]
                    headLocal[k][i + 1] = tmpL[k * 2 + 1]
                }
                i += 2
            }
        }

        val x0 = FaceMap.SEAM[0]
        val x1 = FaceMap.SEAM[FaceMap.SEAM.size - 2]
        for (n in 0 until LENS_N) {
            val x = x0 + (x1 - x0) * n / (LENS_N - 1)
            val y = seamY(x)
            lensRest[n * 2] = x
            lensRest[n * 2 + 1] = y
            globalAt(x, y, tmpG)
            localAt(x, y, tmpL)
            for (k in 0 until GLOBALS) {
                lensGlobal[k][n * 2] = tmpG[k * 2]
                lensGlobal[k][n * 2 + 1] = tmpG[k * 2 + 1]
            }
            for (k in 0 until LOCALS) {
                lensLocal[k][n * 2] = tmpL[k * 2]
                lensLocal[k][n * 2 + 1] = tmpL[k * 2 + 1]
            }
        }
        update(Pose())
    }

    /** Recalcula todas las mallas para la pose dada. Barato: se llama en cada cuadro. */
    fun update(p: Pose) {
        g[G_BREATH] = p.breath
        g[G_SWAY] = p.sway
        g[G_ROLL] = p.roll
        g[G_HEAD_X] = p.headX
        g[G_HEAD_Y] = p.headY
        g[G_YAW] = p.yaw
        g[G_PITCH] = p.pitch

        l[L_JAW] = p.jaw
        l[L_LIP_UP] = p.lipUp
        l[L_WIDE] = p.wide
        l[L_SMILE] = p.smile
        l[L_BROW_L] = p.browL
        l[L_BROW_R] = p.browR
        l[L_GAZE_X] = p.gazeX
        l[L_GAZE_Y] = p.gazeY
        l[L_BLINK_L] = p.blinkL
        l[L_BLINK_R] = p.blinkR

        blend(bodyVerts, bodyRest, bodyGlobal, g, null, null, 0, 0)
        // El micrófono solo sigue a la cabeza.
        blend(headMic, headRest, headGlobal, g, null, null, 0, 0)
        // La cara: todo menos mandíbula y párpados, que van en sus propias capas.
        blend(headBase, headRest, headGlobal, g, headLocal, l, L_LIP_UP, L_GAZE_Y)
        // La mandíbula no sigue al labio de arriba: se le quita ese movimiento y se le suma el suyo.
        addScaled(headJaw, headBase, headLocal[L_LIP_UP], -p.lipUp)
        addScaled(headJaw, headJaw, headLocal[L_JAW], p.jaw)
        addScaled(headLids, headBase, headLocal[L_BLINK_L], p.blinkL)
        addScaled(headLids, headLids, headLocal[L_BLINK_R], p.blinkR)

        // Hueco de la boca: arriba queda el labio superior, abajo baja el inferior con la mandíbula.
        blend(lensTop, lensRest, lensGlobal, g, lensLocal, l, L_LIP_UP, L_SMILE)
        addScaled(lensBottom, lensTop, lensLocal[L_LIP_UP], -p.lipUp)
        addScaled(lensBottom, lensBottom, lensLocal[L_JAW], p.jaw)
        var gap = 0f
        for (n in 0 until LENS_N) gap = max(gap, lensBottom[n * 2 + 1] - lensTop[n * 2 + 1])
        lensGap = gap
    }

    private fun blend(
        out: FloatArray, rest: FloatArray,
        globals: Array<FloatArray>, gw: FloatArray,
        locals: Array<FloatArray>?, lw: FloatArray?, from: Int, to: Int
    ) {
        System.arraycopy(rest, 0, out, 0, rest.size)
        for (k in 0 until GLOBALS) {
            val w = gw[k]
            if (w == 0f) continue
            val basis = globals[k]
            for (i in out.indices) out[i] += w * basis[i]
        }
        if (locals == null || lw == null) return
        for (k in from..to) {
            val w = lw[k]
            if (w == 0f) continue
            val basis = locals[k]
            for (i in out.indices) out[i] += w * basis[i]
        }
    }

    private fun addScaled(out: FloatArray, base: FloatArray, basis: FloatArray, w: Float) {
        if (w == 0f) {
            if (out !== base) System.arraycopy(base, 0, out, 0, base.size)
            return
        }
        for (i in out.indices) out[i] = base[i] + w * basis[i]
    }

    // ------------------------------------------------------------------ capas de la cabeza

    /** Las cuatro capas de la cabeza, como píxeles ARGB del tamaño de head.jpg. */
    class Layers(val width: Int, val height: Int, val base: IntArray, val jaw: IntArray, val lids: IntArray, val mic: IntArray)

    /**
     * Arma las capas a partir de head.jpg ([head], la cara sin micrófono) y head_mic.png ([mic]).
     * La mandíbula y los párpados salen de recortar la misma cara con bordes suaves.
     */
    fun buildLayers(head: IntArray, mic: IntArray, width: Int, height: Int): Layers {
        val n = width * height
        require(head.size == n && mic.size == n) { "tamaño de textura inesperado" }
        val base = IntArray(n)
        val jaw = IntArray(n)
        val lids = IntArray(n)
        val sx = FaceMap.HEAD_W / width
        val sy = FaceMap.HEAD_H / height
        for (ty in 0 until height) {
            val y = FaceMap.HEAD_Y + (ty + 0.5f) * sy
            for (tx in 0 until width) {
                val x = FaceMap.HEAD_X + (tx + 0.5f) * sx
                val i = ty * width + tx
                val rgb = head[i] and 0xFFFFFF

                // Base: opaca, con los bordes del recorte esfumados para que no se note la costura.
                val edge = min(
                    min(x - FaceMap.HEAD_X, FaceMap.HEAD_X + FaceMap.HEAD_W - x),
                    min(y - FaceMap.HEAD_Y, FaceMap.HEAD_Y + FaceMap.HEAD_H - y)
                )
                base[i] = argb(smooth(0.5f, 8f, edge), rgb)
                jaw[i] = argb(jawAlpha(x, y), rgb)
                lids[i] = argb(max(lidAlpha(FaceMap.EYE_L, x, y), lidAlpha(FaceMap.EYE_R, x, y)), rgb)
            }
        }
        return Layers(width, height, base, jaw, lids, mic.copyOf())
    }

    private fun argb(alpha: Float, rgb: Int): Int {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or rgb
    }

    /** 1 debajo de la unión de los labios (lo que baja con la mandíbula), 0 arriba. */
    private fun jawAlpha(x: Float, y: Float): Float {
        if (x < 380f || x > 478f || y > 546f) return 0f
        val below = ((y - seamY(x)) / EDGE + 0.5f).coerceIn(0f, 1f)
        return below * smooth(380f, 384f, x) * (1f - smooth(474f, 478f, x)) * (1f - smooth(541f, 546f, y))
    }

    /** 1 sobre el párpado de arriba (lo que baja al parpadear), 0 sobre el ojo. */
    private fun lidAlpha(eye: FaceMap.Eye, x: Float, y: Float): Float {
        if (x <= eye.x0 || x >= eye.x1 || y < 398f) return 0f
        val above = ((line(eye.upper, x) - y) / EDGE + 0.5f).coerceIn(0f, 1f)
        return above * smooth(398f, 400.5f, y) * smooth(eye.x0, eye.x0 + 1.2f, x) * (1f - smooth(eye.x1 - 1.2f, eye.x1, x))
    }

    // ------------------------------------------------------------------ campos de movimiento

    /** Movimientos de todo el cuerpo y de la cabeza entera, por unidad de cada parámetro. */
    private fun globalAt(x: Float, y: Float, out: FloatArray) {
        out.fill(0f)
        val ax = abs(x - FaceMap.CENTER_X)
        val top = smooth(150f, 285f, y)                       // por encima del pelo el fondo queda quieto

        // Respiración: suben pecho y hombros (la cabeza los acompaña un poco menos) y el torso se ensancha apenas.
        val latBreath = 1f - smooth(230f, 330f, ax)
        val rise = when {
            y >= 1000f -> 0f
            y > 640f -> 1f - smooth(640f, 1000f, y)
            y > 560f -> 1f
            else -> 0.72f + 0.28f * smooth(500f, 560f, y)
        }
        out[G_BREATH * 2 + 1] = -2.4f * rise * latBreath * top
        val chest = (y - 680f) / 120f
        out[G_BREATH * 2] = (x - FaceMap.CENTER_X) * 0.0045f * exp(-chest * chest) * latBreath

        // Balanceo: el cuerpo se inclina desde las rodillas; los pies no se mueven.
        val latSway = 1f - smooth(240f, 360f, ax)
        out[G_SWAY * 2] = 5f * (1f - smooth(560f, 1500f, y)) * latSway * top

        // Cabeza: pesa 1 en la cabeza y se apaga hacia los hombros y hacia el fondo.
        val hx = (x - FaceMap.FACE_X) / 112f
        val hy = (y - FaceMap.FACE_Y) / (if (y < FaceMap.FACE_Y) 128f else 105f)
        val head = 1f - smooth(1f, 1.5f, sqrt(hx * hx + hy * hy))
        // "dome": 1 en la nariz y 0 en el contorno de la cara; da la sensación de volumen al girar.
        val fx = (x - FaceMap.FACE_X) / 58f
        val fy = (y - 438f) / 78f
        val r2 = fx * fx + fy * fy
        val dome = if (r2 < 1f) (1f - r2) * (1f - r2) else 0f

        out[G_ROLL * 2] = -(y - FaceMap.NECK_Y) * ROLL_RAD * head
        out[G_ROLL * 2 + 1] = (x - FaceMap.FACE_X) * ROLL_RAD * head
        out[G_HEAD_X * 2] = head
        out[G_HEAD_Y * 2 + 1] = head
        out[G_YAW * 2] = 1.5f * head + 4.5f * dome
        out[G_PITCH * 2 + 1] = 1f * head + 3.5f * dome
    }

    /** Movimientos de la cara, por unidad de cada parámetro. */
    private fun localAt(x: Float, y: Float, out: FloatArray) {
        out.fill(0f)
        val seam = seamY(x)

        // Mandíbula: baja el labio inferior y el mentón; se ensancha hacia la quijada y se apaga en el cuello.
        // (Cada capa se recorta justo en la unión de los labios; el movimiento se prolonga unos px
        // del otro lado del corte para que el borde no se estire, y después se apaga.)
        val above = seam - y
        val t = max(0f, -above) / FaceMap.SEAM_TO_CHIN
        val half = FaceMap.MOUTH_HALF + (44f - FaceMap.MOUTH_HALF) * smooth(0f, 0.55f, t)
        out[L_JAW * 2 + 1] = JAW_PX * bell((x - FaceMap.MOUTH_X) / half) * (1f - smooth(0.95f, 1.9f, t)) * (1f - smooth(OVERLAP, OVERLAP_END, above))

        // Labio de arriba: sube un poco, apagándose hacia la nariz.
        val lipFade = if (above <= 0f) 1f - smooth(OVERLAP, OVERLAP_END, -above) else 1f - smooth(0f, 11f, above)
        out[L_LIP_UP * 2 + 1] = -LIP_UP_PX * bell((x - FaceMap.MOUTH_X) / 22f) * lipFade

        // Comisuras: hacia afuera para "i"/"e", hacia adentro para "o"/"u". Se mueven poco: el
        // micrófono les pasa justo por delante y no acompaña, así que un movimiento grande se notaría falso.
        val lx = FaceMap.SEAM[0]
        val ly = FaceMap.SEAM[1]
        val rx = FaceMap.SEAM[FaceMap.SEAM.size - 2]
        val ry = FaceMap.SEAM[FaceMap.SEAM.size - 1]
        out[L_WIDE * 2] = 0.9f * (gauss(x - rx, y - ry, 6f, 6f) - gauss(x - lx, y - ly, 6f, 6f))

        // Sonrisa: suben las mejillas y los párpados de abajo; las comisuras, apenas.
        val sl = gauss(x - lx, y - ly, 8f, 7f)
        val sr = gauss(x - rx, y - ry, 8f, 7f)
        val cheeks = gauss(x - 396f, y - 458f, 12f, 11f) + gauss(x - 461f, y - 458f, 12f, 11f)
        val lowerLids = gauss(x - FaceMap.EYE_L.centerX, y - 424.5f, 11f, 3.2f) + gauss(x - FaceMap.EYE_R.centerX, y - 424.5f, 11f, 3.2f)
        out[L_SMILE * 2] = 0.4f * (sr - sl)
        out[L_SMILE * 2 + 1] = -0.7f * (sl + sr) - 1.5f * cheeks - 1.1f * lowerLids

        // Cejas.
        val d = y - FaceMap.BROW_Y
        val h = if (d < 0f) 9.5f else 10f
        val browY = if (abs(d) < h) 0.5f * (1f + cos(PI.toFloat() * d / h)) else 0f
        out[L_BROW_L * 2 + 1] = -2.6f * browY * smooth(373f, 381f, x) * (1f - smooth(409f, 419f, x))
        out[L_BROW_R * 2 + 1] = -2.6f * browY * smooth(437f, 447f, x) * (1f - smooth(473f, 481f, x))

        // Mirada: corre el iris dentro del ojo.
        val gaze = eyeBump(FaceMap.EYE_L, x, y) + eyeBump(FaceMap.EYE_R, x, y)
        out[L_GAZE_X * 2] = 1.7f * gaze
        out[L_GAZE_Y * 2 + 1] = 1.1f * gaze

        // Párpados.
        out[L_BLINK_L * 2 + 1] = lidDrop(FaceMap.EYE_L, x, y)
        out[L_BLINK_R * 2 + 1] = lidDrop(FaceMap.EYE_R, x, y)
    }

    private fun eyeBump(eye: FaceMap.Eye, x: Float, y: Float): Float {
        val dx = (x - eye.centerX) / 12.5f
        val dy = (y - eye.centerY) / 5.2f
        val r2 = dx * dx + dy * dy
        return if (r2 < 1f) (1f - r2) * (1f - r2) else 0f
    }

    /** Cuánto baja el párpado en (x, y) con el ojo cerrado del todo. */
    private fun lidDrop(eye: FaceMap.Eye, x: Float, y: Float): Float {
        if (x <= eye.x0 || x >= eye.x1) return 0f
        val cut = line(eye.upper, x)
        val travel = max(0f, line(eye.lower, x) - cut)
        // Las pestañas y el delineado bajan enteros; lo que se estira es la piel entre ellos y la ceja.
        val rigid = cut - FaceMap.LID_RIGID
        val follow = if (y >= rigid) 1f - smooth(OVERLAP, OVERLAP_END, y - cut - travel) else smooth(FaceMap.LID_TOP, rigid, y)
        return travel * follow
    }

    companion object {
        const val LENS_N = 17

        const val G_BREATH = 0
        const val G_SWAY = 1
        const val G_ROLL = 2
        const val G_HEAD_X = 3
        const val G_HEAD_Y = 4
        const val G_YAW = 5
        const val G_PITCH = 6
        private const val GLOBALS = 7

        private const val L_JAW = 0
        private const val L_LIP_UP = 1
        private const val L_WIDE = 2
        private const val L_SMILE = 3
        private const val L_BROW_L = 4
        private const val L_BROW_R = 5
        private const val L_GAZE_X = 6
        private const val L_GAZE_Y = 7
        private const val L_BLINK_L = 8
        private const val L_BLINK_R = 9
        private const val LOCALS = 10

        /** Cuántos px baja el mentón con la boca bien abierta. */
        const val JAW_PX = 8.5f
        const val LIP_UP_PX = 1.7f
        const val ROLL_RAD = 0.06f
        private const val EDGE = 0.6f     // ancho del borde suave de las capas, en px de la foto

        // Cuánto se prolonga el movimiento de una capa más allá de su línea de corte antes de apagarse.
        private const val OVERLAP = 4f
        private const val OVERLAP_END = 12f

        /** Altura de la unión de los labios en la columna x (se prolonga recta más allá de las comisuras). */
        fun seamY(x: Float): Float = line(FaceMap.SEAM, x)

        /** Altura en la columna x de una línea dada como puntos (x, y) con x creciente. */
        fun line(points: FloatArray, x: Float): Float {
            if (x <= points[0]) return points[1]
            var i = 0
            while (i + 3 < points.size) {
                if (x <= points[i + 2]) {
                    val f = (x - points[i]) / (points[i + 2] - points[i])
                    return points[i + 1] + (points[i + 3] - points[i + 1]) * f
                }
                i += 2
            }
            return points[points.size - 1]
        }

        /** 0 hasta [a], 1 desde [b], con transición suave. */
        fun smooth(a: Float, b: Float, x: Float): Float {
            if (a == b) return if (x < a) 0f else 1f
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Campana: 1 en el centro, 0 desde |u| = 1. */
        private fun bell(u: Float): Float {
            if (u <= -1f || u >= 1f) return 0f
            val k = 1f - u * u
            return k * k
        }

        private fun gauss(dx: Float, dy: Float, sx: Float, sy: Float): Float =
            exp(-0.5f * ((dx / sx) * (dx / sx) + (dy / sy) * (dy / sy)))
    }
}
