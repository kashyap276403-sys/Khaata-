package com.aurax.guard

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** One simple screen: set up admin + camera, switch features on/off, test. */
class MainActivity : Activity() {
    private lateinit var adminStatus: TextView
    private lateinit var cameraStatus: TextView
    private lateinit var photoStatus: TextView
    private lateinit var eventStatus: TextView
    private lateinit var selfieSwitch: Switch
    private lateinit var snatchSwitch: Switch
    private var building = true

    private val admin get() = ComponentName(this, GuardAdminReceiver::class.java)
    private val dpm get() = getSystemService(DevicePolicyManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        root.addView(text("auraX Guard", 28f, bold = true))
        root.addView(text("Chori se bachav: galat password par photo, jhatka lagne par screen lock.", 14f, dim = true))

        root.addView(section("1. Zaroori setup"))
        adminStatus = text("", 15f)
        root.addView(adminStatus)
        root.addView(button("Device Admin chalu karein") {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Galat password pakadne aur screen lock karne ke liye zaroori hai. " +
                        "Jab tak ye chalu hai, koi app ko aasani se uninstall nahi kar sakta.",
                )
            startActivity(i)
        })
        cameraStatus = text("", 15f)
        root.addView(cameraStatus)
        root.addView(button("Camera aur notification ijazat dein") { askPermissions() })

        root.addView(section("2. Galat password par photo"))
        selfieSwitch = Switch(this).apply {
            text = "Intruder photo chalu"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, on -> if (!building) Prefs.setSelfieOn(this@MainActivity, on) }
        }
        root.addView(selfieSwitch)
        root.addView(text("Kitni galat koshish ke baad photo?", 14f, dim = true))
        root.addView(radios(arrayOf("1", "2", "3"), Prefs.attemptsBeforePhoto(this) - 1) {
            Prefs.setAttemptsBeforePhoto(this, it + 1)
        })
        root.addView(button("Photo ka test karein") { testSelfie() })
        photoStatus = text("", 14f, dim = true)
        root.addView(photoStatus)

        root.addView(section("3. Phone chheenne par screen band"))
        snatchSwitch = Switch(this).apply {
            text = "Jhatka detection chalu"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, on -> if (!building) setSnatch(on) }
        }
        root.addView(snatchSwitch)
        root.addView(text("Sensitivity (Zyada = halke jhatke par bhi lock)", 14f, dim = true))
        root.addView(radios(Prefs.snatchNames, Prefs.sensitivity(this)) {
            Prefs.setSensitivity(this, it)
            if (Prefs.snatchOn(this)) startSnatch()
        })

        root.addView(section("Aakhri ghatna"))
        eventStatus = text("", 14f, dim = true)
        root.addView(eventStatus)

        root.addView(
            text(
                "Dhyan dein: gir jaane ya daudne par bhi screen lock ho sakti hai, " +
                    "tab sensitivity 'Kam' karein. Battery saver is app ko band na kare, " +
                    "isliye Settings me iski battery 'Unrestricted' rakhein.",
                12f,
                dim = true,
            )
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
        building = false
    }

    override fun onResume() {
        super.onResume()
        building = true
        selfieSwitch.isChecked = Prefs.selfieOn(this)
        snatchSwitch.isChecked = Prefs.snatchOn(this)
        building = false
        refresh()
    }

    private fun refresh() {
        val adminOn = dpm.isAdminActive(admin)
        adminStatus.text = if (adminOn) "Device Admin: chalu" else "Device Admin: BAND (photo aur lock kaam nahi karenge)"
        val camOn = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        cameraStatus.text = if (camOn) "Camera ijazat: mili" else "Camera ijazat: NAHI mili"
        photoStatus.text = "Ab tak li gayi photo: ${Prefs.photoCount(this)}  (Gallery > Pictures > auraXGuard)"
        eventStatus.text = Prefs.lastEvent(this)
    }

    private fun askPermissions() {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refresh()
    }

    private fun testSelfie() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            askPermissions()
            return
        }
        Prefs.log(this, "Test photo shuru")
        SelfieCapture(this).capture { ok, msg ->
            Prefs.log(this, msg)
            if (ok) Prefs.addPhoto(this)
            runOnUiThread { refresh() }
        }
    }

    private fun setSnatch(on: Boolean) {
        Prefs.setSnatchOn(this, on)
        if (on) {
            if (!dpm.isAdminActive(admin)) {
                Prefs.log(this, "Pehle Device Admin chalu karein")
            }
            startSnatch()
        } else {
            stopService(Intent(this, SnatchService::class.java))
            Prefs.log(this, "Snatch detection band")
        }
        refresh()
    }

    private fun startSnatch() {
        startForegroundService(Intent(this, SnatchService::class.java))
    }

    // ---- tiny view helpers (no XML layouts, keeps the build simple) ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(if (dim) Color.argb(180, 255, 255, 255) else Color.WHITE)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun section(s: String) = text(s, 18f, bold = true).apply {
        setTextColor(Color.rgb(80, 220, 160))
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
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
