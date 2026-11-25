package com.aurax.guard.smart

import android.content.Context
import com.aurax.guard.DeviceInfo

/**
 * Battery Saver: switched on by hand, or automatically when the battery is low and not charging.
 * It only trims EXTRAS (daily digest, heartbeat, optional AI line, fewer boost snapshots).
 * Essential things never pause: motion guard, SIM/wrong-password alerts, Emergency Trace location,
 * emergency photo and the Telegram queue.
 */
object BatterySaver {
    const val BOOST_MAX_SAVER = 2

    fun active(manual: Boolean, auto: Boolean, autoPct: Int, pct: Int, charging: Boolean): Boolean =
        manual || (auto && pct in 0..autoPct && !charging)

    fun active(ctx: Context): Boolean = try {
        val m = DeviceInfo.read(ctx)
        active(SmartPrefs.saverManual(ctx), SmartPrefs.saverAuto(ctx), SmartPrefs.saverPct(ctx), m.batteryPct, m.charging)
    } catch (_: Exception) {
        SmartPrefs.saverManual(ctx)
    }

    fun describe(manual: Boolean, auto: Boolean, autoPct: Int, isActive: Boolean): String = when {
        manual -> "Chalu (haath se)"
        isActive -> "Chalu (battery $autoPct% se kam)"
        auto -> "Tayyar: battery $autoPct% se kam hote hi chalu"
        else -> "Band"
    }
}
