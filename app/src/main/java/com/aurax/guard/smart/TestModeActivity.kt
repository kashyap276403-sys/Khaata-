package com.aurax.guard.smart

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.aurax.guard.Gate
import com.aurax.guard.GuardAdminReceiver
import com.aurax.guard.GuardService
import com.aurax.guard.LocationFetcher
import com.aurax.guard.Prefs
import com.aurax.guard.SensitivityProfile
import com.aurax.guard.SnatchDetector
import com.aurax.guard.ui.Ui
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Test Mode: 1) made-up movements through a fresh detector, 2) a 2-second check that the sensor is alive,
 * 3) a guard / permission / background check, 4) an optional 10-second live shake check.
 * Nothing here locks the screen, sounds the siren, sends an alert, saves an event or deletes a file.
 * Your guard settings are read before and after to prove they did not change.
 */
class TestModeActivity : Activity(), SensorEventListener {
    private lateinit var body: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var busy = false
    private var report: List<Triple<String, Boolean, String>> = emptyList()
    private var summary = "Test abhi nahi chala."

    // live shake check
    private var shaking = false
    private var peak = 0f
    private var wouldTrigger = false
    private var liveDetector: SnatchDetector? = null
    private val grav = FloatArray(3)
    private var linear = false
    private var liveText = ""
    private var lastUi = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@TestModeActivity, 16), Ui.dp(this@TestModeActivity, 24), Ui.dp(this@TestModeActivity, 16), Ui.dp(this@TestModeActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = Ui.backdrop(context); isFillViewport = true; addView(body) })
        render()
    }

    override fun onPause() { stopShake(); super.onPause() }
    override fun onDestroy() { stopShake(); io.shutdownNow(); super.onDestroy() }

    private data class Snap(val guard: Boolean, val snatch: Boolean, val park: Boolean, val level: Int, val running: Boolean, val fast: Boolean)
    private fun snap() = Snap(Prefs.guardOn(this), Prefs.snatchLockOn(this), Prefs.parkOn(this), Prefs.sensitivity(this), GuardService.instance != null, SmartPrefs.fastPull(this))

    private fun runTest() {
        if (busy) return
        busy = true; summary = "Test chal raha hai..."; report = emptyList(); render()
        val app = applicationContext
        io.execute {
            val rows = mutableListOf<Triple<String, Boolean, String>>()
            val before = snap()
            // 1) made-up movements
            val park = before.park
            val outs = TestModeRules.run(before.level, park, before.fast)
            outs.forEach { rows.add(Triple("Simulation: " + it.scenario.title, it.ok, (if (it.triggered) "alarm bajta" else "alarm nahi") + " (chahiye: " + (if (it.scenario.expectTrigger) "alarm" else "alarm nahi") + ")")) }
            val hint = TestModeRules.hint(outs, park)
            // 2) is the sensor alive right now?
            val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (s == null) rows.add(Triple("Motion sensor", false, "Is phone me accelerometer nahi mila"))
            else {
                var n = 0
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) { n++ }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME)
                SystemClock.sleep(2000)
                sm.unregisterListener(l)
                rows.add(Triple("Motion sensor chalu", n >= 20, "$n readings 2 second me" + if (n < 20) " (kam: phone sensor rok raha ho sakta hai)" else ""))
            }
            // 3) guard + permissions + background
            val svc = GuardService.instance
            rows.add(Triple("Guard service chal rahi hai", !before.guard || svc != null, if (!before.guard) "Guard OFF hai (aapne band rakha)" else if (svc != null) "haan" else "NAHI: Home par 'Guard dobara chalu karein' dabayein"))
            rows.add(Triple("Jhatka ya Park mode chalu", before.snatch || before.park, if (before.snatch || before.park) "haan" else "dono OFF: motion guard band hai (Settings me ON karein)"))
            val admin = try { app.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(app, GuardAdminReceiver::class.java)) } catch (_: Exception) { false }
            rows.add(Triple("Device Admin (screen lock + galat password)", admin, if (admin) "haan" else "nahi: bina iske lock nahi lagega"))
            val cam = app.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            rows.add(Triple("Camera ijazat", cam, if (cam) "haan" else "nahi: photo nahi aayegi"))
            rows.add(Triple("Guard app se chalu (lock-screen photo)", GuardService.cameraCapable || !before.guard, if (GuardService.cameraCapable) "haan" else "nahi: app kholkar Guard dobara ON karein"))
            val loc = LocationFetcher.hasPermission(app)
            rows.add(Triple("Location ijazat", loc, if (loc) "haan" else "nahi"))
            val bgLoc = app.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            rows.add(Triple("Location 'Hamesha allow'", bgLoc, if (bgLoc) "haan" else "nahi: screen off me location kam mil sakti hai"))
            val batt = try { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) } catch (_: Exception) { false }
            rows.add(Triple("Battery restriction se bahar", batt, if (batt) "haan" else "nahi: Vivo/OriginOS me 'No restriction' + Autostart ON karein"))
            val secure = try { app.getSystemService(KeyguardManager::class.java).isDeviceSecure } catch (_: Exception) { false }
            rows.add(Triple("Screen lock (PIN/pattern) laga hai", secure, if (secure) "haan" else "nahi: bina lock ke galat password photo nahi banti"))
            rows.add(Triple("Telegram bot", try { com.aurax.guard.telegram.TelegramConfig.configured(app) } catch (_: Exception) { false }, "alerts ke liye token + Chat ID chahiye"))
            // 4) nothing changed
            val after = snap()
            rows.add(Triple("Thief Protection test ke baad bhi waisa hi", before == after, if (before == after) "haan, koi setting nahi badli" else "KUCH BADLA: ye nahi hona chahiye"))
            val okN = rows.count { it.second }
            val sum = "$okN/${rows.size} theek. " + hint
            Prefs.log(app, "Test Mode chala: $okN/${rows.size} theek")
            ui.post {
                if (isDestroyed) return@post
                busy = false; report = rows; summary = sum; render()
            }
        }
    }

    // ---- live shake check (10 s, foreground only, nothing is triggered) ----
    private fun startShake() {
        if (shaking) return
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lin = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val s = lin ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        linear = lin != null
        val p = SensitivityProfile.forLevel(Prefs.sensitivity(this), Prefs.parkOn(this))
        liveDetector = SnatchDetector(p.threshold, 0, 1_000, 0, p.minSamples, p.minDurationMs, p.dipToleranceMs,
            if (SmartPrefs.fastPull(this) && !Prefs.parkOn(this)) p.hardThreshold else Float.MAX_VALUE, p.followThreshold)
        peak = 0f; wouldTrigger = false; shaking = true; java.util.Arrays.fill(grav, 0f)
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        ui.postDelayed({ stopShake(); render() }, 10_000)
        render()
    }

    private fun stopShake() {
        if (!shaking) return
        shaking = false
        try { (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this) } catch (_: Exception) {}
    }

    override fun onSensorChanged(e: SensorEvent) {
        val v = e.values
        val mag = if (linear) sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) else {
            for (i in 0..2) grav[i] = 0.9f * grav[i] + 0.1f * v[i]
            sqrt((v[0] - grav[0]) * (v[0] - grav[0]) + (v[1] - grav[1]) * (v[1] - grav[1]) + (v[2] - grav[2]) * (v[2] - grav[2]))
        }
        if (mag > peak) peak = mag
        if (liveDetector?.onSample(mag, SystemClock.elapsedRealtime()) == true) wouldTrigger = true
        val now = SystemClock.elapsedRealtime()
        if (now - lastUi > 250) { // throttled: 4 updates per second at most
            lastUi = now
            liveText = "Abhi ka sabse zor: %.1f m/s2 | Is jhatke par guard %s".format(peak, if (wouldTrigger) "BAJTA (haan)" else "nahi bajta")
            ui.post { if (shaking && !isDestroyed) render() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "Test Mode"))
        body.addView(Ui.dimText(c, "Safe jaanch: asli lock, siren, alert ya file delete kuch nahi hota. Aapki guard settings ko chheda nahi jata."))
        val top = Ui.card(c, Ui.teal, 14)
        top.addView(Ui.text(c, if (busy) "Chal raha hai" else "Ready", 15f, bold = true))
        top.addView(Ui.dimText(c, summary))
        if (!busy) top.addView(Ui.button(c, "Test chalayein (jhatka simulation + guard check)", Ui.Btn.PRIMARY, 10) { runTest() })
        body.addView(top)
        val live = Ui.card(c, null, 12)
        live.addView(Ui.text(c, "Live jhatka check (10 sec)", 15f, bold = true))
        live.addView(Ui.dimText(c, "Phone ko haath me lekar chaliye, uthaiye ya zor se kheenchiye. Dekhiye kaun sa movement guard ko bajata (sirf screen par dikhta hai)."))
        if (shaking) live.addView(Ui.text(c, liveText.ifEmpty { "Hilaiye..." }, 14f, bold = true, color = if (wouldTrigger) Ui.amber else Ui.emerald))
        else live.addView(Ui.button(c, "10 sec ka live check shuru karein", Ui.Btn.GHOST, 8) { startShake() })
        body.addView(live)
        report.forEach { (title, ok, note) ->
            val card = Ui.card(c, if (ok) Ui.emerald else Ui.amber, 8, drops = 0)
            card.addView(Ui.badge(c, if (ok) "OK" else "Dhyan dein", if (ok) Ui.emerald else Ui.amber))
            card.addView(Ui.text(c, title, 14f, bold = true).apply { setPadding(0, Ui.dp(c, 6), 0, 0) })
            card.addView(Ui.dimText(c, note))
            body.addView(card)
        }
        body.addView(Ui.dimText(c, "Simulation made-up movement par chalti hai. Asli phone par jeb/bhid/chheen ka final test aap khud karke dekhein. Screen band hone par sensor ka jawab ye test nahi dikha sakta."))
    }
}
