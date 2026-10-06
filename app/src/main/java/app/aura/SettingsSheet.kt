package app.aura

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
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
        val d = Dialog(activity, R.style.Theme_Aura_Sheet)
        d.setContentView(R.layout.dialog_settings)
        d.setCanceledOnTouchOutside(true)

        val title = d.findViewById<TextView>(R.id.set_title)
        val langLabel = d.findViewById<TextView>(R.id.set_lang_label)
        val langEs = d.findViewById<TextView>(R.id.set_lang_es)
        val langEn = d.findViewById<TextView>(R.id.set_lang_en)
        val webLabel = d.findViewById<TextView>(R.id.set_web_label)
        val webHelp = d.findViewById<TextView>(R.id.set_web_help)
        val web = d.findViewById<Switch>(R.id.set_web)
        val nameLabel = d.findViewById<TextView>(R.id.set_name_label)
        val name = d.findViewById<EditText>(R.id.set_name)
        val keyLabel = d.findViewById<TextView>(R.id.set_key_label)
        val key = d.findViewById<EditText>(R.id.set_key)
        val keyHelp = d.findViewById<TextView>(R.id.set_key_help)
        val modelLabel = d.findViewById<TextView>(R.id.set_model_label)
        val modelSmart = d.findViewById<TextView>(R.id.set_model_smart)
        val modelFast = d.findViewById<TextView>(R.id.set_model_fast)
        val modelHelp = d.findViewById<TextView>(R.id.set_model_help)
        val animLabel = d.findViewById<TextView>(R.id.set_anim_label)
        val animHelp = d.findViewById<TextView>(R.id.set_anim_help)
        val anim = d.findViewById<Switch>(R.id.set_anim)
        val save = d.findViewById<TextView>(R.id.set_save)

        var lang = Prefs.lang(activity)
        var fast = Prefs.fastModel(activity)

        name.setText(Prefs.name(activity))
        key.setText(Prefs.apiKey(activity))
        key.hint = "sk-ant-api…"
        web.isChecked = Prefs.web(activity)
        anim.isChecked = Prefs.widgetAnimated(activity)
        langEs.text = Lang.ES.label
        langEn.text = Lang.EN.label

        // Los textos del panel cambian al instante cuando elegís otro idioma.
        fun render() {
            val s = Texts.of(lang)
            title.text = s.settings
            langLabel.text = s.languageLabel
            webLabel.text = s.webLabel
            webHelp.text = s.webHelp
            nameLabel.text = s.nameLabel
            keyLabel.text = s.keyLabel
            keyHelp.text = s.keyHelp
            modelLabel.text = s.modelLabel
            modelSmart.text = s.modelSmart
            modelFast.text = s.modelFast
            modelHelp.text = s.modelHelp
            animLabel.text = s.animLabel
            animHelp.text = s.animHelp
            save.text = s.save
            langEs.isSelected = lang == Lang.ES
            langEn.isSelected = lang == Lang.EN
            modelSmart.isSelected = !fast
            modelFast.isSelected = fast
        }
        render()

        langEs.setOnClickListener {
            lang = Lang.ES
            render()
        }
        langEn.setOnClickListener {
            lang = Lang.EN
            render()
        }
        modelSmart.setOnClickListener {
            fast = false
            render()
        }
        modelFast.setOnClickListener {
            fast = true
            render()
        }
        save.setOnClickListener {
            Prefs.save(
                ctx = activity,
                apiKey = key.text.toString(),
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
