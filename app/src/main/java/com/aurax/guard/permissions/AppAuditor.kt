package com.aurax.guard.permissions

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Reads what every installed app can do. Read-only: it never changes anything.
 * If the phone hides some information, that part is simply treated as "no".
 */
object AppAuditor {
    private fun enabledPackages(ctx: Context, key: String): Set<String> = try {
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "")
            .split(':')
            .filter { it.isNotBlank() }
            .map { it.substringBefore('/') }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun activeAdmins(ctx: Context): Set<String> = try {
        ctx.getSystemService(DevicePolicyManager::class.java)
            .activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    @Suppress("DEPRECATION")
    private fun packages(pm: PackageManager): List<PackageInfo> = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    } catch (_: Exception) {
        emptyList()
    }

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (_: Exception) {
        null
    }

    private fun opAllowed(ops: AppOpsManager, op: String, uid: Int, pkg: String): Boolean? = try {
        when (ops.unsafeCheckOpNoThrow(op, uid, pkg)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> null // decided by the normal permission flag
            else -> false
        }
    } catch (_: Exception) {
        null
    }

    fun audit(ctx: Context): List<AppAssessment> {
        val pm = ctx.packageManager
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val accessibility = enabledPackages(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listeners = enabledPackages(ctx, "enabled_notification_listeners")
        val admins = activeAdmins(ctx)

        val out = mutableListOf<AppFacts>()
        for (pi in packages(pm)) {
            val ai = pi.applicationInfo ?: continue
            val pkg = pi.packageName
            val req = pi.requestedPermissions ?: emptyArray()
            val flags = pi.requestedPermissionsFlags ?: IntArray(0)
            val declared = req.toSet()
            val granted = HashSet<String>()
            for (i in req.indices) {
                if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted.add(req[i])
            }
            fun g(vararg p: String) = p.any { it in granted }

            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val uid = ai.uid
            val overlay = "android.permission.SYSTEM_ALERT_WINDOW" in declared &&
                (opAllowed(ops, "android:system_alert_window", uid, pkg)
                    ?: g("android.permission.SYSTEM_ALERT_WINDOW"))
            val allFiles = "android.permission.MANAGE_EXTERNAL_STORAGE" in declared &&
                (opAllowed(ops, "android:manage_external_storage", uid, pkg)
                    ?: g("android.permission.MANAGE_EXTERNAL_STORAGE"))
            val install = "android.permission.REQUEST_INSTALL_PACKAGES" in declared &&
                (opAllowed(ops, "android:request_install_packages", uid, pkg) ?: false)

            out.add(
                AppFacts(
                    packageName = pkg,
                    label = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pkg },
                    isSystem = isSystem,
                    isSelf = pkg == ctx.packageName,
                    installer = InstallerKind.classify(installerOf(pm, pkg), isSystem),
                    camera = g("android.permission.CAMERA"),
                    microphone = g("android.permission.RECORD_AUDIO"),
                    location = g("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
                    backgroundLocation = g("android.permission.ACCESS_BACKGROUND_LOCATION"),
                    overlay = overlay,
                    accessibility = pkg in accessibility,
                    deviceAdmin = pkg in admins,
                    notificationListener = pkg in listeners,
                    sms = g("android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
                        "android.permission.SEND_SMS", "android.permission.RECEIVE_MMS"),
                    call = g("android.permission.CALL_PHONE", "android.permission.ANSWER_PHONE_CALLS"),
                    callLog = g("android.permission.READ_CALL_LOG", "android.permission.PROCESS_OUTGOING_CALLS"),
                    contacts = g("android.permission.READ_CONTACTS"),
                    allFiles = allFiles,
                    installPackages = install,
                )
            )
        }
        return RiskScorer.assessAll(out)
    }
}
