package app.slop

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La "actuación" de la asistente: decide en cada cuadro la [Pose] según lo que esté haciendo.
 *
 * - Siempre: respira, se balancea apenas, parpadea y mueve los ojos.
 * - Escuchando: inclina la cabeza y levanta las cejas, atenta.
 * - Pensando: mira hacia arriba y al costado.
 * - Hablando: la boca sigue a la voz ([lips]) y la cabeza y las cejas marcan el ritmo.
 *
 * No usa nada de Android, así se puede probar en una PC.
 */
class Performer(seed: Long = System.nanoTime()) {
    val pose = Pose()
    val lips = LipSync()

    /** Encuadre: 0 = cuerpo entero, 1 = primer plano. La vista lo usa para mover la "cámara". */
    var zoom = 0f
        private set

    /** Volumen del micrófono mientras escucha, 0..1. */
    var micLevel = 0f

    var phase = Phase.IDLE
        set(value) {
            if (field == value) return
            field = value
            side = if (random.nextBoolean()) 1f else -1f
            talkYaw = 0f
            if (value != Phase.IDLE) nextNodAt = clock + 1.2f + random.nextFloat() * 1.5f
            // Al cambiar de estado suele haber un parpadeo.
            if (blinkAt > clock + 0.25f) blinkAt = clock + 0.05f + random.nextFloat() * 0.2f
        }

    private val random = Random(seed)
    private val shape = LipSync.Shape()
    private var lastMs = -1L
    private var clock = 0f              // segundos de animación transcurridos
    private var activeUntil = 0f        // hasta cuándo se queda en primer plano

    private var breathAngle = 0f
    private var mouthOpen = 0f
    private var mouthWide = 0f
    private var seenWords = 0
    private var wordsSinceBrow = 0
    private var wordsSinceTurn = 0
    private var side = 1f
    private var talkYaw = 0f
    private var level = 0f

    private val yaw = Spring()
    private val pitch = Spring()
    private val roll = Spring()
    private var browL = 0f
    private var browR = 0f
    private var smile = 0f
    private var browKick = 0f
    private var touchKick = 0f
    private var nextNodAt = 0f

    private var blinkAt = 1.5f
    private var blinkStart = -1f
    private var blinkAgain = false
    private var gazeX = 0f
    private var gazeY = 0f
    private var gazeTargetX = 0f
    private var gazeTargetY = 0f
    private var saccadeAt = 0.8f

    /** Reacción al toque: cejas arriba, sonrisa y un cabeceo. */
    fun touch() {
        touchKick = 1f
        pitch.velocity += 7f
        activeUntil = clock + STAY_CLOSE_S
        if (blinkStart < 0f) blinkAt = clock + 0.12f
    }

    /** Avanza la animación hasta [nowMs] (reloj que no retrocede) y deja el resultado en [pose]. */
    fun update(nowMs: Long) {
        val dt = if (lastMs < 0L) 0f else ((nowMs - lastMs) / 1000f).coerceIn(0f, 0.1f)
        lastMs = nowMs
        clock += dt

        val speaking = phase == Phase.SPEAKING
        val listening = phase == Phase.LISTENING
        val thinking = phase == Phase.THINKING

        // --- cuerpo
        breathAngle += dt * 2f * PI.toFloat() * (if (speaking) 0.34f else if (listening) 0.27f else 0.23f)
        pose.breath = 0.5f - 0.5f * cos(breathAngle)
        pose.sway = 0.55f * sin(clock * 2f * PI.toFloat() / 9.3f) + 0.25f * sin(clock * 2f * PI.toFloat() / 14.7f + 1.1f)

        // --- boca
        lips.sample(nowMs, shape)
        val openTarget = shape.open * SPEECH_OPEN
        mouthOpen = follow(mouthOpen, openTarget, dt, if (openTarget > mouthOpen) 0.04f else 0.055f)
        mouthWide = follow(mouthWide, shape.wide, dt, 0.07f)
        pose.jaw = mouthOpen
        pose.lipUp = (mouthOpen * 0.6f + max(0f, -mouthWide) * 0.25f).coerceIn(0f, 1f)
        pose.wide = mouthWide * (0.4f + 0.6f * min(1f, mouthOpen * 2f))

        // --- ritmo del habla: cada palabra da un pequeño cabeceo; cada tanto, cejas y un giro
        if (lips.words != seenWords) {
            seenWords = lips.words
            // Las palabras largas se acompañan con más cabeza que las cortas.
            val weight = (lips.lastWordUnits / 5f).coerceIn(0.25f, 1.3f)
            pitch.velocity += (2.2f + random.nextFloat() * 3.2f) * weight
            roll.velocity += (random.nextFloat() - 0.5f) * 1.6f * weight
            if (++wordsSinceBrow >= 3 + random.nextInt(4)) {
                wordsSinceBrow = 0
                browKick = 0.45f + random.nextFloat() * 0.35f
            }
            if (++wordsSinceTurn >= 5 + random.nextInt(5)) {
                wordsSinceTurn = 0
                talkYaw = (random.nextFloat() - 0.5f) * 0.7f
            }
        }

        // --- hacia dónde tiende la cabeza y la cara según el estado
        level = follow(level, if (listening) micLevel.coerceIn(0f, 1f) else 0f, dt, 0.12f)
        var yawTarget = 0f
        var pitchTarget = 0f
        var rollTarget = 0f
        var browLTarget = 0f
        var browRTarget = 0f
        var smileTarget = 0.08f
        when {
            listening -> {
                rollTarget = 0.5f * side
                pitchTarget = 0.2f
                browLTarget = 0.3f + 0.4f * level
                browRTarget = browLTarget
                smileTarget = 0.2f
            }
            thinking -> {
                yawTarget = 0.7f * side
                pitchTarget = -0.55f
                rollTarget = -0.25f * side
                browLTarget = if (side < 0f) 0.6f else 0.15f
                browRTarget = if (side < 0f) 0.15f else 0.6f
                smileTarget = 0f
            }
            speaking -> {
                yawTarget = talkYaw
                pitchTarget = 0.05f + 0.12f * mouthOpen
                rollTarget = 0.12f * side
                browLTarget = 0.12f
                browRTarget = 0.12f
                smileTarget = 0.14f
            }
        }
        // Cabeceo de "te sigo" mientras escucha o piensa.
        if ((listening || thinking) && clock >= nextNodAt) {
            nextNodAt = clock + 2.4f + random.nextFloat() * 2.6f
            if (listening) pitch.velocity += 6.5f
        }
        // Vida propia: la cabeza nunca queda clavada.
        yawTarget += 0.12f * sin(clock / 2.9f) + 0.07f * sin(clock / 1.7f + 2f)
        pitchTarget += 0.08f * sin(clock / 3.7f + 1f)
        rollTarget += 0.06f * sin(clock / 4.3f + 0.5f) - 0.15f * pose.sway

        yaw.step(yawTarget, dt, 30f, 0.9f)
        pitch.step(pitchTarget, dt, 42f, 0.75f)
        roll.step(rollTarget, dt, 30f, 0.85f)
        pose.yaw = yaw.value.coerceIn(-1.2f, 1.2f)
        pose.pitch = pitch.value.coerceIn(-1.2f, 1.2f)
        pose.roll = roll.value.coerceIn(-1.2f, 1.2f)
        pose.headX = 0f
        pose.headY = 0.9f * pose.pitch      // al asentir la cabeza también baja un poco

        browKick *= exp(-dt / 0.22f)
        touchKick *= exp(-dt / 0.45f)
        browL = follow(browL, browLTarget, dt, 0.16f)
        browR = follow(browR, browRTarget, dt, 0.16f)
        smile = follow(smile, smileTarget, dt, 0.25f)
        pose.browL = (browL + browKick + 0.8f * touchKick).coerceIn(-1f, 1.2f)
        pose.browR = (browR + browKick + 0.8f * touchKick).coerceIn(-1f, 1.2f)
        pose.smile = (smile + 0.6f * touchKick).coerceIn(0f, 1f)

        // --- ojos
        if (clock >= saccadeAt) {
            val glance = random.nextFloat() < 0.22f
            when {
                thinking -> {
                    gazeTargetX = 0.8f * side + (random.nextFloat() - 0.5f) * 0.3f
                    gazeTargetY = -0.85f + (random.nextFloat() - 0.5f) * 0.2f
                }
                glance -> {
                    gazeTargetX = (random.nextFloat() - 0.5f) * 1.4f
                    gazeTargetY = (random.nextFloat() - 0.6f) * 0.6f
                }
                else -> {
                    gazeTargetX = (random.nextFloat() - 0.5f) * 0.3f
                    gazeTargetY = (random.nextFloat() - 0.5f) * 0.2f
                }
            }
            saccadeAt = clock + if (glance && !thinking) 0.35f + random.nextFloat() * 0.5f else 0.8f + random.nextFloat() * 2.2f
        }
        gazeX = follow(gazeX, gazeTargetX, dt, 0.035f)
        gazeY = follow(gazeY, gazeTargetY, dt, 0.035f)
        // Los ojos compensan un poco el giro de la cabeza, para seguir mirando al frente.
        pose.gazeX = (gazeX - 0.3f * pose.yaw).coerceIn(-1f, 1f)
        pose.gazeY = (gazeY - 0.2f * pose.pitch).coerceIn(-1f, 1f)

        if (blinkStart < 0f && clock >= blinkAt) {
            blinkStart = clock
            blinkAgain = random.nextFloat() < 0.15f
        }
        var blink = 0f
        if (blinkStart >= 0f) {
            val t = clock - blinkStart
            blink = when {
                t < BLINK_CLOSE -> ease(t / BLINK_CLOSE)
                t < BLINK_CLOSE + BLINK_HOLD -> 1f
                t < BLINK_CLOSE + BLINK_HOLD + BLINK_OPEN -> 1f - ease((t - BLINK_CLOSE - BLINK_HOLD) / BLINK_OPEN)
                else -> 0f
            }
            if (t >= BLINK_CLOSE + BLINK_HOLD + BLINK_OPEN) {
                blinkStart = -1f
                blinkAt = if (blinkAgain) {
                    clock + 0.08f
                } else {
                    clock + when {
                        thinking -> 3f + random.nextFloat() * 4f
                        speaking -> 1.8f + random.nextFloat() * 3f
                        else -> 2.4f + random.nextFloat() * 3.6f
                    }
                }
                blinkAgain = false
            }
        }
        // Al sonreír los ojos se entrecierran apenas.
        val lids = max(blink, 0.1f * pose.smile)
        pose.blinkL = lids
        pose.blinkR = lids

        // --- encuadre: primer plano mientras conversa y un rato después; luego vuelve al cuerpo entero
        if (phase != Phase.IDLE) activeUntil = clock + STAY_CLOSE_S
        zoom = follow(zoom, if (clock < activeUntil) 1f else 0f, dt, 0.42f)
    }

    private fun follow(current: Float, target: Float, dt: Float, tau: Float): Float =
        current + (target - current) * (1f - exp(-dt / tau))

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Resorte amortiguado: sigue a un objetivo y acepta empujones (para los cabeceos). */
    private class Spring {
        var value = 0f
        var velocity = 0f

        fun step(target: Float, dt: Float, stiffness: Float, damping: Float) {
            // Pasos chicos para que no se desestabilice si un cuadro tarda.
            var left = dt
            while (left > 0f) {
                val h = min(left, 0.008f)
                val accel = stiffness * (target - value) - 2f * damping * sqrt(stiffness) * velocity
                velocity += accel * h
                value += velocity * h
                left -= h
            }
        }
    }

    private companion object {
        /** Qué tanto abre la boca al hablar: la "a" más abierta llega a este valor. */
        const val SPEECH_OPEN = 0.85f
        const val STAY_CLOSE_S = 6f
        const val BLINK_CLOSE = 0.07f
        const val BLINK_HOLD = 0.035f
        const val BLINK_OPEN = 0.14f
    }
}

/**
 * El encuadre: dónde y a qué tamaño se dibuja la foto en la pantalla.
 * En plano general la foto cubre la pantalla con el pelo justo debajo de la barra de arriba;
 * en primer plano la cabeza ocupa buena parte del ancho.
 */
object Framing {
    /** En primer plano, fracción del ancho de pantalla que ocupa la cabeza. */
    private const val CLOSE_HEAD_WIDTH = 0.6f

    /** En primer plano, el mentón no baja de esta fracción del alto (abajo va el chat). */
    private const val CLOSE_CHIN_AT = 0.44f

    /**
     * Calcula la "cámara" para una pantalla de [vw] x [vh] px. [topInset] es dónde debe quedar el
     * borde de arriba del pelo y [zoom] va de 0 (cuerpo entero) a 1 (primer plano).
     * Deja en [out] la escala y el corrimiento: pantalla = foto * out[0] + (out[1], out[2]).
     */
    fun compute(vw: Float, vh: Float, topInset: Float, density: Float, zoom: Float, out: FloatArray) {
        val iw = FaceMap.IMG_W.toFloat()
        val ih = FaceMap.IMG_H.toFloat()

        var wide = max(vw / iw, (vh - topInset) / (ih - FaceMap.HAIR_TOP))
        var wideY = topInset - FaceMap.HAIR_TOP * wide
        if (wideY > 0f) {                                   // la foto no tiene tanto aire arriba: se alinea al tope
            wideY = 0f
            wide = max(wide, vh / ih)
        }
        val wideX = (vw - iw * wide) / 2f

        var close = min(
            CLOSE_HEAD_WIDTH * vw / FaceMap.HEAD_WIDTH,
            (CLOSE_CHIN_AT * vh - topInset) / (FaceMap.CHIN_Y - FaceMap.HAIR_TOP)
        )
        close = max(close, wide * 1.25f)
        val closeX = vw / 2f - FaceMap.FACE_X * close
        val closeY = min(0f, topInset + 4f * density - FaceMap.HAIR_TOP * close)

        val z = zoom.coerceIn(0f, 1f)
        val t = z * z * (3f - 2f * z)
        val scale = wide * exp(t * kotlin.math.ln(close / wide))
        // La cara viaja en línea recta entre sus dos posiciones en pantalla.
        val faceX = wideX + FaceMap.FACE_X * wide + (closeX + FaceMap.FACE_X * close - wideX - FaceMap.FACE_X * wide) * t
        val faceY = wideY + FaceMap.FACE_Y * wide + (closeY + FaceMap.FACE_Y * close - wideY - FaceMap.FACE_Y * wide) * t
        out[0] = scale
        // Por las dudas, la foto nunca deja un borde de pantalla sin cubrir.
        out[1] = (faceX - FaceMap.FACE_X * scale).coerceIn(min(0f, vw - iw * scale), 0f)
        out[2] = (faceY - FaceMap.FACE_Y * scale).coerceIn(min(0f, vh - ih * scale), 0f)
    }
}
