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
    private var chargerReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromUi = intent?.getBooleanExtra(EXTRA_FROM_UI, false) ?: false
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        startAsForeground(withCamera = fromUi && camOk)

        instance = this
        sensors = getSystemService(SensorManager::class.java)
        sensors.unregisterListener(this)
        unregisterCharger()

        if (Prefs.snatchLockOn(this)) startMotion()
        if (Prefs.chargerAlarmOn(this)) registerCharger()
        Prefs.log(
            this,
            "Guard chalu (camera ${if (cameraCapable) "taiyaar" else "background me nahi"})",
        )
        return START_STICKY
    }

    private fun startAsForeground(withCamera: Boolean) {
        val note = Notif.build(this, "Chori se bachav chalu hai")
        val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (withCamera) {
            try {
                startForeground(12, note, special or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                cameraCapable = true
                return
            } catch (e: Exception) {
                Prefs.log(this, "Camera type foreground nahi mila: ${e.message}")
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
        detector = SnatchDetector(
            threshold = Prefs.snatchThresholds[Prefs.sensitivity(this)],
            armDelayMs = 5_000,
            cooldownMs = 8_000,
            startMs = SystemClock.elapsedRealtime(),
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
        if (detector?.onSample(mag, SystemClock.elapsedRealtime()) == true) {
            trigger("Jhatka pakda gaya")
        }
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
        if (instance === this) {
            instance = null
            cameraCapable = false
        }
        super.onDestroy()
    }
}
