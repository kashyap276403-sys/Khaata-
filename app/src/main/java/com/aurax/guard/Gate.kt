package com.aurax.guard

import android.content.Context
import android.os.SystemClock

/** Remembers that the correct app PIN was entered, for the chosen number of minutes. */
object Gate {
    @Volatile private var openUntil = 0L

    fun isOpen(c: Context): Boolean =
        !Prefs.hasPin(c) || SystemClock.elapsedRealtime() < openUntil

    fun open(c: Context) {
        openUntil = SystemClock.elapsedRealtime() + Prefs.lockGraceMin(c) * 60_000L
    }

    fun close() {
        openUntil = 0L
    }
}
