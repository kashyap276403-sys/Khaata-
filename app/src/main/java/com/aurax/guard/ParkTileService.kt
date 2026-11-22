package com.aurax.guard

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: switch "Haath mat lagao mode" on/off from the pull-down panel. */
class ParkTileService : TileService() {
    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        unlockAndRun {
            val on = !Prefs.parkOn(this)
            Prefs.setParkOn(this, on)
            if (on) Prefs.setGuardOn(this, true)
            try {
                if (Prefs.guardOn(this)) GuardService.start(this, false)
            } catch (e: Exception) {
                Prefs.log(this, "Tile se guard shuru nahi hua: ${e.message}")
            }
            Prefs.log(this, if (on) "Tile: Haath mat lagao mode chalu" else "Tile: Haath mat lagao mode band")
            update()
        }
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Prefs.parkOn(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Haath mat lagao"
        t.updateTile()
    }
}
