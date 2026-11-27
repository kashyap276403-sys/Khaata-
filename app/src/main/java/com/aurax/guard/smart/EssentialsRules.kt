package com.aurax.guard.smart

/** The four things worth seeing at a glance on Home. Pure rules, unit tested. */
object EssentialsRules {
    fun items(
        guardOn: Boolean, running: Boolean, lockdown: Boolean,
        telegramConfigured: Boolean, telegramEnabled: Boolean, remoteOn: Boolean,
        wrongAlertOn: Boolean, camera: Boolean, deviceAdmin: Boolean,
        battery: Int, traceOn: Boolean, saver: Boolean,
    ): List<StatusItem> {
        val g = when {
            !guardOn -> StatusItem("Guard", "Off", StatusKind.OFF)
            !running -> StatusItem("Guard", "Ruka hua", StatusKind.ATTENTION)
            lockdown -> StatusItem("Guard", "Lockdown ON", StatusKind.ACTIVE)
            else -> StatusItem("Guard", "Active", StatusKind.ACTIVE)
        }
        val t = when {
            !telegramConfigured -> StatusItem("Telegram bot", "Setup baaki", StatusKind.ATTENTION)
            !telegramEnabled -> StatusItem("Telegram bot", "Off", StatusKind.OFF)
            remoteOn -> StatusItem("Telegram bot", "Ready + remote", StatusKind.READY)
            else -> StatusItem("Telegram bot", "Ready", StatusKind.READY)
        }
        val w = when {
            !wrongAlertOn -> StatusItem("Galat password photo", "Off", StatusKind.OFF)
            !camera || !deviceAdmin -> StatusItem("Galat password photo", if (!deviceAdmin) "Device Admin chahiye" else "Camera ijazat chahiye", StatusKind.ATTENTION)
            else -> StatusItem("Galat password photo", "Ready", StatusKind.READY)
        }
        val b = (if (battery >= 0) "$battery%" else "--") + " | " + (if (traceOn) "survival ON" else "survival OFF") + (if (saver) " | saver" else "")
        return listOf(g, t, w, StatusItem("Battery", b, if (traceOn) StatusKind.ACTIVE else StatusKind.OFF))
    }
}
