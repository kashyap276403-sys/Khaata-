package com.aurax.guard

import com.aurax.guard.smart.SmartPrefs
import android.Manifest
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * The always-on guard. It
 *  - watches the motion sensor and locks / sounds the siren on a snatch,
 *  - reacts when the charger is pulled out (optional),
 *  - keeps camera access alive so intruder photos work even when Android
 *    blocks camera use from the background (only if it was started from the
 *    app screen with camera permission).
 */
class GuardService : Service(), SensorEventListener {
    companion object {
        const val EXTRA_FROM_UI = "from_ui"

        @Volatile var instance: GuardService? = null
        @Volatile var cameraCapable = false
        @Volatile var micCapable = false

        fun start(context: Context, fromUi: Boolean) {
            val i = Intent(context, GuardService::class.java).putExtra(EXTRA_FROM_UI, fromUi)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }

    private lateinit var sensors: SensorManager
    private var detector: SnatchDetector? = null
    private val gravity = FloatArray(3)
    private var usingLinear = false
    private var motionReason = "Jhatka pakda gaya"
    private var chargerReceiver: BroadcastReceiver? = null
    private var simReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var levelReceiver: BroadcastReceiver? = null
    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastNetKick = 0L
    @Volatile private var plugged = false
    @Volatile private var lastSampleMs = 0L

    /** Watchdog: if motion should be running but the phone stopped delivering sensor data, register it again. */
    fun ensureSensor() {
        try {
            val on = Prefs.snatchLockOn(this) || Prefs.parkOn(this)
            if (on && com.aurax.guard.smart.GuardRules.sensorStale(true, lastSampleMs, SystemClock.elapsedRealtime())) {
                sensors.unregisterListener(this)
                startMotion()
                Prefs.log(this, "Motion sensor chup tha, dobara chalu kiya")
            }
        } catch (_: Exception) {
        }
    }
    @Volatile private var quietUntilElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromUi = intent?.getBooleanExtra(EXTRA_FROM_UI, false) ?: false
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locOk = LocationFetcher.hasPermission(this)
        val micOk = AudioClip.hasPermission(this) && Prefs.audioOn(this)
        startAsForeground(withCamera = fromUi && camOk, withLocation = fromUi && locOk, withMic = fromUi && micOk)

        instance = this
        sensors = getSystemService(SensorManager::class.java)
        sensors.unregisterListener(this)
        unregisterCharger()

        SelfieCapture.purgeOld(this, Prefs.retentionDays(this))
        if (Prefs.snatchLockOn(this) || Prefs.parkOn(this)) startMotion()
        if (Prefs.chargerAlarmOn(this)) registerCharger()
        registerSim()
        registerLevel()
        registerNet()
        com.aurax.guard.smart.SmartScheduler.arm(this)
        registerScreen()
        registerBattery()
        SimWatcher.check(this)
        Prefs.log(
            this,
            "Guard chalu (camera ${if (cameraCapable) "taiyaar" else "background me nahi"})",
        )
        return START_STICKY
    }

    private fun startAsForeground(withCamera: Boolean, withLocation: Boolean, withMic: Boolean = false) {
        val note = Notif.build(this, "Smart Protection chalu hai")
        val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        micCapable = false
        if (withCamera || withLocation || withMic) {
            var type = special
            if (withMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (withLocation) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            try {
                startForeground(12, note, type)
                cameraCapable = withCamera
                micCapable = withMic
                return
            } catch (e: Exception) {
                Prefs.log(this, "Camera/location type foreground nahi mila: ${e.message}")
            }
        }
        cameraCapable = false
        try {
            startForeground(12, note, special)
        } catch (e: Exception) {
            Prefs.log(this, "Guard foreground nahi hui: ${e.message}")
        }
    }

    /** Re-reads sensitivity/grace without restarting the service (keeps camera/mic access). */
    fun reloadMotion() {
        try {
            sensors.unregisterListener(this)
            detector = null
            if (Prefs.snatchLockOn(this) || Prefs.parkOn(this)) startMotion()
        } catch (e: Exception) {
            Prefs.log(this, "Motion reload nahi hua (${e.javaClass.simpleName})")
        }
    }

    private fun startMotion() {
        val park = Prefs.parkOn(this)
        motionReason = if (park) "Phone ko haath lagaya gaya" else "Jhatka pakda gaya"
        val prof = SensitivityProfile.forLevel(Prefs.sensitivity(this), park)
        detector = SnatchDetector(
            threshold = prof.threshold,
            armDelayMs = prof.armDelayMs,
            cooldownMs = prof.cooldownMs,
            startMs = SystemClock.elapsedRealtime(),
            minSamples = prof.minSamples,
            minDurationMs = prof.minDurationMs,
            dipToleranceMs = prof.dipToleranceMs,
            hardThreshold = if (SmartPrefs.fastPull(this) && !park) prof.hardThreshold else Float.MAX_VALUE,
            followThreshold = prof.followThreshold,
        )
        lastSampleMs = SystemClock.elapsedRealtime()
        val linear = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Prefs.log(this, "Is phone me motion sensor nahi hai")
            return
        }
        usingLinear = linear != null
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun registerCharger() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Prefs.snoozed(context)) return
                trigger("Charger nikala gaya")
            }
        }
        registerReceiver(r, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
        chargerReceiver = r
    }

    /** Screen on/off/unlock = the owner is handling the phone. Ignore motion for a few seconds afterwards. */
    private fun registerScreen() {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                quietUntilElapsed = SystemClock.elapsedRealtime() + Prefs.graceSec(context) * 1000L
                detector?.reset()
                try { if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) Thread { com.aurax.guard.remote.RemoteControl.poll(context.applicationContext) }.start() } catch (_: Exception) {}
                // some phones quietly drop sensor listeners around screen on/off: put it back so protection stays alive
                try { if (Prefs.snatchLockOn(context) || Prefs.parkOn(context)) { sensors.unregisterListener(this@GuardService); startMotion() } } catch (_: Exception) {}
            }
        }
        try {
            registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            })
            screenReceiver = r
        } catch (_: Exception) {
        }
    }

    private fun unregisterScreen() {
        screenReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenReceiver = null
    }

    /** Phone nearly dead: send its last location while it still can (opt-in, at most every 6 hours). */
    private fun registerBattery() {
        if (batteryReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    if (!Prefs.batteryAlertOn(context)) return
                    val now = System.currentTimeMillis()
                    if (now - Prefs.batteryLastMs(context) < 6 * 3600 * 1000L) return
                    Prefs.setBatteryLastMs(context, now)
                    val pct = DeviceInfo.read(context).batteryPct
                    EventRecorder.trigger(context, "Battery bahut kam ($pct%): aakhri location", "Battery low", EventRecorder.PhotoMode.NONE)
                } catch (_: Exception) {
                }
            }
        }
        try {
            registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_LOW))
            batteryReceiver = r
        } catch (_: Exception) {
        }
    }

    /** Tracks charging state (for "ignore while charging") and feeds Emergency Trace. */
    private fun registerLevel() {
        if (levelReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    val st = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
                    if (SmartPrefs.traceOn(context) || SmartPrefs.state(context).active) {
                        val chg = plugged || st == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        Thread { try { com.aurax.guard.smart.TraceEngine.onBattery(context, pct, chg) } catch (_: Exception) {} }.start()
                    }
                } catch (_: Exception) {
                }
            }
        }
        try {
            registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            levelReceiver = r
        } catch (_: Exception) {
        }
    }

    /** Internet came back: send whatever waited in the offline queue (one kick per 10 s at most; the dispatcher never double-sends). */
    private fun registerNet() {
        if (netCallback != null) return
        try {
            val cm = getSystemService(android.net.ConnectivityManager::class.java)
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNetKick < 10_000L) return
                    lastNetKick = now
                    try { com.aurax.guard.telegram.TelegramDispatcher.kick(this@GuardService) } catch (_: Exception) {}
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    private fun unregisterNet() {
        try { netCallback?.let { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(it) } } catch (_: Exception) {}
        netCallback = null
    }

    private fun unregisterLevel() {
        levelReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        levelReceiver = null
    }

    private fun unregisterBattery() {
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
    }

    private fun registerSim() {
        if (simReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                SimWatcher.check(context)
            }
        }
        try {
            registerReceiver(r, IntentFilter("android.intent.action.SIM_STATE_CHANGED"))
            simReceiver = r
        } catch (_: Exception) {
        }
    }

    /** Swiped away from recents: ask the watchdog to check again in a few seconds (the guard itself keeps running). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try { com.aurax.guard.smart.SmartScheduler.armAt(this, System.currentTimeMillis() + 5_000L) } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    private fun unregisterSim() {
        simReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        simReceiver = null
    }

    private fun unregisterCharger() {
        chargerReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        chargerReceiver = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        // An exception here would kill the whole service (and with it the protection): never let one escape.
        try { handleSensor(e) } catch (t: Throwable) { try { detector?.reset() } catch (_: Throwable) {} }
    }

    private fun handleSensor(e: SensorEvent) {
        lastSampleMs = SystemClock.elapsedRealtime()
        val v = e.values
        val mag = if (usingLinear) {
            sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        } else {
            // Plain accelerometer includes gravity: remove it with a slow filter.
            for (i in 0..2) gravity[i] = 0.9f * gravity[i] + 0.1f * v[i]
            val x = v[0] - gravity[0]
            val y = v[1] - gravity[1]
            val z = v[2] - gravity[2]
            sqrt(x * x + y * y + z * z)
        }
        val now = SystemClock.elapsedRealtime()
        // Quiet time, a recent screen on/off/unlock (picking up or putting down), or phone open and in use: not a snatch.
        if (Prefs.snoozed(this) || now < quietUntilElapsed || (plugged && SmartPrefs.ignoreCharging(this)) ||
            (Prefs.ignoreWhileUnlocked(this) && !phoneLocked())
        ) {
            detector?.reset()
            return
        }
        if (detector?.onSample(mag, now) == true) {
            trigger(motionReason)
        }
    }

    private fun phoneLocked(): Boolean = try {
        getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked ||
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    } catch (_: Exception) {
        true // unsure: stay protective
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Lock the screen and (if enabled) sound the siren. */
    fun trigger(reason: String) {
        try { triggerInner(reason) } catch (t: Throwable) { Prefs.log(this, "Trigger me dikkat (${t.javaClass.simpleName}); protection chalu hai") }
    }

    private fun triggerInner(reason: String) {
        var locked = false
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(this, GuardAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                locked = true
            }
        } catch (e: Exception) {
            Prefs.log(this, "Screen lock nahi hui (${e.javaClass.simpleName})")
        }
        val siren = try { Prefs.sirenOn(this) && !SmartPrefs.stealth(this) && Siren.start(this) } catch (e: Exception) { false }
        Prefs.log(
            this,
            "$reason: " + (if (locked) "screen lock kar di" else "Device Admin band, lock nahi hui") +
                (if (siren) ", siren chalu" else ""),
        )
        // Also write photo-less metadata (GPS, battery, network, time) to the local queue.
        try {
            EventRecorder.trigger(this, reason, reason, EventRecorder.PhotoMode.NONE)
        } catch (e: Exception) {
            Prefs.log(this, "Alert queue me nahi gaya (${e.javaClass.simpleName})")
        }
    }

    /** Called when a wrong password was entered. */
    fun takeSelfie(label: String, onDone: (Boolean) -> Unit) {
        SelfieCapture.run(this, label) { ok, msg ->
            Prefs.log(this, msg)
            onDone(ok)
        }
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        unregisterCharger()
        unregisterSim()
        unregisterScreen()
        unregisterBattery()
        unregisterLevel()
        unregisterNet()
        if (instance === this) {
            instance = null
            cameraCapable = false
            micCapable = false
        }
        super.onDestroy()
    }
}
