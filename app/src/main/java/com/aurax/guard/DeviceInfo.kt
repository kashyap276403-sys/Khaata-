package com.aurax.guard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings

/** Battery and network state at the moment of a trigger. */
object DeviceInfo {
    data class Meta(val batteryPct: Int, val charging: Boolean, val network: String)

    fun read(ctx: Context): Meta {
        var pct = -1
        var charging = false
        try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) pct = level * 100 / scale
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {
        }
        return Meta(pct, charging, network(ctx))
    }

    private fun network(ctx: Context): String {
        return try {
            val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val base = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
            val noInternet = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            base + (if (noInternet) " (internet nahi)" else "") + (if (airplane) ", Airplane mode chalu" else "")
        } catch (_: Exception) {
            "pata nahi"
        }
    }
}
