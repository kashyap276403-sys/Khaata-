package com.aurax.guard.smart

/** Small pure rules that keep Thief Protection from ever switching itself off. */
object GuardRules {
    const val WATCHDOG_MS = 15 * 60_000L

    /** The switch says ON but the service is not running (killed by the phone's battery manager): bring it back. */
    fun shouldRestart(guardOn: Boolean, running: Boolean): Boolean = guardOn && !running

    /** A mode may turn Park ON but never switches an existing Park setting OFF behind your back. */
    fun parkAfterMode(currentPark: Boolean, modePark: Boolean): Boolean = currentPark || modePark

    /** Protection status word for Home. */
    fun status(guardOn: Boolean, running: Boolean, permsOk: Boolean): String =
        if (!guardOn) "Off" else if (!running) "Ruka hua" else if (permsOk) "Active" else "Adhoora"
}
