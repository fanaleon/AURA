package app.slop

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView

/** Panel de ajustes que sube desde abajo. */
class SettingsSheet(private val activity: Activity, private val onSaved: () -> Unit) {

    private var dialog: Dialog? = null

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show() {
        if (isShowing || activity.isFinishing) return
        val d = Dialog(activity, R.style.Theme_Slop_Sheet)
        d.setContentView(R.layout.dialog_settings)
        d.setCanceledOnTouchOutside(true)

        val title = d.findViewById<TextView>(R.id.set_title)
        val langLabel = d.findViewById<TextView>(R.id.set_lang_label)
        val langEs = d.findViewById<TextView>(R.id.set_lang_es)
        val langEn = d.findViewById<TextView>(R.id.set_lang_en)
        val providerLabel = d.findViewById<TextView>(R.id.set_provider_label)
        val providerGemini = d.findViewById<TextView>(R.id.set_provider_gemini)
        val providerClaude = d.findViewById<TextView>(R.id.set_provider_claude)
        val providerHelp = d.findViewById<TextView>(R.id.set_provider_help)
        val keyLabel = d.findViewById<TextView>(R.id.set_key_label)
        val key = d.findViewById<EditText>(R.id.set_key)
        val keyHelp = d.findViewById<TextView>(R.id.set_key_help)
        val searchGroup = d.findViewById<View>(R.id.set_search_group)
        val searchLabel = d.findViewById<TextView>(R.id.set_search_label)
        val searchKey = d.findViewById<EditText>(R.id.set_search_key)
        val searchHelp = d.findViewById<TextView>(R.id.set_search_help)
        val webLabel = d.findViewById<TextView>(R.id.set_web_label)
        val webHelp = d.findViewById<TextView>(R.id.set_web_help)
        val web = d.findViewById<Switch>(R.id.set_web)
        val modelLabel = d.findViewById<TextView>(R.id.set_model_label)
        val modelSmart = d.findViewById<TextView>(R.id.set_model_smart)
        val modelFast = d.findViewById<TextView>(R.id.set_model_fast)
        val modelHelp = d.findViewById<TextView>(R.id.set_model_help)
        val nameLabel = d.findViewById<TextView>(R.id.set_name_label)
        val name = d.findViewById<EditText>(R.id.set_name)
        val animLabel = d.findViewById<TextView>(R.id.set_anim_label)
        val animHelp = d.findViewById<TextView>(R.id.set_anim_help)
        val anim = d.findViewById<Switch>(R.id.set_anim)
        val save = d.findViewById<TextView>(R.id.set_save)

        var lang = Prefs.lang(activity)
        var fast = Prefs.fastModel(activity)
        var provider = Prefs.provider(activity)
        // Cada IA tiene su propia key: al cambiar de una a otra no se pierde lo escrito.
        val keys = HashMap<Provider, String>()
        for (p in Provider.entries) keys[p] = Prefs.keyFor(activity, p)

        name.setText(Prefs.name(activity))
        key.setText(keys[provider])
        searchKey.setText(Prefs.searchKey(activity))
        searchKey.hint = "tvly-…"
        web.isChecked = Prefs.web(activity)
        anim.isChecked = Prefs.widgetAnimated(activity)
        langEs.text = Lang.ES.label
        langEn.text = Lang.EN.label

        // Los textos del panel cambian al instante cuando elegís otro idioma u otra IA.
        fun render() {
            val s = Texts.of(lang)
            val gemini = provider == Provider.GEMINI
            title.text = s.settings
            langLabel.text = s.languageLabel
            providerLabel.text = s.providerLabel
            providerGemini.text = s.providerFree
            providerClaude.text = s.providerPaid
            providerHelp.text = if (gemini) s.providerHelpGemini else s.providerHelpClaude
            keyLabel.text = s.keyLabel.forAi(provider)
            keyHelp.text = if (gemini) s.keyHelpGemini else s.keyHelpClaude
            key.hint = provider.keyHint
            searchGroup.visibility = if (gemini) View.VISIBLE else View.GONE
            searchLabel.text = s.searchKeyLabel
            searchHelp.text = s.searchKeyHelp
            webLabel.text = s.webLabel
            webHelp.text = if (gemini) s.webHelpGemini else s.webHelpClaude
            modelLabel.text = s.modelLabel
            modelSmart.text = s.modelSmart
            modelFast.text = s.modelFast
            modelHelp.text = if (gemini) s.modelHelpGemini else s.modelHelpClaude
            nameLabel.text = s.nameLabel
            animLabel.text = s.animLabel
            animHelp.text = s.animHelp
            save.text = s.save
            langEs.isSelected = lang == Lang.ES
            langEn.isSelected = lang == Lang.EN
            providerGemini.isSelected = gemini
            providerClaude.isSelected = !gemini
            modelSmart.isSelected = !fast
            modelFast.isSelected = fast
        }
        render()

        fun choose(next: Provider) {
            if (next == provider) return
            keys[provider] = key.text.toString()
            provider = next
            key.setText(keys[next])
            render()
        }

        langEs.setOnClickListener {
            lang = Lang.ES
            render()
        }
        langEn.setOnClickListener {
            lang = Lang.EN
            render()
        }
        providerGemini.setOnClickListener { choose(Provider.GEMINI) }
        providerClaude.setOnClickListener { choose(Provider.CLAUDE) }
        modelSmart.setOnClickListener {
            fast = false
            render()
        }
        modelFast.setOnClickListener {
            fast = true
            render()
        }
        save.setOnClickListener {
            keys[provider] = key.text.toString()
            Prefs.save(
                ctx = activity,
                provider = provider,
                geminiKey = keys[Provider.GEMINI].orEmpty(),
                claudeKey = keys[Provider.CLAUDE].orEmpty(),
                searchKey = searchKey.text.toString(),
                name = name.text.toString(),
                lang = lang,
                web = web.isChecked,
                fastModel = fast,
                widgetAnimated = anim.isChecked
            )
            d.dismiss()
            onSaved()
        }

        d.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            )
        }
        d.setOnDismissListener { dialog = null }
        dialog = d
        d.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
