package com.aurax.guard.smart

enum class StatusKind { ACTIVE, READY, QUEUED, OFF, ATTENTION }
data class StatusItem(val title: String, val label: String, val kind: StatusKind)

/** The one-glance status strip on Home. Pure rules so the words stay consistent and tested. */
object StatusRules {
    fun items(
        guardOn: Boolean, telegramConfigured: Boolean, telegramEnabled: Boolean, queued: Int,
        traceOn: Boolean, traceActive: Boolean, saverActive: Boolean, permsOk: Boolean, running: Boolean = true, lockdown: Boolean = false,
    ): List<StatusItem> {
        val l = mutableListOf<StatusItem>()
        val ps = GuardRules.status(guardOn, running, permsOk)
        l.add(StatusItem("Protection", ps, when (ps) { "Off" -> StatusKind.OFF; "Active" -> StatusKind.ACTIVE; else -> StatusKind.ATTENTION }))
        l.add(StatusItem("Telegram", if (!telegramConfigured) "Setup baaki" else if (!telegramEnabled) "Off" else "Ready", if (!telegramConfigured) StatusKind.ATTENTION else if (!telegramEnabled) StatusKind.OFF else StatusKind.READY))
        l.add(StatusItem("Queue", if (queued > 0) "$queued queued" else "Khaali", if (queued > 0) StatusKind.QUEUED else StatusKind.READY))
        l.add(StatusItem("Emergency trace", if (!traceOn) "Off" else if (traceActive) "Active" else "Ready", if (!traceOn) StatusKind.OFF else if (traceActive) StatusKind.ACTIVE else StatusKind.READY))
        l.add(StatusItem("Battery saver", if (saverActive) "Active" else "Off", if (saverActive) StatusKind.ACTIVE else StatusKind.OFF))
        if (lockdown) l.add(StatusItem("Tactical Lockdown", "Active", StatusKind.ACTIVE))
        return l
    }
}
