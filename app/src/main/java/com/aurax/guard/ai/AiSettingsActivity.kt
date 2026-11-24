package com.aurax.guard.ai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.aurax.guard.EventStore
import com.aurax.guard.Gate
import com.aurax.guard.Prefs
import com.aurax.guard.ui.Ui
import java.util.concurrent.Executors

/**
 * "AI & Look": optional AI summaries, smart hints and the glass look switch.
 * The API key is typed here only; it stays on this phone (encrypted) and is never in the source code.
 * With AI off, nothing changes: normal alerts, photos, location links and queue retries work as before.
 */
class AiSettingsActivity : Activity() {
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var status: TextView
    private lateinit var master: Switch
    private lateinit var chips: LinearLayout
    private lateinit var hintsBox: LinearLayout
    private var showing = false
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 48))
        }
        root.addView(Ui.heading(c, "AI & Look"))
        root.addView(Ui.dimText(c, "AI optional hai. Band ho ya internet na ho to normal Telegram alerts, photo, location aur queue pehle jaisa chalte hain."))

        val ai = Ui.card(c, null, 14)
        ai.addView(Ui.text(c, "AI summary", 15f, bold = true))
        ai.addView(Ui.dimText(c, "API key: apne AI provider (Anthropic) ke console se. Yahin daalein, GitHub ya code me nahi."))
        ai.addView(Ui.text(c, "API KEY", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        key = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "sk-ant-... (save ke baad yahan nahi dikhta)")
        ai.addView(key)
        ai.addView(Ui.button(c, "Key dikhayein / chhupayein", Ui.Btn.GHOST, 4) { toggleShow() })
        ai.addView(Ui.text(c, "Model", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        model = field(InputType.TYPE_CLASS_TEXT, AiConfig.DEFAULT_MODEL)
        model.setText(AiConfig.model(c))
        ai.addView(model)
        ai.addView(Ui.button(c, "Save", Ui.Btn.PRIMARY, 10) { save() })
        root.addView(ai)

        val opt = Ui.card(c, null, 12)
        master = Ui.tintSwitch(Switch(c)).apply {
            text = "AI summary chalu"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { v, on ->
                if (!v.isPressed) return@setOnCheckedChangeListener
                if (on && !AiConfig.hasKey(c)) { v.isChecked = false; toast("Pehle API key save karein") } else { AiConfig.setEnabled(c, on); render() }
            }
        }
        opt.addView(master)
        opt.addView(Ui.text(c, "Bhasha / style", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
        chips = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        opt.addView(chips)
        opt.addView(Ui.tintSwitch(Switch(c)).apply {
            text = "AI ko Google Maps link bhi bhejein"
            setTextColor(Color.WHITE)
            isChecked = AiConfig.shareLink(c)
            setOnCheckedChangeListener { v, on -> if (v.isPressed) AiConfig.setShareLink(c, on) }
        })
        opt.addView(Ui.dimText(c, "AI ko sirf event ka type, device naam, battery, network, samay, location accuracy aur 24 ghante ke alerts ki ginti jati hai. Photo, audio aur coordinates kabhi nahi. Link default band hai."))
        root.addView(opt)

        val st = Ui.card(c, null, 12)
        st.addView(Ui.text(c, "Status", 15f, bold = true))
        status = Ui.dimText(c, "")
        st.addView(status)
        st.addView(Ui.button(c, "AI Test", Ui.Btn.PRIMARY, 10) { test() })
        st.addView(Ui.button(c, "Saved key hatayein", Ui.Btn.DANGER, 6) {
            AiConfig.clearKey(c); render()
        })
        root.addView(st)

        val dx = Ui.card(c, null, 12)
        dx.addView(Ui.text(c, "Smart hints (sensitivity aur phone health)", 15f, bold = true))
        hintsBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        dx.addView(hintsBox)
        dx.addView(Ui.button(c, "AI se asaan bhasha me samjhao", Ui.Btn.GHOST, 8) { explainHints() })
        dx.addView(Ui.dimText(c, "Hints aapke apne alert history se phone me hi bante hain. Ye sirf salah hai, koi setting apne aap nahi badalti."))
        root.addView(dx)

        val look = Ui.card(c, null, 12)
        look.addView(Ui.tintSwitch(Switch(c)).apply {
            text = "Liquid glass look"
            setTextColor(Color.WHITE)
            isChecked = Ui.glassOn(c)
            setOnCheckedChangeListener { v, on -> if (v.isPressed) { Ui.setGlass(c, on); toast("Agli baar screen kholne par poora lagega"); recreate() } }
        })
        look.addView(Ui.text(c, "Glass kitna transparent", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        look.addView(Ui.chipRow(c, listOf(0 to "Clear (poora transparent)", 1 to "Soft", 2 to "Frosted").map { (v, n) ->
            Ui.chip(c, n, Ui.clarity(c) == v) { Ui.setClarity(c, v); recreate() }
        }, 6))
        look.addView(Ui.tintSwitch(Switch(c)).apply {
            text = "Paani ki boondein / bubbles"
            setTextColor(Color.WHITE)
            isChecked = Ui.dropsOn(c)
            setOnCheckedChangeListener { v, on -> if (v.isPressed) { Ui.setDropsOn(c, on); recreate() } }
        })
        look.addView(Ui.text(c, "Dark theme", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        look.addView(Ui.chipRow(c, com.aurax.guard.ui.ThemePalette.ALL.map { p ->
            Ui.chip(c, p.title, Ui.themeKey(c) == p.key) { Ui.setTheme(c, p.key); recreate() }
        }, 6))
        look.addView(Ui.text(c, "Rang (accent)", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        look.addView(Ui.chipRow(c, com.aurax.guard.ui.Accent.ALL.map { a ->
            Ui.chip(c, a.title, Ui.accent.key == a.key) { Ui.setAccent(c, a.key); recreate() }
        }, 6))
        look.addView(Ui.dimText(c, "Aurora me peeche kai rang ek saath dikhte hain (gulabi, baingani, neela, firozi)."))
        look.addView(Ui.dimText(c, "Dark: hara-neela glow. Midnight: gehra neela-baingani. AMOLED: poora kaala, OLED screen par battery bachata hai."))
        look.addView(Ui.dimText(c, "Clear me glass lagbhag poora transparent hota hai: sirf patli chamakdar kinaar aur peeche ka rang dikhta hai. Boondein default band hain. Animation ya blur nahi hai, isliye battery par asar nahi."))
        root.addView(look)

        setContentView(ScrollView(c).apply { background = com.aurax.guard.ui.Ui.backdrop(context); isFillViewport = true; addView(root) })
    }

    private fun field(type: Int, hint: String) = EditText(this).apply {
        inputType = type
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(150, 255, 255, 255))
        isSingleLine = true
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun toggleShow() {
        showing = !showing
        key.inputType = InputType.TYPE_CLASS_TEXT or (if (showing) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        key.setSelection(key.text.length)
    }

    private fun save() {
        val k = key.text.toString()
        if (k.isNotBlank()) {
            if (k.trim().length < 20) { toast("API key chhoti lag rahi hai"); return }
            if (!AiConfig.setKey(this, k)) { toast("Phone ki secure storage use nahi ho payi"); return }
            key.setText("")
        } else if (!AiConfig.hasKey(this)) { toast("API key daalein"); return }
        AiConfig.setModel(this, model.text.toString().ifBlank { AiConfig.DEFAULT_MODEL })
        toast("Save ho gaya. 'AI Test' dabakar check karein.")
        render()
    }

    private fun test() {
        val app = applicationContext
        val k = AiConfig.key(app) ?: run { toast("Pehle API key save karein"); return }
        status.text = "Test ho raha hai..."
        io.execute {
            val r = AiClient.complete(k, AiConfig.model(app), "Reply with the single word OK.", "ping", 10)
            val msg = when (r) {
                is AiResult.Ok -> "AI theek chal rahi hai."
                is AiResult.Err -> AiPrompt.redact(r.why, k)
            }
            AiConfig.setStatus(app, msg)
            runOnUiThread { if (!isDestroyed) render() }
        }
    }

    private fun explainHints() {
        val app = applicationContext
        if (!AiConfig.ready(app)) { toast("AI chalu aur key save honi chahiye"); return }
        val hints = currentHints()
        io.execute {
            val r = AiSummarizer.explainHints(app, hints)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val text = if (r is AiResult.Ok) r.text else "AI nahi chali: " + (r as AiResult.Err).why + ". Upar ke normal hints dekhein."
                Ui.showGlass(android.app.AlertDialog.Builder(this).setTitle("AI salah").setMessage(text).setPositiveButton("OK", null))
            }
        }
    }

    private fun currentHints(): List<String> {
        val ev = try { EventStore.readAll(this) } catch (_: Exception) { emptyList() }
        val out = SensitivityAdvisor.hints(ev, System.currentTimeMillis(), Prefs.sensitivity(this), Prefs.parkOn(this), Prefs.snatchLockOn(this)).toMutableList()
        try {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) out.add("Battery optimization abhi ON hai: background me guard band ho sakta hai. 'Phone readiness' kholein.")
        } catch (_: Exception) {}
        if (com.aurax.guard.telegram.TelegramDispatcher.hasWaiting(this)) out.add("Telegram queue me alerts ruke hain (internet ya Telegram setting check karein).")
        return out
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) { finish(); return }
        render()
    }

    private fun render() {
        val c = this
        master.isChecked = AiConfig.enabled(c)
        status.text = (if (AiConfig.hasKey(c)) "Key: saved (...${AiConfig.keyTail(c)})" else "Key: saved nahi") +
            "\nAI: " + (if (AiConfig.ready(c)) "chalu" else "band (normal alerts chalte rahenge)") + "\nAakhri: " + AiConfig.status(c)
        chips.removeAllViews()
        chips.addView(Ui.chipRow(c, AiLang.values().map { l -> Ui.chip(c, l.title, AiConfig.lang(c) == l) { AiConfig.setLang(c, l); render() } }, 6))
        chips.addView(Ui.chipRow(c, AiStyle.values().map { s -> Ui.chip(c, s.title, AiConfig.style(c) == s) { AiConfig.setStyle(c, s); render() } }, 6))
        hintsBox.removeAllViews()
        currentHints().forEach { hintsBox.addView(Ui.dimText(c, "- $it")) }
    }
}
