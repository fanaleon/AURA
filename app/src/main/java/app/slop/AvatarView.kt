package app.slop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * La modelo, animada. Dibuja la foto deformada cuadro a cuadro: [Performer] decide la pose
 * (boca, ojos, cabeza, respiración) y [FaceRig] la convierte en mallas.
 *
 * También maneja el encuadre: cuerpo entero en reposo y primer plano mientras conversa.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    val performer = Performer()

    private val rig = FaceRig()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hole = Path()
    private val teeth = Path()
    private val camera = Matrix()
    private val frame = FloatArray(3)

    private var body: Bitmap? = null
    private var base: Bitmap? = null
    private var jaw: Bitmap? = null
    private var lids: Bitmap? = null
    private var mic: Bitmap? = null
    private var running = false

    /** Altura de la pantalla (en px) donde debe quedar el borde de arriba del pelo. */
    var topInset = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        body = BitmapFactory.decodeResource(resources, R.drawable.avatar_full, options)
        // Las capas de la cara se arman aparte para no demorar la apertura de la pantalla;
        // mientras tanto se ve la foto, que ya respira y se balancea.
        Thread({ loadHead(options) }, "slop-avatar").start()
    }

    private fun loadHead(options: BitmapFactory.Options) {
        try {
            val head = BitmapFactory.decodeResource(resources, R.drawable.head, options) ?: return
            val micSrc = BitmapFactory.decodeResource(resources, R.drawable.head_mic, options) ?: return
            val w = head.width
            val h = head.height
            if (micSrc.width != w || micSrc.height != h) return
            val headPx = IntArray(w * h)
            val micPx = IntArray(w * h)
            head.getPixels(headPx, 0, w, 0, 0, w, h)
            micSrc.getPixels(micPx, 0, w, 0, 0, w, h)
            head.recycle()
            micSrc.recycle()
            val layers = FaceRig().buildLayers(headPx, micPx, w, h)
            val newBase = Bitmap.createBitmap(layers.base, w, h, Bitmap.Config.ARGB_8888)
            val newJaw = Bitmap.createBitmap(layers.jaw, w, h, Bitmap.Config.ARGB_8888)
            val newLids = Bitmap.createBitmap(layers.lids, w, h, Bitmap.Config.ARGB_8888)
            val newMic = Bitmap.createBitmap(layers.mic, w, h, Bitmap.Config.ARGB_8888)
            // Se entrega por el hilo principal (sirve aunque la vista todavía no esté en pantalla).
            Handler(Looper.getMainLooper()).post {
                base = newBase
                jaw = newJaw
                lids = newLids
                mic = newMic
                invalidate()
            }
        } catch (e: RuntimeException) {
            // Si algo falla, queda la foto sin la cara animada.
        } catch (e: OutOfMemoryError) {
            // Ídem: mejor la foto quieta que cerrar la app.
        }
    }

    /** Arranca o frena la animación (se frena con la pantalla en segundo plano, para no gastar batería). */
    fun setRunning(on: Boolean) {
        if (running == on) return
        running = on
        if (on) postInvalidateOnAnimation()
    }

    fun setPhase(phase: Phase) {
        performer.phase = phase
    }

    fun setMicLevel(level: Float) {
        performer.micLevel = level
    }

    /** Reacción al toque. */
    fun touch() {
        performer.touch()
    }

    fun speechStart(text: String, english: Boolean, atMs: Long) = performer.lips.start(text, english, atMs)

    fun speechRange(start: Int, end: Int, atMs: Long) = performer.lips.range(start, end, atMs)

    fun speechEnd() = performer.lips.stop()

    override fun onDraw(canvas: Canvas) {
        val body = body ?: return
        if (width == 0 || height == 0) return
        performer.update(SystemClock.uptimeMillis())
        val pose = performer.pose
        rig.update(pose)
        setCamera(performer.zoom)

        canvas.save()
        canvas.concat(camera)
        canvas.drawBitmapMesh(body, rig.bodyCols, rig.bodyRows, rig.bodyVerts, 0, null, 0, paint)
        val base = base
        val jaw = jaw
        val lids = lids
        val mic = mic
        if (base != null && jaw != null && lids != null && mic != null) {
            // Mismo orden que explica FaceRig: cara, interior de la boca, mandíbula, párpados y micrófono.
            canvas.drawBitmapMesh(base, rig.headCols, rig.headRows, rig.headBase, 0, null, 0, paint)
            if (rig.lensGap >= MouthLook.MIN_GAP) {
                drawMouth(canvas)
                canvas.drawBitmapMesh(jaw, rig.headCols, rig.headRows, rig.headJaw, 0, null, 0, paint)
            }
            if (pose.blinkL > 0.01f || pose.blinkR > 0.01f) {
                canvas.drawBitmapMesh(lids, rig.headCols, rig.headRows, rig.headLids, 0, null, 0, paint)
            }
            canvas.drawBitmapMesh(mic, rig.headCols, rig.headRows, rig.headMic, 0, null, 0, paint)
        }
        canvas.restore()

        if (running) {
            // En reposo y de cuerpo entero el movimiento es lento: con la mitad de cuadros alcanza y gasta menos.
            val resting = performer.phase == Phase.IDLE && performer.zoom < 0.01f && !performer.lips.speaking
            if (resting) postInvalidateDelayed(IDLE_FRAME_MS) else postInvalidateOnAnimation()
        }
    }

    /** El hueco oscuro entre los labios y, si abre lo suficiente, el borde de los dientes de arriba. */
    private fun drawMouth(canvas: Canvas) {
        val top = rig.lensTop
        val bottom = rig.lensBottom
        val n = FaceRig.LENS_N
        hole.rewind()
        for (i in 0 until n) {
            val gap = bottom[i * 2 + 1] - top[i * 2 + 1]
            val y = top[i * 2 + 1] - MouthLook.overTop(gap)
            if (i == 0) hole.moveTo(top[0], y) else hole.lineTo(top[i * 2], y)
        }
        for (i in n - 1 downTo 0) {
            val gap = bottom[i * 2 + 1] - top[i * 2 + 1]
            hole.lineTo(bottom[i * 2], bottom[i * 2 + 1] + MouthLook.overBottom(gap))
        }
        hole.close()
        mouthPaint.color = MouthLook.DARK
        canvas.drawPath(hole, mouthPaint)

        if (rig.lensGap > MouthLook.TEETH_FROM) {
            teeth.rewind()
            for (i in 0 until n) {
                if (i == 0) teeth.moveTo(top[0], top[1]) else teeth.lineTo(top[i * 2], top[i * 2 + 1])
            }
            for (i in n - 1 downTo 0) {
                val gap = bottom[i * 2 + 1] - top[i * 2 + 1]
                teeth.lineTo(top[i * 2], top[i * 2 + 1] + MouthLook.teeth(i, n, gap))
            }
            teeth.close()
            mouthPaint.color = MouthLook.TEETH
            canvas.drawPath(teeth, mouthPaint)
        }
    }

    private fun setCamera(zoom: Float) {
        Framing.compute(width.toFloat(), height.toFloat(), topInset, resources.displayMetrics.density, zoom, frame)
        camera.setScale(frame[0], frame[0])
        camera.postTranslate(frame[1], frame[2])
    }

    private companion object {
        const val IDLE_FRAME_MS = 33L
    }
}
