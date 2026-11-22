package com.aurax.guard.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * One-tap shortcuts into the phone's own settings. Every link tries a list of
 * options and falls back to something that exists, so a missing OEM screen
 * (OriginOS, Funtouch OS, Hello UI) never crashes the app.
 */
object SettingsLinks {
    fun open(ctx: Context, vararg options: Intent): Boolean {
        for (i in options) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // try the next option
            }
        }
        Toast.makeText(ctx, "Ye settings screen is phone par nahi khuli. Settings me haath se dhoondein.", Toast.LENGTH_LONG).show()
        return false
    }

    private fun pkgUri(pkg: String) = Uri.parse("package:$pkg")

    fun appInfo(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(pkg))

    fun appPermissions(pkg: String) =
        Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)

    fun overlay(pkg: String) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(pkg))
    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun deviceAdmin() = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
    fun security() = Intent(Settings.ACTION_SECURITY_SETTINGS)
    fun notificationListeners() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun batteryOptimization() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    fun uninstall(pkg: String) = Intent(Intent.ACTION_DELETE, pkgUri(pkg))
    fun internalStorage() = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    fun allApps() = Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)

    /** Best-effort Funtouch / OriginOS autostart screens (names differ between versions). */
    private fun vivo(pkg: String, cls: String) =
        Intent().setComponent(ComponentName(pkg, cls))

    fun autostartOptions(appPkg: String): Array<Intent> = arrayOf(
        vivo("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        vivo("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        vivo("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
        appInfo(appPkg),
    )

    /** Which group's most useful system screen to open for guidance. */
    fun forGroup(group: PermGroup, pkg: String): Array<Intent> = when (group) {
        PermGroup.OVERLAY -> arrayOf(overlay(pkg), appInfo(pkg))
        PermGroup.ACCESSIBILITY -> arrayOf(accessibility(), appInfo(pkg))
        PermGroup.DEVICE_ADMIN -> arrayOf(deviceAdmin(), security(), appInfo(pkg))
        PermGroup.NOTIFICATIONS -> arrayOf(notificationListeners(), appInfo(pkg))
        else -> arrayOf(appPermissions(pkg), appInfo(pkg))
    }
}
