package com.aurax.guard.smart

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.aurax.guard.AudioClip
import com.aurax.guard.Gate
import com.aurax.guard.GuardService
import com.aurax.guard.LocationFetcher
import com.aurax.guard.Prefs
import com.aurax.guard.QuietRules
import com.aurax.guard.ui.Ui

/** Emergency Trace, Guard Boost, digest, heartbeat, modes, stealth and the feature readiness map. */
class EmergencySettingsActivity : Activity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@EmergencySettingsActivity, 16), Ui.dp(this@EmergencySettingsActivity, 24), Ui.dp(this@EmergencySettingsActivity, 16), Ui.dp(this@EmergencySettingsActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = Ui.backdrop(context); isFillViewport = true; addView(body) })
        render()
    }

    override fun onResume() { super.onResume(); if (::body.isInitialized) render() }

    private fun sw(text: String, read: () -> Boolean, on: (Boolean) -> Unit) = Ui.tintSwitch(Switch(this)).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE)
        setPadding(0, Ui.dp(this@EmergencySettingsActivity, 8), 0, Ui.dp(this@EmergencySettingsActivity, 8))
        isChecked = read()
        setOnCheckedChangeListener { v, b -> if (v.isPressed) { on(b); changed() } }
    }

    private fun changed() {
        SmartScheduler.arm(this)
        try { if (SmartPrefs.traceOn(this)) { val m = com.aurax.guard.DeviceInfo.read(this); Thread { TraceEngine.onBattery(applicationContext, m.batteryPct, m.charging) }.start() } } catch (_: Exception) {}
        render()
    }

    private fun restartGuard() {
        try { if (Prefs.guardOn(this)) GuardService.start(this, true) } catch (_: Exception) {}
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "Emergency & Smart"))
        body.addView(Ui.dimText(c, "Sab kuch phone par chalta hai: koi AI key ya account nahi chahiye. Ye sab default me band hai. Alert Telegram queue se jate hain (offline me baad me)."))

        // ---- all ON / OFF switches in one place ----
        val ms = Ui.card(c, Ui.teal, 14)
        ms.addView(Ui.text(c, "Sab ON / OFF ek jagah", 15f, bold = true))
        ms.addView(Ui.dimText(c, "Jo OFF hai wo band rehta hai: na background me chalta hai, na alert bhejta hai. Protection kabhi apne aap band nahi hota."))
        ms.addView(sw("Thief Protection (master)", { Prefs.guardOn(c) }) { on ->
            if (on) {
                Prefs.setGuardOn(c, true)
                try { GuardService.start(c, true) } catch (_: Exception) {}
            } else {
                // switch back until confirmed
                Ui.showGlass(android.app.AlertDialog.Builder(c).setTitle("Protection band karein?")
                    .setMessage("Jhatka, galat password, SIM aur charger ki nigrani ruk jayegi.")
                    .setPositiveButton("Haan, band karein") { _, _ -> Prefs.setGuardOn(c, false); GuardService.stop(c); com.aurax.guard.Siren.stop(); Prefs.log(c, "Suraksha band (user)"); render() }
                    .setNegativeButton("Chalu rakhein") { _, _ -> render() })
            }
        })
        ms.addView(sw("Tactical Lockdown (sab tamper guard ek saath)", { Lockdown.active(c) }) { on ->
            val a = applicationContext
            Thread { if (on) Lockdown.enter(a, true) else Lockdown.exit(a, true); runOnUiThread { if (!isDestroyed) render() } }.start()
        })
        ms.addView(sw("Jhatka lagne par screen lock", { Prefs.snatchLockOn(c) }) { Prefs.setSnatchLockOn(c, it); restartGuard() })
        ms.addView(sw("Haath mat lagao (Park) mode", { Prefs.parkOn(c) }) { Prefs.setParkOn(c, it); restartGuard() })
        ms.addView(sw("Siren", { Prefs.sirenOn(c) }) { Prefs.setSirenOn(c, it) })
        ms.addView(sw("Stealth / Silent (awaaz nahi)", { SmartPrefs.stealth(c) }) { SmartPrefs.setStealth(c, it) })
        ms.addView(sw("Galat password alert", { Prefs.alertTypeOn(c, com.aurax.guard.AlertType.WRONG_UNLOCK.key) }) { Prefs.setAlertTypeOn(c, com.aurax.guard.AlertType.WRONG_UNLOCK.key, it) })
        ms.addView(sw("SIM alert", { Prefs.simAlertOn(c) }) { Prefs.setSimAlertOn(c, it) })
        ms.addView(sw("Emergency Trace", { SmartPrefs.traceOn(c) }) { SmartPrefs.setTraceOn(c, it) })
        ms.addView(sw("Emergency photo", { SmartPrefs.tracePhoto(c) }) { SmartPrefs.setTracePhoto(c, it) })
        ms.addView(sw("Battery saver (abhi)", { SmartPrefs.saverManual(c) }) { SmartPrefs.setSaverManual(c, it) })
        ms.addView(sw("Battery saver khud (kam battery par)", { SmartPrefs.saverAuto(c) }) { SmartPrefs.setSaverAuto(c, it) })
        ms.addView(sw("Heartbeat", { SmartPrefs.heartbeatOn(c) }) { SmartPrefs.setHeartbeatOn(c, it) })
        ms.addView(sw("Smart Engine (risk + summary)", { Prefs.smartOn(c) }) { Prefs.setSmartOn(c, it) })
        ms.addView(sw("Auto-Tune (sensitivity khud halki)", { Prefs.autoTune(c) }) { Prefs.setAutoTune(c, it) })
        ms.addView(sw("Telegram remote control", { com.aurax.guard.remote.RemotePrefs.masterOn(c) }) { com.aurax.guard.remote.RemotePrefs.setMasterOn(c, it); SmartScheduler.arm(c) })
        ms.addView(sw("APK / file check", { SmartPrefs.apkScanOn(c) }) { SmartPrefs.setApkScanOn(c, it) })
        body.addView(ms)

        // ---- trace ----
        val t = Ui.card(c, Ui.red, 14)
        t.addView(Ui.text(c, "Emergency Trace (kam battery)", 15f, bold = true))
        t.addView(sw("Battery kam hote hi location bhejo", { SmartPrefs.traceOn(c) }) { SmartPrefs.setTraceOn(c, it) })
        t.addView(Ui.dimText(c, "Threshold se neeche aate hi turant, phir har interval par: Device, battery %, samay, Maps link, kitna chala aur pichhle points. Charger lagne par ruk jata hai; threshold se upar charge hone par reset."))
        t.addView(Ui.text(c, "Threshold: ${SmartPrefs.threshold(c)}%", 14f, bold = true))
        t.addView(Ui.chipRow(c, listOf(30, 40, 50, 60).map { v -> Ui.chip(c, "$v%", SmartPrefs.threshold(c) == v) { SmartPrefs.setThreshold(c, v); changed() } }, 8))
        t.addView(Ui.text(c, "Har: ${SmartPrefs.intervalMin(c)} min", 14f, bold = true))
        t.addView(Ui.chipRow(c, listOf(15 to "15 min", 30 to "30 min", 60 to "1 ghanta", 120 to "2 ghante").map { (v, n) -> Ui.chip(c, n, SmartPrefs.intervalMin(c) == v) { SmartPrefs.setIntervalMin(c, v); changed() } }, 8))
        t.addView(Ui.text(c, "Critical alert: ${SmartPrefs.criticalPct(c)}% (ek baar)", 14f, bold = true))
        t.addView(Ui.chipRow(c, listOf(20, 15, 10, 5).map { v -> Ui.chip(c, "$v%", SmartPrefs.criticalPct(c) == v) { SmartPrefs.setCriticalPct(c, v); changed() } }, 8))
        t.addView(sw("Emergency photo bhi lo", { SmartPrefs.tracePhoto(c) }) { SmartPrefs.setTracePhoto(c, it) })
        t.addView(Ui.dimText(c, "Camera band/locked ho to photo ke bina location + status bheja jata hai (alert me likha hota hai)."))
        val st = SmartPrefs.state(c)
        t.addView(Ui.dimText(c, "Abhi: " + (if (!SmartPrefs.traceOn(c)) "band" else if (st.active) "trace chal raha hai" + (if (st.criticalSent) " (critical bhej diya)" else "") else "tayyar, battery theek hai")))
        body.addView(t)

        // ---- telegram remote control ----
        val rc = Ui.card(c, Ui.teal, 12)
        rc.addView(Ui.text(c, "Telegram remote control", 15f, bold = true))
        val rOn = com.aurax.guard.remote.RemotePrefs.masterOn(c)
        rc.addView(sw("Remote control ON", { com.aurax.guard.remote.RemotePrefs.masterOn(c) }) { com.aurax.guard.remote.RemotePrefs.setMasterOn(c, it); SmartScheduler.arm(c) })
        rc.addView(Ui.dimText(c, if (!com.aurax.guard.telegram.TelegramConfig.configured(c)) "Pehle Telegram alerts me token aur Chat ID jodein." else if (rOn) "Chalu: sirf aapki configured private chat se commands. Polling ${com.aurax.guard.remote.RemoteControl.interval(c) / 60_000} min me ek baar (screen off ya saver me dheemi)." else "Band: koi command nahi sunta aur koi polling nahi."))
        rc.addView(sw("/loc aur /trace (location)", { com.aurax.guard.remote.RemotePrefs.locOn(c) }) { com.aurax.guard.remote.RemotePrefs.setLocOn(c, it) })
        rc.addView(sw("/photo (photo)", { com.aurax.guard.remote.RemotePrefs.photoOn(c) }) { com.aurax.guard.remote.RemotePrefs.setPhotoOn(c, it) })
        rc.addView(sw("/status, /saver, /boost", { com.aurax.guard.remote.RemotePrefs.modeOn(c) }) { com.aurax.guard.remote.RemotePrefs.setModeOn(c, it) })
        rc.addView(Ui.dimText(c, "Commands: /loc /photo /status /trace /saver on|off /boost /help. Koi command file delete ya Thief Protection band nahi kar sakta. Cooldown: /photo 60s, /loc 30s, /boost 2 min, aur ghante me max 20. Jawab Telegram queue se aate hain, aur har command Activity log me dikhta hai."))
        body.addView(rc)

        // ---- battery saver ----
        val sv = Ui.card(c, null, 12)
        sv.addView(Ui.text(c, "Battery saver", 15f, bold = true))
        sv.addView(Ui.dimText(c, "Abhi: " + BatterySaver.describe(SmartPrefs.saverManual(c), SmartPrefs.saverAuto(c), SmartPrefs.saverPct(c), BatterySaver.active(c))))
        sv.addView(sw("Battery saver abhi chalu karein", { SmartPrefs.saverManual(c) }) { SmartPrefs.setSaverManual(c, it) })
        sv.addView(sw("Battery kam hone par khud chalu", { SmartPrefs.saverAuto(c) }) { SmartPrefs.setSaverAuto(c, it) })
        sv.addView(Ui.chipRow(c, listOf(15, 20, 30).map { v -> Ui.chip(c, "$v% se kam", SmartPrefs.saverPct(c) == v) { SmartPrefs.setSaverPct(c, v); changed() } }, 8))
        sv.addView(Ui.dimText(c, "Saver sirf extras rokta hai: daily digest, heartbeat, optional AI line, aur Guard Boost ke snapshots (2 tak). Motion guard, SIM/galat password alert, Emergency Trace location, emergency photo aur Telegram queue hamesha chalte hain."))
        body.addView(sv)

        // ---- boost ----
        val b = Ui.card(c, null, 12)
        b.addView(Ui.text(c, "Guard Boost", 15f, bold = true))
        b.addView(sw("High-risk alert ke baad extra snapshot", { SmartPrefs.boostOn(c) }) { SmartPrefs.setBoostOn(c, it) })
        b.addView(Ui.dimText(c, "Sirf High risk par: 30 minute tak, pehle ~45 sec baad phir har 5 min me location + photo (max 6)."))
        body.addView(b)

        // ---- modes / stealth ----
        val m = Ui.card(c, Ui.teal, 12)
        m.addView(Ui.text(c, "Mode profiles", 15f, bold = true))
        val cur = Mode.of(SmartPrefs.mode(c))
        m.addView(Ui.dimText(c, "Ek tap me sensitivity/grace/siren set hote hain; baad me alag se badal sakte ho. Abhi: " + (cur?.title ?: "koi nahi")))
        Mode.ALL.forEach { md ->
            m.addView(Ui.button(c, md.title, if (cur == md) Ui.Btn.PRIMARY else Ui.Btn.GHOST, 6) { ModeProfiles.apply(c, md); render() })
            m.addView(Ui.dimText(c, md.desc))
        }
        m.addView(sw("Stealth: siren/awaaz nahi (sirf lock + alert)", { SmartPrefs.stealth(c) }) { SmartPrefs.setStealth(c, it) })
        m.addView(sw("Charger lagा ho to jhatka ignore", { SmartPrefs.ignoreCharging(c) }) { SmartPrefs.setIgnoreCharging(c, it) })
        body.addView(m)

        // ---- digest / heartbeat ----
        val d = Ui.card(c, null, 12)
        d.addView(Ui.text(c, "Daily digest aur heartbeat", 15f, bold = true))
        d.addView(sw("Roz ka digest Telegram par", { SmartPrefs.digestOn(c) }) { SmartPrefs.setDigestOn(c, it) })
        d.addView(Ui.button(c, "Digest ka samay: " + QuietRules.fmt(SmartPrefs.digestMin(c)), Ui.Btn.GHOST, 6) {
            val v = SmartPrefs.digestMin(c)
            TimePickerDialog(c, { _, h, mi -> SmartPrefs.setDigestMin(c, h * 60 + mi); changed() }, v / 60, v % 60, true).show()
        })
        d.addView(sw("Heartbeat: guard zinda hai ka sanket", { SmartPrefs.heartbeatOn(c) }) { SmartPrefs.setHeartbeatOn(c, it) })
        d.addView(Ui.chipRow(c, listOf(6, 12, 24).map { h -> Ui.chip(c, "$h ghante", SmartPrefs.heartbeatHours(c) == h) { SmartPrefs.setHeartbeatHours(c, h); changed() } }, 8))
        d.addView(Ui.dimText(c, "Digest/heartbeat me sirf counts aur battery hoti hai, location ya photo nahi."))
        body.addView(d)

        // ---- readiness map ----
        val tg = try { com.aurax.guard.telegram.TelegramConfig.configured(c) } catch (_: Exception) { false }
        val rows = FeatureReadiness.rows(ReadyInputs(
            telegramReady = tg, camera = granted(Manifest.permission.CAMERA), location = LocationFetcher.hasPermission(c),
            bgLocation = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION), mic = AudioClip.hasPermission(c),
            notifications = android.os.Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
            deviceAdmin = try { getSystemService(android.app.admin.DevicePolicyManager::class.java).isAdminActive(android.content.ComponentName(c, com.aurax.guard.GuardAdminReceiver::class.java)) } catch (_: Exception) { false },
            guardOn = Prefs.guardOn(c), cameraService = GuardService.cameraCapable, traceOn = SmartPrefs.traceOn(c),
            tracePhoto = SmartPrefs.tracePhoto(c), audioOn = Prefs.audioOn(c), simOn = Prefs.simAlertOn(c),
        ))
        val r = Ui.card(c, null, 12)
        r.addView(Ui.text(c, "Feature readiness (kya kis cheez par chalta hai)", 15f, bold = true))
        rows.forEach { row ->
            r.addView(Ui.text(c, (if (row.ok) "OK  " else "!!  ") + row.feature, 14f, bold = true, color = if (row.ok) Ui.emerald else Ui.amber).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
            r.addView(Ui.dimText(c, "Chahiye: ${row.needs}. ${row.fix}"))
        }
        r.addView(Ui.dimText(c, "Vivo/iQOO (OriginOS/Funtouch): Settings > Battery > Background power consumption me auraX Guard ko 'No restriction' aur Autostart ON rakhein, warna screen off me trace ruk sakta hai."))
        body.addView(r)
    }
}
