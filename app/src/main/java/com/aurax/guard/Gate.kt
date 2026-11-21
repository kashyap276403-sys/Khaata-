package com.aurax.guard

import android.content.Context
import android.os.SystemClock

/** Remembers that the correct app PIN was entered, for 5 minutes. */
object Gate {
    private const val GRACE_MS = 5 * 60 * 1000L
    @Volatile private var openUntil = 0L

    fun isOpen(c: Context): Boolean =
        !Prefs.hasPin(c) || SystemClock.elapsedRealtime() < openUntil

    fun open() {
        openUntil = SystemClock.elapsedRealtime() + GRACE_MS
    }

    fun close() {
        openUntil = 0L
    }
}
