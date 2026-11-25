package com.aurax.guard.smart

import android.content.Context
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs

data class Mode(val key: String, val title: String, val sens: Int, val grace: Int, val park: Boolean, val siren: Boolean, val stealth: Boolean, val desc: String) {
    companion object {
        val HOME = Mode("home", "Ghar", 2, 15, false, true, false, "Kam sensitivity, lamba grace: ghar me aaram se rakhne/uthane par galat alert nahi.")
        val OUTSIDE = Mode("outside", "Bahar", 1, 10, false, true, false, "Madhyam sensitivity: bheed, bus ya market me chheenne par lock.")
        val NIGHT = Mode("night", "Raat (table par)", 1, 10, true, true, false, "Phone table par: haath lagte hi alarm (Park mode).")
        val SILENT = Mode("silent", "Silent guard", 1, 10, false, false, true, "Bina awaaz: sirf lock + alert aur photo. Chor ko pata nahi chalega.")
        val ALL = listOf(HOME, OUTSIDE, NIGHT, SILENT)
        fun of(key: String?) = ALL.firstOrNull { it.key == key }
    }
}

/** Applies a mode as ONE tap: it only sets ordinary settings that you can still change one by one. */
object ModeProfiles {
    fun apply(ctx: Context, m: Mode) {
        Prefs.setSensitivity(ctx, m.sens)
        Prefs.setGraceSec(ctx, m.grace)
        Prefs.setParkOn(ctx, GuardRules.parkAfterMode(Prefs.parkOn(ctx), m.park))
        if (m.park && !Prefs.guardOn(ctx)) { Prefs.setGuardOn(ctx, true); try { GuardService.start(ctx, false) } catch (_: Exception) {} }
        Prefs.setSirenOn(ctx, m.siren)
        SmartPrefs.setStealth(ctx, m.stealth)
        SmartPrefs.setMode(ctx, m.key)
        Prefs.log(ctx, "Mode lagaya: ${m.title}")
        try { GuardService.instance?.reloadMotion() } catch (_: Exception) {}
    }
}
