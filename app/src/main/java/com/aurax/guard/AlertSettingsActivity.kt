package com.aurax.guard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.aurax.guard.ui.Ui

/** One place for: which alerts are on, quiet/snooze timer, and how long motion is ignored after handling the phone. */
class AlertSettingsActivity : Activity() {
    private lateinit var quietInfo: TextView
    private lateinit var graceInfo: TextView
    private lateinit var smartBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 48))
        }
        root.addView(Ui.heading(c, "Alert controls"))
        root.addView(Ui.dimText(c, "Kaun se alert chalu rahein, aur roz ke use ke liye Quiet timer."))

        // ---- quiet ----
        val q = Ui.card(c, null, 14)
        q.addView(Ui.text(c, "Quiet / Snooze", 15f, bold = true))
        quietInfo = Ui.dimText(c, "")
        q.addView(quietInfo)
        q.addView(Ui.chipRow(c, listOf(
            Ui.chip(c, "15 min", false) { quiet(15) },
            Ui.chip(c, "1 ghanta", false) { quiet(60) },
            Ui.chip(c, "Off (chalu karein)", false) { quiet(0) },
        ), 8))
        q.addView(Ui.dimText(c, "Quiet me jhatka, charger aur siren ruk jate hain. Galat password ki photo aur SIM alert phir bhi chalte hain."))
        root.addView(q)

        // ---- detection ----
        val d = Ui.card(c, null, 12)
        d.addView(Ui.text(c, "Detection (kya pakda jaye)", 15f, bold = true))
        d.addView(sw("Jhatka lagne par screen lock", { Prefs.snatchLockOn(c) }) { Prefs.setSnatchLockOn(c, it); restart() })
        d.addView(sw("Charger nikalte hi alarm", { Prefs.chargerAlarmOn(c) }) { Prefs.setChargerAlarmOn(c, it); restart() })
        d.addView(sw("Siren bajao", { Prefs.sirenOn(c) }) { Prefs.setSirenOn(c, it) })
        d.addView(sw("Galat password par intruder photo", { Prefs.selfieOn(c) }) { Prefs.setSelfieOn(c, it) })
        d.addView(sw("Audio clip (10-15 sec)", { Prefs.audioOn(c) }) {
            Prefs.setAudioOn(c, it)
            if (it && !AudioClip.hasPermission(c)) requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 9) else restart()
        })
        root.addView(d)

        // ---- per-type alerts ----
        val t = Ui.card(c, null, 12)
        t.addView(Ui.text(c, "Alert bhejna / save karna (type ke hisaab se)", 15f, bold = true))
        t.addView(Ui.dimText(c, "Band karne par us type ka alert queue/Telegram me nahi jayega. Test aur Panic hamesha chalte hain."))
        listOf(AlertType.MOTION, AlertType.CHARGER, AlertType.WRONG_UNLOCK).forEach { ty ->
            t.addView(sw(ty.title, { Prefs.alertTypeOn(c, ty.key) }) { Prefs.setAlertTypeOn(c, ty.key, it) })
        }
        t.addView(sw(AlertType.SIM.title, { Prefs.simAlertOn(c) }) { Prefs.setSimAlertOn(c, it) })
        root.addView(t)

        // ---- grace ----
        val g = Ui.card(c, null, 12)
        g.addView(Ui.text(c, "Galat trigger se bachav", 15f, bold = true))
        graceInfo = Ui.dimText(c, "")
        g.addView(graceInfo)
        g.addView(Ui.chipRow(c, listOf(5, 10, 20, 30).map { s ->
            Ui.chip(c, "$s sec", Prefs.graceSec(c) == s) { Prefs.setGraceSec(c, s); renderGrace(); recreate() }
        }, 8))
        g.addView(sw("Phone unlock/use me ho to jhatka ignore", { Prefs.ignoreWhileUnlocked(c) }) { Prefs.setIgnoreWhileUnlocked(c, it) })
        g.addView(Ui.dimText(c, "Screen on/off ya unlock ke baad itne second jhatka ignore hota hai (phone rakhna ya uthana). Sensitivity: Protection settings me Kam / Madhyam / Zyada."))
        root.addView(g)

        // ---- local smart engine ----
        val sm = Ui.card(c, Ui.teal, 12)
        sm.addView(Ui.text(c, "Local Smart Engine", 15f, bold = true))
        sm.addView(Ui.dimText(c, "Phone par chalta hai, bina internet, API key ya account ke. Har alert ka risk (Low/Medium/High), summary aur agla kadam batata hai."))
        sm.addView(sw("Smart summary aur risk", { Prefs.smartOn(c) }) { Prefs.setSmartOn(c, it) })
        sm.addView(sw("Galat trigger par sensitivity khud halki karein", { Prefs.autoTune(c) }) { Prefs.setAutoTune(c, it); renderSmart() })
        sm.addView(Ui.dimText(c, "Auto-adjust sirf tab, jab 24 ghante me 4+ jhatke aaye aur galat password/SIM/door jana jaisa koi sanket na ho. Ek baar me ek kadam, din me ek baar se zyada nahi, protection kabhi band nahi karta, aur wapas le sakte ho."))
        smartBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        sm.addView(smartBox)
        root.addView(sm)

        setContentView(ScrollView(c).apply { background = com.aurax.guard.ui.Ui.backdrop(context); isFillViewport = true; addView(root) })
        renderGrace()
        renderSmart()
    }

    private fun renderSmart() {
        val c = this
        smartBox.removeAllViews()
        val t = com.aurax.guard.smart.AutoTuner.current(c)
        if (t != null) {
            smartBox.addView(Ui.text(c, "Suggestion", 14f, bold = true, color = Ui.amber).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
            smartBox.addView(Ui.dimText(c, t.reason + " Naya: " + Prefs.snatchNames[t.newLevel] + ", grace " + t.newGrace + "s"))
            smartBox.addView(Ui.button(c, "Ye suggestion lagayein", Ui.Btn.PRIMARY, 6) { com.aurax.guard.smart.AutoTuner.apply(c, t, auto = false); renderSmart(); renderGrace() })
        } else {
            smartBox.addView(Ui.dimText(c, "Abhi koi badlav suggest nahi hua. Sensitivity theek lag rahi hai.").apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
        }
        if (Prefs.autoPrev(c) != null) {
            smartBox.addView(Ui.dimText(c, "Aakhri smart badlav: " + Prefs.autoNote(c)))
            smartBox.addView(Ui.button(c, "Wapas purani setting karein", Ui.Btn.GHOST, 6) { com.aurax.guard.smart.AutoTuner.undo(c); renderSmart(); renderGrace() })
        }
    }

    private fun sw(text: String, read: () -> Boolean, on: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        setPadding(0, Ui.dp(this@AlertSettingsActivity, 8), 0, Ui.dp(this@AlertSettingsActivity, 8))
        isChecked = read()
        setOnCheckedChangeListener { v, b -> if (v.isPressed) on(b) }
    }

    private fun restart() {
        try { if (Prefs.guardOn(this)) GuardService.start(this, fromUi = true) } catch (_: Exception) {}
    }

    private fun quiet(min: Int) {
        Prefs.setSnoozeUntil(this, if (min == 0) 0L else System.currentTimeMillis() + min * 60_000L)
        Prefs.log(this, if (min == 0) "Quiet band: detection dobara chalu" else "Quiet $min min")
        renderQuiet()
    }

    private fun renderQuiet() {
        val left = (Prefs.snoozeUntil(this) - System.currentTimeMillis()) / 60000
        quietInfo.text = if (Prefs.snoozed(this)) "Quiet chalu: ${left + 1} min baaki" else "Quiet band hai: detection normal chal raha hai"
    }

    private fun renderGrace() {
        graceInfo.text = "Abhi: ${Prefs.graceSec(this)} second"
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) { finish(); return }
        renderQuiet()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (!AudioClip.hasPermission(this)) Prefs.setAudioOn(this, false)
        restart()
        recreate()
    }
}
