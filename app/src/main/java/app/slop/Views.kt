package app.slop

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val ORANGE = 0xFFFF7A1A.toInt()
private const val ORANGE_DEEP = 0xFFFF4D2E.toInt()
private const val ORANGE_CLEAR = 0x00FF7A1A

/**
 * Resplandor naranja en los cuatro bordes de la pantalla.
 * "base" es el nivel sostenido (por ejemplo mientras escucha) y "flash" un destello que se apaga solo.
 */
class GlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thickness = 72f * resources.displayMetrics.density
    private var shaders: Array<Shader>? = null
    private var base = 0f
    private var flash = 0f
    private var baseAnim: ValueAnimator? = null
    private var flashAnim: ValueAnimator? = null

    /** Lleva el nivel sostenido a [target] (0..1) con una transición corta. */
    fun setBase(target: Float, durationMs: Long = 140L) {
        val to = target.coerceIn(0f, 1f)
        baseAnim?.cancel()
        if (durationMs <= 0L || !isAttachedToWindow) {
            base = to
            invalidate()
            return
        }
        baseAnim = ValueAnimator.ofFloat(base, to).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                base = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Destello que arranca en [peak] y se apaga en [durationMs]. */
    fun flash(peak: Float, durationMs: Long) {
        flashAnim?.cancel()
        flashAnim = ValueAnimator.ofFloat(peak.coerceIn(0f, 1f), 0f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                flash = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val t = thickness
        shaders = arrayOf(
            LinearGradient(0f, 0f, 0f, t, ORANGE, ORANGE_CLEAR, Shader.TileMode.CLAMP),
            LinearGradient(0f, h.toFloat(), 0f, h - t, ORANGE, ORANGE_CLEAR, Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, t, 0f, ORANGE, ORANGE_CLEAR, Shader.TileMode.CLAMP),
            LinearGradient(w.toFloat(), 0f, w - t, 0f, ORANGE, ORANGE_CLEAR, Shader.TileMode.CLAMP)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val s = shaders ?: return
        val strength = min(1f, base + flash)
        if (strength <= 0.004f) return
        val w = width.toFloat()
        val h = height.toFloat()
        val t = thickness
        val alpha = (strength * 235f).toInt()

        paint.shader = s[0]
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, w, t, paint)
        paint.shader = s[1]
        paint.alpha = alpha
        canvas.drawRect(0f, h - t, w, h, paint)
        paint.shader = s[2]
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, t, h, paint)
        paint.shader = s[3]
        paint.alpha = alpha
        canvas.drawRect(w - t, 0f, w, h, paint)
    }

    override fun onDetachedFromWindow() {
        baseAnim?.cancel()
        flashAnim?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Botón de micrófono dibujado a mano:
 * - en reposo: círculo naranja con un aro fino;
 * - escuchando: aros que se expanden y un halo que sigue el volumen de tu voz;
 * - pensando: un arco que gira;
 * - hablando: un aro que late con cada palabra.
 */
class MicButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE }
    private val arcBounds = RectF()
    private val micIcon: Drawable = context.getDrawable(R.drawable.ic_mic)!!.mutate()
    private val stopIcon: Drawable = context.getDrawable(R.drawable.ic_stop)!!.mutate()
    private val density = resources.displayMetrics.density

    private var t = 0f              // 0..1, se repite
    private var shownLevel = 0f
    private var wordPulse = 0f
    private var pulseAnim: ValueAnimator? = null

    private val loop = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    var phase: Phase = Phase.IDLE
        set(value) {
            if (field != value) {
                field = value
                syncLoop()
                invalidate()
            }
        }

    /** Volumen de la voz mientras escucha, 0..1. */
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    init {
        isClickable = true
        isFocusable = true
    }

    /** Latido corto; se llama con cada palabra que dice. */
    fun pulse() {
        pulseAnim?.cancel()
        pulseAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                wordPulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun syncLoop() {
        val shouldRun = phase != Phase.IDLE && isAttachedToWindow
        if (shouldRun && !loop.isStarted) loop.start()
        if (!shouldRun && loop.isStarted) loop.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncLoop()
    }

    override fun onDetachedFromWindow() {
        loop.cancel()
        pulseAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        val changed = pressed != isPressed
        super.setPressed(pressed)
        if (changed) {
            val to = if (pressed) 0.92f else 1f
            animate().scaleX(to).scaleY(to).setDuration(if (pressed) 80L else 180L).start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = radius()
        val cx = w / 2f
        val cy = h / 2f
        core.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, ORANGE, ORANGE_DEEP, Shader.TileMode.CLAMP)
    }

    private fun radius(): Float = min(width, height) * 0.34f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = radius()
        val maxR = min(width, height) / 2f - density

        ring.color = ORANGE
        when (phase) {
            Phase.IDLE -> {
                ring.strokeWidth = 1.5f * density
                ring.alpha = 110
                canvas.drawCircle(cx, cy, r + 4f * density, ring)
            }
            Phase.LISTENING -> {
                shownLevel += (level - shownLevel) * 0.3f
                halo.alpha = (70 + 90 * shownLevel).toInt()
                canvas.drawCircle(cx, cy, r + (maxR - r) * (0.25f + 0.75f * shownLevel), halo)
                ring.strokeWidth = 2f * density
                for (k in 0..1) {
                    val p = (t + k * 0.5f) % 1f
                    ring.alpha = (200 * (1f - p)).toInt()
                    canvas.drawCircle(cx, cy, r + (maxR - r) * p, ring)
                }
            }
            Phase.THINKING -> {
                val rr = r + 5f * density
                ring.strokeWidth = 3f * density
                ring.strokeCap = Paint.Cap.ROUND
                ring.alpha = 60
                canvas.drawCircle(cx, cy, rr, ring)
                ring.alpha = 255
                arcBounds.set(cx - rr, cy - rr, cx + rr, cy + rr)
                canvas.drawArc(arcBounds, t * 360f - 90f, 100f, false, ring)
                ring.strokeCap = Paint.Cap.BUTT
            }
            Phase.SPEAKING -> {
                val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat())
                val grow = (0.30f + 0.25f * breath + 0.45f * wordPulse).coerceIn(0f, 1f)
                halo.alpha = (55 + 80 * wordPulse).toInt()
                canvas.drawCircle(cx, cy, r + (maxR - r) * grow, halo)
                ring.strokeWidth = 2f * density
                ring.alpha = 190
                canvas.drawCircle(cx, cy, r + (maxR - r) * grow, ring)
            }
        }

        canvas.drawCircle(cx, cy, r, core)

        val icon = if (phase == Phase.LISTENING) stopIcon else micIcon
        val half = (r * 0.56f).toInt()
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.draw(canvas)
    }
}

/** ScrollView que no crece más allá de [maxHeight] píxeles. */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var maxHeight: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var spec = heightMeasureSpec
        if (maxHeight != Int.MAX_VALUE) {
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val limit = if (mode == MeasureSpec.UNSPECIFIED) maxHeight else min(size, maxHeight)
            // EXACTLY se respeta; en los otros casos se acota la altura.
            if (mode != MeasureSpec.EXACTLY) spec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, spec)
    }
}
