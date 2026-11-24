package com.aurax.guard

import android.app.Application
import com.aurax.guard.ui.Ui

/** Loads the chosen accent colour once at start, so every screen (and the service notifications' screens) agree. */
class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { Ui.loadAccent(this) } catch (_: Exception) {}
    }
}
