package com.aurax.guard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gets the phone's position using only the Android location manager
 * (GPS / network). No Google Play services, no internet needed for GPS.
 */
object LocationFetcher {
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float)

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Calls [cb] exactly once, on the main thread, within about [timeoutMs]. */
    @SuppressLint("MissingPermission")
    fun fetch(ctx: Context, timeoutMs: Long = 12_000, cb: (Fix?, String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (!hasPermission(ctx)) {
            main.post { cb(null, "Location ijazat nahi mili") }
            return
        }
        val lm = ctx.getSystemService(LocationManager::class.java)
        val done = AtomicBoolean(false)
        val cancel = CancellationSignal()
        val listeners = mutableListOf<LocationListener>()

        fun finish(fix: Fix?, note: String) {
            if (!done.compareAndSet(false, true)) return
            cancel.cancel()
            listeners.forEach { try { lm.removeUpdates(it) } catch (_: Exception) {} }
            main.removeCallbacksAndMessages(null)
            cb(fix, note)
        }

        fun lastKnown(): Pair<Location, Long>? {
            var best: Location? = null
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
                if (best == null || l.time > best.time) best = l
            }
            val b = best ?: return null
            return b to ((System.currentTimeMillis() - b.time) / 60_000)
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }

        if (providers.isEmpty()) {
            val last = lastKnown()
            main.post {
                if (last != null) {
                    finish(Fix(last.first.latitude, last.first.longitude, last.first.accuracy),
                        "Phone ki Location band thi; aakhri location (${last.second} min pehle)")
                } else {
                    finish(null, "Phone ki Location band hai")
                }
            }
            return
        }

        main.postDelayed({
            val last = lastKnown()
            if (last != null) {
                finish(Fix(last.first.latitude, last.first.longitude, last.first.accuracy),
                    "Live fix nahi mila; aakhri location (${last.second} min pehle)")
            } else {
                finish(null, "Location nahi mili (andar ya signal kam)")
            }
        }, timeoutMs)

        for (p in providers) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(p, cancel, ctx.mainExecutor) { loc ->
                        if (loc != null) finish(Fix(loc.latitude, loc.longitude, loc.accuracy), "Live location (${providerName(p)})")
                    }
                } else {
                    val l = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            finish(Fix(loc.latitude, loc.longitude, loc.accuracy), "Live location (${providerName(p)})")
                        }
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                        @Deprecated("Needed on Android 10")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    listeners.add(l)
                    lm.requestSingleUpdate(p, l, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // Try the other provider / fall back at timeout.
            }
        }
    }

    private fun providerName(p: String) = if (p == LocationManager.GPS_PROVIDER) "GPS" else "Network"
}
