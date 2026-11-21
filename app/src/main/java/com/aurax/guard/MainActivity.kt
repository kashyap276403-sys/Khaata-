package com.aurax.guard

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** One screen: big on/off, setup checklist, per-feature cards, event history. */
class MainActivity : Activity() {
    private val green = Color.rgb(80, 220, 160)
    private val red = Color.rgb(255, 110, 110)

    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var adminRow: TextView
    private lateinit var cameraRow: TextView
    private lateinit var notifRow: TextView
    private lateinit var photoInfo: TextView
    private lateinit var snoozeInfo: TextView
    private lateinit var events: TextView
    private val syncs = mutableListOf<() -> Unit>()

    private val admin get() = ComponentName(this, GuardAdminReceiver::class.java)
    private val dpm get() = getSystemService(DevicePolicyManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(40))
        }

        // ---- header + master switch ----
        root.addView(label("auraX Guard", 28f, bold = true))
        root.addView(label("Chori se bachav: photo, siren aur screen lock", 14f, dim = true))
        val hero = card(highlight = true)
        statusTitle = label("", 22f, bold = true)
        statusSub = label("", 13f, dim = true)
        hero.addView(statusTitle)
        hero.addView(statusSub)
        hero.addView(toggle("Suraksha chalu", { Prefs.guardOn(this) }) { on -> setGuard(on) })
        root.addView(hero)

        // ---- setup checklist ----
        val setup = card()
        setup.addView(title("Zaroori setup"))
        adminRow = label("", 15f)
        setup.addView(adminRow)
        setup.addView(button("Device Admin chalu karein") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Galat password pakadne aur screen lock karne ke liye zaroori hai. " +
                            "Jab tak ye chalu hai, koi is app ko aasani se uninstall nahi kar sakta.",
                    )
            )
        })
        cameraRow = label("", 15f)
        setup.addView(cameraRow)
        notifRow = label("", 15f)
        setup.addView(notifRow)
        setup.addView(button("Camera aur notification ijazat dein") { askPermissions() })
        setup.addView(button("Battery 'Unrestricted' karein") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(setup)

        // ---- wrong password ----
        val pw = card()
        pw.addView(title("Galat password par photo"))
        pw.addView(toggle("Intruder photo chalu", { Prefs.selfieOn(this) }) { Prefs.setSelfieOn(this, it) })
        pw.addView(label("Kitni galat koshish ke baad photo? (3 galat par siren bhi)", 13f, dim = true))
        pw.addView(radios(arrayOf("1", "2", "3"), Prefs.attemptsBeforePhoto(this) - 1) {
            Prefs.setAttemptsBeforePhoto(this, it + 1)
        })
        pw.addView(button("Photo ka test karein") { testSelfie() })
        photoInfo = label("", 13f, dim = true)
        pw.addView(photoInfo)
        pw.addView(button("Aakhri photo dekhein") { openLatestPhoto() })
        root.addView(pw)

        // ---- snatch / charger / siren ----
        val sn = card()
        sn.addView(title("Phone chheenne par"))
        sn.addView(toggle("Jhatka lagne par screen lock", { Prefs.snatchLockOn(this) }) {
            Prefs.setSnatchLockOn(this, it)
            restartGuardIfOn()
        })
        sn.addView(label("Sensitivity (Zyada = halke jhatke par bhi lock)", 13f, dim = true))
        sn.addView(radios(Prefs.snatchNames, Prefs.sensitivity(this)) {
            Prefs.setSensitivity(this, it)
            restartGuardIfOn()
        })
        sn.addView(toggle("Charger nikalte hi alarm aur lock", { Prefs.chargerAlarmOn(this) }) {
            Prefs.setChargerAlarmOn(this, it)
            restartGuardIfOn()
        })
        sn.addView(toggle("Siren bajao", { Prefs.sirenOn(this) }) { Prefs.setSirenOn(this, it) })
        sn.addView(button("Siren test (dobara dabane par band)") {
            if (Siren.isPlaying()) Siren.stop() else Siren.start(this, 10)
        })
        root.addView(sn)

        // ---- snooze ----
        val sz = card()
        sz.addView(title("Ghar par ho? Thodi der rukein"))
        snoozeInfo = label("", 13f, dim = true)
        sz.addView(snoozeInfo)
        sz.addView(button("1 ghanta detection band") { snooze(60) })
        sz.addView(button("Dobara chalu karein") { snooze(0) })
        root.addView(sz)

        // ---- events ----
        val ev = card()
        ev.addView(title("Pichli ghatnayein"))
        events = label("", 12f, dim = true)
        ev.addView(events)
        root.addView(ev)

        root.addView(
            label(
                "Dhyan dein: gir jaane ya daudne par bhi screen lock ho sakti hai, tab sensitivity 'Kam' karein. " +
                    "Kuch phones background me camera rokte hain; us hisaab se photo shayad na aaye. " +
                    "Aisa ho to upar 'Pichli ghatnayein' me wajah dikhegi.",
                12f,
                dim = true,
            )
        )

        setContentView(ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 38, 28), Color.BLACK, Color.BLACK),
            )
            isFillViewport = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        // The service may have been killed while the switch said "on".
        if (Prefs.guardOn(this) && GuardService.instance == null) {
            try { GuardService.start(this, true) } catch (_: Exception) {}
        }
        refresh()
    }

    private fun refresh() {
        syncs.forEach { it() }
        val adminOn = dpm.isAdminActive(admin)
        val camOn = hasCamera()
        val notifOn = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        mark(adminRow, adminOn, "Device Admin")
        mark(cameraRow, camOn, "Camera ijazat")
        mark(notifRow, notifOn, "Notification ijazat")

        val on = Prefs.guardOn(this)
        statusTitle.text = if (on) "SURAKSHA CHALU" else "SURAKSHA BAND"
        statusTitle.setTextColor(if (on) green else red)
        statusSub.text = when {
            !adminOn -> "Pehle Device Admin chalu karein, warna photo aur lock kaam nahi karenge."
            !camOn -> "Camera ijazat dein, warna photo nahi aayegi."
            on -> "Camera background me: " + if (GuardService.cameraCapable) "taiyaar" else "pakka nahi"
            else -> "Upar switch dabakar shuru karein."
        }
        photoInfo.text = "Ab tak ki photo: ${SelfieCapture.countPhotos(this)}  (Gallery > Pictures > auraXGuard)"
        val left = (Prefs.snoozeUntil(this) - System.currentTimeMillis()) / 60000
        snoozeInfo.text = if (Prefs.snoozed(this)) {
            "Detection band hai, ${left + 1} minute baad apne aap chalu hoga."
        } else {
            "Detection chalu hai."
        }
        events.text = Prefs.events(this).take(8).joinToString("\n").ifBlank { "Abhi kuch nahi hua" }
    }

    private fun mark(v: TextView, ok: Boolean, name: String) {
        v.text = (if (ok) "✔ " else "✘ ") + name + if (ok) ": theek" else ": chalu karna baaki"
        v.setTextColor(if (ok) green else red)
    }

    private fun hasCamera() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        restartGuardIfOn()
        refresh()
    }

    private fun setGuard(on: Boolean) {
        Prefs.setGuardOn(this, on)
        if (on) {
            if (!hasCamera()) askPermissions()
            try {
                GuardService.start(this, true)
            } catch (e: Exception) {
                Prefs.log(this, "Guard shuru nahi hua: ${e.message}")
            }
        } else {
            GuardService.stop(this)
            Siren.stop()
            Prefs.log(this, "Suraksha band")
        }
        refresh()
    }

    private fun restartGuardIfOn() {
        if (!Prefs.guardOn(this)) return
        try { GuardService.start(this, true) } catch (_: Exception) {}
    }

    private fun snooze(minutes: Int) {
        Prefs.setSnoozeUntil(this, if (minutes == 0) 0L else System.currentTimeMillis() + minutes * 60_000L)
        Prefs.log(this, if (minutes == 0) "Detection dobara chalu" else "Detection $minutes minute ke liye band")
        refresh()
    }

    private fun testSelfie() {
        if (!hasCamera()) {
            askPermissions()
            return
        }
        Prefs.log(this, "Test photo shuru")
        SelfieCapture.run(this, "Test photo") { _, msg ->
            Prefs.log(this, msg)
            runOnUiThread { refresh() }
        }
    }

    private fun openLatestPhoto() {
        val uri = SelfieCapture.latestPhotoUri(this)
        if (uri == null) {
            Toast.makeText(this, "Abhi koi photo nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Gallery nahi khuli", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- small view helpers (no XML layouts, keeps the build simple) ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float, bold: Boolean = false, dim: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(if (dim) Color.argb(185, 255, 255, 255) else Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun title(s: String) = label(s, 17f, bold = true).apply { setTextColor(green) }

    /** Frosted-glass style panel: soft white fill, thin light border, round corners. */
    private fun card(highlight: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (highlight) Color.argb(60, 80, 220, 160) else Color.argb(26, 255, 255, 255))
            setStroke(dp(1), Color.argb(46, 255, 255, 255))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(14) }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb(70, 80, 220, 160))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) }
    }

    /** A switch whose value is re-read from settings whenever the screen refreshes. */
    private fun toggle(text: String, read: () -> Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(this)
        sw.text = text
        sw.textSize = 15f
        sw.setTextColor(Color.WHITE)
        sw.setPadding(0, dp(8), 0, dp(8))
        val listener = CompoundButton.OnCheckedChangeListener { _, on -> onChange(on) }
        sw.isChecked = read()
        sw.setOnCheckedChangeListener(listener)
        syncs.add {
            val now = read()
            if (sw.isChecked != now) {
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = now
                sw.setOnCheckedChangeListener(listener)
            }
        }
        return sw
    }

    private fun radios(labels: Array<String>, selected: Int, onPick: (Int) -> Unit) =
        RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            labels.forEachIndexed { i, l ->
                addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    text = l
                    setTextColor(Color.WHITE)
                    isChecked = i == selected
                    setOnClickListener { onPick(i) }
                    setPadding(dp(4), 0, dp(16), 0)
                })
            }
        }
}
