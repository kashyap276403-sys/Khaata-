package com.aurax.guard.smart

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import com.aurax.guard.AlertSettingsActivity
import com.aurax.guard.AlertType
import com.aurax.guard.EventsActivity
import com.aurax.guard.Gate
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs
import com.aurax.guard.ai.AiSettingsActivity
import com.aurax.guard.oem.OemReadinessActivity
import com.aurax.guard.remote.RemotePrefs
import com.aurax.guard.telegram.TelegramSettingsActivity
import com.aurax.guard.ui.TelemetryActivity
import com.aurax.guard.ui.Ui

/** Top-right Settings: every ON/OFF switch in one list, then links to the detailed screens. */
class SettingsHubActivity : Activity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsHubActivity, 16), Ui.dp(this@SettingsHubActivity, 24), Ui.dp(this@SettingsHubActivity, 16), Ui.dp(this@SettingsHubActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = Ui.backdrop(context); isFillViewport = true; addView(body) })
        render()
    }

    override fun onResume() { super.onResume(); if (::body.isInitialized) render() }

    private fun sw(text: String, sub: String, read: () -> Boolean, on: (Boolean) -> Unit): LinearLayout {
        val c = this
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4))
            addView(Ui.tintSwitch(Switch(c)).apply {
                this.text = text; textSize = 15f; setTextColor(Color.WHITE); isChecked = read()
                setOnCheckedChangeListener { v, b -> if (v.isPressed) { on(b); SmartScheduler.arm(c) } }
            })
            addView(Ui.dimText(c, sub, 12f))
        }
    }

    private fun link(title: String, sub: String, cls: Class<*>) = Ui.card(this, null, 8, drops = 0).apply {
        addView(Ui.text(context, title, 15f, bold = true))
        addView(Ui.dimText(context, sub))
        setOnClickListener { try { startActivity(Intent(this@SettingsHubActivity, cls)) } catch (_: Exception) {} }
    }

    private fun restartGuard() { try { if (Prefs.guardOn(this)) GuardService.start(this, true) } catch (_: Exception) {} }

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "Settings"))
        body.addView(Ui.dimText(c, "Har feature ka ON/OFF yahin hai. Jo OFF hai wo band rehta hai."))

        val a = Ui.card(c, Ui.teal, 12)
        a.addView(Ui.text(c, "Suraksha", 16f, bold = true))
        a.addView(sw("Thief Protection", "Master switch. Band karne par confirm poochta hai.", { Prefs.guardOn(c) }) { on ->
            if (on) { Prefs.setGuardOn(c, true); try { GuardService.start(c, true) } catch (_: Exception) {} }
            else Ui.showGlass(android.app.AlertDialog.Builder(c).setTitle("Protection band karein?")
                .setMessage("Jhatka, galat password, SIM aur charger ki nigrani ruk jayegi.")
                .setPositiveButton("Haan, band karein") { _, _ -> Prefs.setGuardOn(c, false); GuardService.stop(c); com.aurax.guard.Siren.stop(); Prefs.log(c, "Suraksha band (user)"); render() }
                .setNegativeButton("Chalu rakhein") { _, _ -> render() })
        })
        a.addView(sw("Tactical Lockdown", "Sab tamper guard ek saath (Thief Protection kabhi band nahi hota).", { Lockdown.active(c) }) { on ->
            val ap = applicationContext
            Thread { if (on) Lockdown.enter(ap, true) else Lockdown.exit(ap, true); runOnUiThread { if (!isDestroyed) render() } }.start()
        })
        a.addView(sw("Jhatka lagne par screen lock", "Phone chheene par lock.", { Prefs.snatchLockOn(c) }) { Prefs.setSnatchLockOn(c, it); restartGuard() })
        a.addView(sw("Tez chheen pakad (fast-pull)", "Ek bahut zor ka jhatka + phone ke saath le jaana. Girne/takrane par nahi.", { SmartPrefs.fastPull(c) }) { SmartPrefs.setFastPull(c, it); try { GuardService.instance?.reloadMotion() } catch (_: Exception) {} })
        a.addView(sw("Haath mat lagao (Park) mode", "Table par rakhe phone ko chhute hi alarm.", { Prefs.parkOn(c) }) { Prefs.setParkOn(c, it); restartGuard() })
        a.addView(sw("Siren", "Awaaz wala alarm.", { Prefs.sirenOn(c) }) { Prefs.setSirenOn(c, it) })
        a.addView(sw("Stealth / Silent", "Awaaz nahi: sirf lock + alert.", { SmartPrefs.stealth(c) }) { SmartPrefs.setStealth(c, it) })
        a.addView(sw("Galat password alert + photo", "3 galat try par photo aur alert.", { Prefs.alertTypeOn(c, AlertType.WRONG_UNLOCK.key) }) { Prefs.setAlertTypeOn(c, AlertType.WRONG_UNLOCK.key, it) })
        a.addView(sw("SIM alert", "SIM badalne par alert.", { Prefs.simAlertOn(c) }) { Prefs.setSimAlertOn(c, it) })
        a.addView(sw("Charger / USB alert", "Charger ya USB lagne-nikalne par alert.", { Prefs.chargerAlarmOn(c) }) { Prefs.setChargerAlarmOn(c, it) })
        body.addView(a)

        val t = Ui.card(c, null, 12)
        t.addView(Ui.text(c, "Telegram aur survival", 16f, bold = true))
        t.addView(sw("Telegram remote control", "/loc /photo /status /trace /saver /boost /lockdown /normal.", { RemotePrefs.masterOn(c) }) { RemotePrefs.setMasterOn(c, it) })
        t.addView(sw("Emergency Trace (kam battery)", "Sirf battery kam hone par location.", { SmartPrefs.traceOn(c) }) { SmartPrefs.setTraceOn(c, it) })
        t.addView(sw("Emergency photo", "Trace aur remote photo ke liye.", { SmartPrefs.tracePhoto(c) }) { SmartPrefs.setTracePhoto(c, it) })
        t.addView(sw("Battery saver (abhi)", "Extras kam, alerts chalu.", { SmartPrefs.saverManual(c) }) { SmartPrefs.setSaverManual(c, it) })
        t.addView(sw("Battery saver khud", "Battery kam hote hi apne aap.", { SmartPrefs.saverAuto(c) }) { SmartPrefs.setSaverAuto(c, it) })
        body.addView(t)

        val s = Ui.card(c, null, 12)
        s.addView(Ui.text(c, "Smart aur extras", 16f, bold = true))
        s.addView(sw("Smart Engine", "Risk score aur summary (phone par, bina internet).", { Prefs.smartOn(c) }) { Prefs.setSmartOn(c, it) })
        s.addView(sw("Auto-Tune", "Galat alarm par sensitivity khud halki.", { Prefs.autoTune(c) }) { Prefs.setAutoTune(c, it) })
        s.addView(sw("Heartbeat", "Guard zinda hai ka sanket.", { SmartPrefs.heartbeatOn(c) }) { SmartPrefs.setHeartbeatOn(c, it) })
        s.addView(sw("Daily digest", "Roz ka summary.", { SmartPrefs.digestOn(c) }) { SmartPrefs.setDigestOn(c, it) })
        s.addView(sw("Guard Boost", "High-risk ke baad extra snapshot.", { SmartPrefs.boostOn(c) }) { SmartPrefs.setBoostOn(c, it) })
        s.addView(sw("APK / file check", "Download ki jaanch (sirf report).", { SmartPrefs.apkScanOn(c) }) { SmartPrefs.setApkScanOn(c, it) })
        body.addView(s)

        body.addView(link("Test Mode", "Safe jhatka simulation + guard check (koi asli lock/siren nahi)", TestModeActivity::class.java))
        body.addView(Ui.text(c, "Aur settings", 16f, bold = true).apply { setPadding(Ui.dp(c, 4), Ui.dp(c, 18), 0, Ui.dp(c, 2)) })
        body.addView(link("Telegram bot", "Token, Chat ID, device label, test alert", TelegramSettingsActivity::class.java))
        body.addView(link("Alert controls", "Alert types, quiet timer, sensitivity", AlertSettingsActivity::class.java))
        body.addView(link("Emergency & Smart (poora)", "Trace threshold, modes, digest, remote details", EmergencySettingsActivity::class.java))
        body.addView(link("Look & AI", "Liquid Glass / Cyber style, theme, rang", AiSettingsActivity::class.java))
        body.addView(link("Phone readiness", "Background / OEM check", OemReadinessActivity::class.java))
        body.addView(link("Event history", "Saare alerts", EventsActivity::class.java))
        body.addView(link("Live stream", "Terminal log", TelemetryActivity::class.java))
    }
}
