package com.aurax.guard

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock
import kotlin.math.sqrt

/** Watches the motion sensor and locks the screen if the phone is jerked away. */
class SnatchService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var detector: SnatchDetector
    private val gravity = FloatArray(3)
    private var usingLinear = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(
                12,
                Notif.build(this, "Chori se bachav chalu hai"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            Prefs.log(this, "Snatch service foreground nahi hui: ${e.message}")
        }
        sensors = getSystemService(SensorManager::class.java)
        detector = SnatchDetector(
            threshold = Prefs.snatchThresholds[Prefs.sensitivity(this)],
            armDelayMs = 5_000,
            cooldownMs = 5_000,
            startMs = SystemClock.elapsedRealtime(),
        )
        sensors.unregisterListener(this)
        val linear = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Prefs.log(this, "Is phone me motion sensor nahi hai")
            stopSelf()
            return START_NOT_STICKY
        }
        usingLinear = linear != null
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        Prefs.log(this, "Snatch detection chalu (5 second baad active)")
        return START_STICKY
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
        if (detector.onSample(mag, SystemClock.elapsedRealtime())) lockScreen()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lockScreen() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, GuardAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            Prefs.log(this, "Jhatka pakda gaya: screen lock kar di")
        } else {
            Prefs.log(this, "Jhatka pakda gaya par Device Admin chalu nahi, lock nahi hui")
        }
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        super.onDestroy()
    }
}
