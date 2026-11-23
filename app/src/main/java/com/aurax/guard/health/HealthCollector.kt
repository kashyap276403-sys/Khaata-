package com.aurax.guard.health

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import com.aurax.guard.AudioClip
import com.aurax.guard.EventStore
import com.aurax.guard.GuardAdminReceiver
import com.aurax.guard.LocationFetcher
import com.aurax.guard.Prefs
import com.aurax.guard.telegram.TelegramConfig
import com.aurax.guard.telegram.TelegramDispatcher
import com.aurax.guard.telegram.TelegramRules

/** Reads the real phone state into [HealthInputs]. Each lookup is guarded: a failing check just shows as "not ok", never a crash. */
object HealthCollector {
    private inline fun <T> safe(default: T, f: () -> T): T = try { f() } catch (_: Exception) { default }

    fun collect(c: Context): HealthInputs {
        val since = TelegramConfig.sinceMs(c)
        val all = safe(emptyList()) { EventStore.readAll(c) }
        val tokenOk = safe(false) { TelegramConfig.token(c)?.let { TelegramRules.isValidToken(it) } ?: false }
        return HealthInputs(
            tokenSaved = TelegramConfig.hasToken(c),
            tokenFormatOk = tokenOk,
            chatSaved = TelegramConfig.chatId(c).isNotBlank(),
            chatFormatOk = TelegramRules.isValidChatId(TelegramConfig.chatId(c)),
            telegramEnabled = TelegramConfig.enabled(c),
            deviceLabel = TelegramConfig.label(c),
            labelIsDefault = TelegramConfig.rawLabel(c).isBlank(),
            pending = all.count { TelegramRules.isWaiting(it, since) },
            failed = all.count { TelegramRules.isFailed(it, since) },
            lastTelegramStatus = TelegramConfig.status(c),
            online = safe(true) { TelegramDispatcher.isOnline(c) },
            camera = c.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            location = LocationFetcher.hasPermission(c),
            notifications = safe(true) { c.getSystemService(NotificationManager::class.java).areNotificationsEnabled() },
            deviceAdmin = safe(false) { c.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(c, GuardAdminReceiver::class.java)) },
            batteryUnrestricted = safe(false) { c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
            audioWanted = Prefs.audioOn(c),
            micPermission = AudioClip.hasPermission(c),
            guardOn = Prefs.guardOn(c),
            smartOn = Prefs.smartOn(c),
            autoTune = Prefs.autoTune(c),
            quietOn = Prefs.snoozed(c),
        )
    }
}
