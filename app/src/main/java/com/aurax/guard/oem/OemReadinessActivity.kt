package com.aurax.guard.oem

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.aurax.guard.AudioClip
import com.aurax.guard.GuardAdminReceiver
import com.aurax.guard.Gate
import com.aurax.guard.LocationFetcher
import com.aurax.guard.Prefs
import com.aurax.guard.permissions.SettingsLinks
import com.aurax.guard.ui.Ui

/** Shows what must be allowed so the guard keeps running on vivo / iQOO / Motorola phones. Only opens settings, changes nothing itself. */
class OemReadinessActivity : Activity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@OemReadinessActivity, 16), Ui.dp(this@OemReadinessActivity, 24), Ui.dp(this@OemReadinessActivity, 16), Ui.dp(this@OemReadinessActivity, 48))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(body) })
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) { finish(); return }
        render()
    }

    private fun sysProp(key: String): String = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as String
    } catch (_: Exception) {
        ""
    }

    private fun render() {
        val c = this
        body.removeAllViews()
        val osName = sysProp("ro.vivo.os.name")
        val osVer = sysProp("ro.vivo.os.version")
        val fam = OemRules.family(Build.MANUFACTURER ?: "", Build.BRAND ?: "", osName)
        body.addView(Ui.heading(c, "Phone readiness"))
        body.addView(Ui.dimText(c, "${Build.MANUFACTURER} ${Build.MODEL}  |  ${OemRules.skinLabel(fam, osName, osVer)}  |  Android ${Build.VERSION.RELEASE}"))

        val pm = getSystemService(PowerManager::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val checks = listOf(
            "Battery optimization se bahar" to (try { pm.isIgnoringBatteryOptimizations(packageName) } catch (_: Exception) { false }),
            "Device Admin ON (screen lock ke liye)" to (try { dpm.isAdminActive(ComponentName(c, GuardAdminReceiver::class.java)) } catch (_: Exception) { false }),
            "Notifications allowed" to (try { getSystemService(NotificationManager::class.java).areNotificationsEnabled() } catch (_: Exception) { true }),
            "Camera permission" to (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED),
            "Location permission" to LocationFetcher.hasPermission(c),
            "Microphone permission (audio clip ke liye)" to (AudioClip.hasPermission(c) || !Prefs.audioOn(c)),
        )
        val card = Ui.card(c, if (checks.all { it.second }) Ui.emerald else Ui.amber, 14)
        card.addView(Ui.text(c, OemRules.summary(checks), 16f, bold = true))
        checks.forEach { (name, ok) ->
            card.addView(Ui.text(c, (if (ok) "OK   " else "KARNA HAI   ") + name, 14f, color = if (ok) Ui.emerald else Ui.amber))
        }
        body.addView(card)

        val steps = Ui.card(c, null, 12)
        steps.addView(Ui.text(c, "${fam.title}: kya karein", 16f, bold = true))
        OemRules.steps(fam).forEachIndexed { i, s -> steps.addView(Ui.dimText(c, "${i + 1}. $s")) }
        steps.addView(Ui.dimText(c, "OEM screens ke naam update ke saath badal sakte hain. Agar button par seedhi screen na khule to app info khulegi."))
        body.addView(steps)

        val act = Ui.card(c, null, 12)
        act.addView(Ui.text(c, "Seedhe settings kholein", 16f, bold = true))
        act.addView(Ui.button(c, "Battery optimization", Ui.Btn.GHOST, 8) { SettingsLinks.open(c, SettingsLinks.batteryOptimization(), SettingsLinks.appInfo(packageName)) })
        if (fam == OemFamily.VIVO) {
            act.addView(Ui.button(c, "Autostart / background power", Ui.Btn.GHOST, 6) { SettingsLinks.open(c, *SettingsLinks.autostartOptions(packageName)) })
        }
        act.addView(Ui.button(c, "App info / permissions", Ui.Btn.GHOST, 6) { SettingsLinks.open(c, SettingsLinks.appInfo(packageName)) })
        act.addView(Ui.button(c, "Device admin", Ui.Btn.GHOST, 6) { SettingsLinks.open(c, SettingsLinks.deviceAdmin(), SettingsLinks.security()) })
        body.addView(act)
    }
}
