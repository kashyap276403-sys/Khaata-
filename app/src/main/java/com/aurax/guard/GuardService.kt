package com.aurax.guard

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
        )
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
        if (Prefs.snoozed(this)) return
        // Phone is open and in use: handling it is normal, not a snatch.
        if (Prefs.ignoreWhileUnlocked(this) && !phoneLocked()) return
        if (detector?.onSample(mag, SystemClock.elapsedRealtime()) == true) {
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
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, GuardAdminReceiver::class.java)
        val locked = if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            true
        } else {
            false
        }
        val siren = Prefs.sirenOn(this) && Siren.start(this)
        Prefs.log(
            this,
            "$reason: " + (if (locked) "screen lock kar di" else "Device Admin band, lock nahi hui") +
                (if (siren) ", siren chalu" else ""),
        )
        // Also write photo-less metadata (GPS, battery, network, time) to the local queue.
        EventRecorder.trigger(this, reason, reason, EventRecorder.PhotoMode.NONE)
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
        if (instance === this) {
            instance = null
            cameraCapable = false
            micCapable = false
        }
        super.onDestroy()
    }
}
