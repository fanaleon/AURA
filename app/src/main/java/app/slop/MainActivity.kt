package app.slop

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Matrix
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

/**
 * Pantalla de la asistente: la modelo de fondo, el chat abajo y el micrófono.
 * Se abre desde el ícono o tocando el widget (en ese caso saluda y escucha).
 */
class MainActivity : Activity(), Assistant.Ui {

    private lateinit var assistant: Assistant
    private lateinit var strings: Strings
    private lateinit var settings: SettingsSheet

    private lateinit var stage: View
    private lateinit var avatar: ImageView
    private lateinit var glow: GlowView
    private lateinit var content: View
    private lateinit var topBar: View
    private lateinit var nameView: TextView
    private lateinit var chipWeb: TextView
    private lateinit var chipHands: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var tapArea: View
    private lateinit var status: TextView
    private lateinit var chatScroll: MaxHeightScrollView
    private lateinit var chat: LinearLayout
    private lateinit var errorView: TextView
    private lateinit var input: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var mic: MicButton

    private var breathing: ObjectAnimator? = null
    private var pendingMicAction: (() -> Unit)? = null
    private var partial = ""
    private var started = false
    private var wantGreet = false

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC

        bindViews()
        setupInsets()
        assistant = Assistant(this, this)
        settings = SettingsSheet(this) { onSettingsSaved() }
        applyTexts()
        for (msg in Conversation.messages) addBubble(msg, animate = false)
        wireActions()
        startBreathing()

        if (savedInstanceState == null) {
            wantGreet = isTalkRequest(intent)
            if (!wantGreet) {
                playWakeUp()
                if (Prefs.apiKey(this).isBlank()) content.post { settings.show() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isTalkRequest(intent)) {
            wantGreet = true
            if (started) greetFromWidget()
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        assistant.setForeground(true)
        breathing?.resume()
        if (wantGreet) greetFromWidget()
    }

    override fun onStop() {
        started = false
        assistant.setForeground(false)
        breathing?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        settings.dismiss()
        breathing?.cancel()
        assistant.release()
        super.onDestroy()
    }

    /** true si nos abrió el widget (y no es una reapertura desde "recientes"). */
    private fun isTalkRequest(intent: Intent?): Boolean {
        if (intent == null || !intent.getBooleanExtra(EXTRA_TALK, false)) return false
        intent.removeExtra(EXTRA_TALK)
        return intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
    }

    private fun greetFromWidget() {
        wantGreet = false
        playWakeUp()
        if (Prefs.apiKey(this).isBlank()) {
            settings.show()
            return
        }
        withMic { assistant.greetAndListen() }
    }

    // ---------------------------------------------------------------- armado de la pantalla

    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun bindViews() {
        stage = findViewById(R.id.stage)
        avatar = findViewById(R.id.avatar)
        glow = findViewById(R.id.glow)
        content = findViewById(R.id.content)
        topBar = findViewById(R.id.top_bar)
        nameView = findViewById(R.id.name)
        chipWeb = findViewById(R.id.chip_web)
        chipHands = findViewById(R.id.chip_hands)
        btnSettings = findViewById(R.id.btn_settings)
        tapArea = findViewById(R.id.tap_area)
        status = findViewById(R.id.status)
        chatScroll = findViewById(R.id.chat_scroll)
        chat = findViewById(R.id.chat)
        errorView = findViewById(R.id.error)
        input = findViewById(R.id.input)
        btnSend = findViewById(R.id.btn_send)
        mic = findViewById(R.id.mic)

        val globe = getDrawable(R.drawable.ic_globe)!!.mutate()
        globe.setBounds(0, 0, dp(16), dp(16))
        chipWeb.setCompoundDrawablesRelative(globe, null, null, null)

        avatar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitAvatar() }
        topBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitAvatar() }
    }

    /**
     * Escala la foto para cubrir la pantalla y la ubica de modo que la cabeza de la modelo
     * empiece justo debajo de la barra superior (así los botones nunca le tapan la cara).
     */
    private fun fitAvatar() {
        val d = avatar.drawable ?: return
        val vw = avatar.width.toFloat()
        val vh = avatar.height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw <= 0f || vh <= 0f || dw <= 0f || dh <= 0f) return

        val target = topBar.bottom + dp(8).toFloat()   // dónde debe quedar el borde superior del pelo
        val hairY = dh * PHOTO_HAIR_TOP
        var scale = max(vw / dw, (vh - target) / (dh - hairY))
        var dy = target - hairY * scale
        if (dy > 0f) {                                  // la foto no tiene tanto aire arriba: se alinea al tope
            dy = 0f
            scale = max(scale, vh / dh)
        }
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate((vw - dw * scale) / 2f, dy)
        avatar.imageMatrix = m
        // La respiración hace zoom alrededor de la cara.
        avatar.pivotX = vw / 2f
        avatar.pivotY = dy + dh * PHOTO_FACE * scale
    }

    /** Dibuja detrás de las barras del sistema y deja lugar para ellas y para el teclado. */
    @Suppress("DEPRECATION")
    private fun setupInsets() {
        content.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                top = i.top
                bottom = i.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(0, top, 0, bottom)
            // Con el teclado abierto queda menos lugar: el chat se achica para no tapar la entrada.
            val screen = resources.displayMetrics.heightPixels
            val keyboardOpen = bottom > screen / 4
            chatScroll.maxHeight = (screen * (if (keyboardOpen) 0.15f else 0.34f)).toInt()
            insets
        }
        content.requestApplyInsets()
    }

    private fun applyTexts() {
        strings = Texts.of(Prefs.lang(this))
        nameView.text = Prefs.name(this)
        input.hint = strings.inputHint
        chipWeb.text = strings.web
        chipHands.text = strings.handsFree
        btnSettings.contentDescription = strings.settings
        btnSend.contentDescription = strings.send
        mic.contentDescription = strings.talk
        tapArea.contentDescription = strings.talk
        renderChips()
        renderStatus()
    }

    private fun renderChips() {
        // Se ve encendido solo si de verdad puede buscar (con Gemini hace falta la key de Tavily).
        chipWeb.isSelected = Prefs.webActive(this)
        chipHands.isSelected = Prefs.handsFree(this)
    }

    private fun wireActions() {
        mic.setOnClickListener { onTalkTapped(fromModel = false) }
        tapArea.setOnClickListener { onTalkTapped(fromModel = true) }
        btnSettings.setOnClickListener { settings.show() }
        btnSend.setOnClickListener { sendTyped() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped()
                true
            } else {
                false
            }
        }
        chipWeb.setOnClickListener {
            val on = !Prefs.webActive(this)
            if (on && Prefs.needsSearchKey(this)) {
                // Falta la key de Tavily: se avisa y se abren los ajustes para cargarla.
                Prefs.setWeb(this, true)
                toast(strings.searchKeyNeeded)
                settings.show()
                return@setOnClickListener
            }
            Prefs.setWeb(this, on)
            renderChips()
            toast(if (on) strings.webOn else strings.webOff)
        }
        chipHands.setOnClickListener {
            val on = !Prefs.handsFree(this)
            Prefs.setHandsFree(this, on)
            renderChips()
            toast(if (on) strings.handsFreeOn else strings.handsFreeOff)
        }
    }

    private fun onSettingsSaved() {
        assistant.applySettings()
        applyTexts()
        onError(null)
        AvatarWidget.refreshAll(this)
    }

    // ---------------------------------------------------------------- acciones

    /** Toque en el micrófono o en la modelo: empieza a escuchar, o corta si ya escuchaba. */
    private fun onTalkTapped(fromModel: Boolean) {
        if (fromModel) bump()
        when (assistant.phase) {
            Phase.LISTENING -> assistant.stopListening()
            Phase.THINKING -> Unit
            Phase.IDLE, Phase.SPEAKING -> {
                if (Prefs.apiKey(this).isBlank()) {
                    settings.show()
                    return
                }
                withMic { assistant.startListening() }
            }
        }
    }

    private fun sendTyped() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        hideKeyboard()
        if (Prefs.apiKey(this).isBlank()) {
            settings.show()
            return
        }
        assistant.send(text)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    /** Ejecuta [action] si hay permiso de micrófono; si no, lo pide y la ejecuta al concederlo. */
    private fun withMic(action: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMicAction = action
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        val action = pendingMicAction
        pendingMicAction = null
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            action?.invoke()
        } else {
            onError(strings.micPermission)
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // no hay navegador instalado
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- avisos de la asistente

    override fun onPhase(phase: Phase) {
        mic.phase = phase
        if (phase != Phase.LISTENING) {
            partial = ""
            mic.level = 0f
        }
        glow.setBase(if (phase == Phase.LISTENING) 0.28f else 0f, 260L)
        if (phase == Phase.IDLE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        renderStatus()
    }

    override fun onPartial(text: String) {
        partial = text
        renderStatus()
    }

    override fun onLevel(level: Float) {
        mic.level = level
        if (assistant.phase == Phase.LISTENING) glow.setBase(0.28f + 0.5f * level)
    }

    override fun onMessage(msg: ChatMsg) {
        addBubble(msg, animate = true)
    }

    override fun onError(text: String?) {
        errorView.text = text.orEmpty()
        errorView.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun onWord() {
        mic.pulse()
        glow.flash(0.22f, 260L)
    }

    private fun renderStatus() {
        val phase = assistant.phase
        val text = when (phase) {
            Phase.LISTENING -> if (partial.isBlank()) strings.listening else tail(partial)
            Phase.THINKING -> strings.thinking
            Phase.SPEAKING -> strings.speaking
            Phase.IDLE -> if (Conversation.messages.isEmpty()) strings.idleHint else ""
        }
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        status.setTextColor(getColor(if (phase == Phase.IDLE) R.color.text else R.color.orange))
    }

    /** Muestra el final de un dictado largo (lo último que dijiste). */
    private fun tail(text: String): String = if (text.length <= 110) text else "…" + text.takeLast(110)

    // ---------------------------------------------------------------- chat

    private fun addBubble(msg: ChatMsg, animate: Boolean) {
        val maxBubble = (resources.displayMetrics.widthPixels * 0.80f).toInt()
        val bubble = TextView(this).apply {
            text = msg.text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTextColor(getColor(if (msg.fromUser) R.color.on_orange else R.color.text))
            setBackgroundResource(if (msg.fromUser) R.drawable.bubble_me else R.drawable.bubble_her)
            setPadding(dp(14), dp(9), dp(14), dp(10))
            setLineSpacing(0f, 1.12f)
            maxWidth = maxBubble
        }
        chat.addView(bubble, rowParams(if (msg.fromUser) Gravity.END else Gravity.START, 8))

        if (!msg.fromUser) {
            if (msg.searched) addCaption(strings.searched, R.color.orange, withGlobe = true)
            for (source in msg.sources) addLink(source, maxBubble)
            if (msg.note != null) addCaption(msg.note, R.color.text_dim, withGlobe = false)
        }

        if (animate) {
            bubble.alpha = 0f
            bubble.translationY = dp(12).toFloat()
            bubble.animate().alpha(1f).translationY(0f).setDuration(240L).start()
        }
        renderStatus()
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addCaption(text: String, colorRes: Int, withGlobe: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColor(colorRes))
            setPadding(dp(6), 0, dp(6), 0)
            if (withGlobe) {
                val globe = getDrawable(R.drawable.ic_globe)!!.mutate()
                globe.setTint(getColor(colorRes))
                globe.setBounds(0, 0, dp(13), dp(13))
                setCompoundDrawablesRelative(globe, null, null, null)
                compoundDrawablePadding = dp(5)
            }
        }
        chat.addView(tv, rowParams(Gravity.START, 5))
    }

    private fun addLink(source: Source, maxWidthPx: Int) {
        val host = Net.hostOf(source.url)
        val label = if (source.title.isBlank() || source.title == host) host else host + " · " + source.title
        val tv = TextView(this).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(getColor(R.color.text))
            setBackgroundResource(R.drawable.link_bg)
            setPadding(dp(10), dp(6), dp(12), dp(6))
            maxWidth = maxWidthPx
            val arrow = getDrawable(R.drawable.ic_link)!!.mutate()
            arrow.setTintList(ColorStateList.valueOf(getColor(R.color.orange)))
            arrow.setBounds(0, 0, dp(14), dp(14))
            setCompoundDrawablesRelative(arrow, null, null, null)
            compoundDrawablePadding = dp(6)
            setOnClickListener { openLink(source.url) }
        }
        chat.addView(tv, rowParams(Gravity.START, 5))
    }

    private fun rowParams(gravity: Int, topMarginDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = gravity
        lp.topMargin = dp(topMarginDp)
        return lp
    }

    // ---------------------------------------------------------------- animaciones

    /** Respiración suave y continua de la modelo. */
    private fun startBreathing() {
        breathing = ObjectAnimator.ofPropertyValuesHolder(
            avatar,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.024f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.024f)
        ).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** "Despertar": la modelo entra desde un zoom, sube el panel y destella el borde. */
    private fun playWakeUp() {
        stage.animate().cancel()
        stage.alpha = 0f
        stage.scaleX = 1.12f
        stage.scaleY = 1.12f
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(0L).setDuration(650L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction(null)
            .start()

        content.animate().cancel()
        content.alpha = 0f
        content.translationY = dp(36).toFloat()
        content.animate().alpha(1f).translationY(0f)
            .setStartDelay(140L).setDuration(480L)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        glow.flash(1f, 1100L)
    }

    /** Reacción al tocarla: un pequeño salto y un destello. */
    private fun bump() {
        stage.animate().cancel()
        stage.alpha = 1f
        stage.animate().scaleX(1.035f).scaleY(1.035f)
            .setStartDelay(0L).setDuration(110L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                stage.animate().scaleX(1f).scaleY(1f)
                    .setStartDelay(0L).setDuration(340L)
                    .setInterpolator(OvershootInterpolator(2.5f))
                    .withEndAction(null)
                    .start()
            }
            .start()
        glow.flash(0.7f, 600L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val ACTION_TALK = "app.slop.action.TALK"
        const val EXTRA_TALK = "talk"
        private const val REQUEST_MIC = 41

        // Posiciones dentro de avatar_full.jpg, como fracción de su alto (850 x 1915 px):
        private const val PHOTO_HAIR_TOP = 0.159f   // borde superior del pelo (y = 305)
        private const val PHOTO_FACE = 0.219f       // centro de la cara (y = 420)
    }
}
