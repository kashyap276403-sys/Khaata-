package com.aurax.guard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
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
import com.aurax.guard.ui.HomeActions
import com.aurax.guard.ui.TabShell

/** One screen: big on/off, setup checklist, per-feature cards, event history. */
class MainActivity : Activity(), HomeActions {
    private val green = Color.rgb(80, 220, 160)
    private val red = Color.rgb(255, 110, 110)

    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var adminRow: TextView
    private lateinit var cameraRow: TextView
    private lateinit var notifRow: TextView
    private lateinit var locRow: TextView
    private lateinit var queueInfo: TextView
    private lateinit var testBtn: Button
    private lateinit var photoInfo: TextView
    private lateinit var snoozeInfo: TextView
    private lateinit var events: TextView
    private lateinit var sirenBtn: Button
    private lateinit var lockView: View
    private lateinit var mainView: View
    private lateinit var shell: TabShell
    private lateinit var pinBtnLabel: Button
    private lateinit var pinRemoveBtn: Button
    private val syncs = mutableListOf<() -> Unit>()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var bioBtn: Button
    private var pinFails = 0
    private var pinBlockedUntil = 0L
    private val poll = object : Runnable {
        override fun run() {
            sirenBtn.visibility = if (Siren.isPlaying()) View.VISIBLE else View.GONE
            if (::shell.isInitialized) shell.setSirenVisible(Siren.isPlaying())
            ui.postDelayed(this, 700)
        }
    }

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
        hero.addView(toggle("Smart Protection", { Prefs.guardOn(this) }) { on -> setGuard(on) })
        testBtn = button("Test Trigger (photo + GPS + log)") { runTestTrigger() }
        hero.addView(testBtn)
        sirenBtn = button("SIREN BAND KAREIN", danger = true) {
            Siren.stop()
            Prefs.log(this, "App se siren band ki")
            refresh()
        }
        sirenBtn.visibility = View.GONE
        hero.addView(sirenBtn)
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
        locRow = label("", 15f)
        setup.addView(locRow)
        setup.addView(button("Camera, location aur notification ijazat dein") { askPermissions() })
        setup.addView(button("Location 'Hamesha' (background) ke liye settings") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        })
        setup.addView(button("Battery 'Unrestricted' karein") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(setup)

        // ---- local alert queue ----
        val ql = card()
        ql.addView(title("Alert queue (sirf aapke phone me)"))
        queueInfo = label("", 13f, dim = true)
        ql.addView(queueInfo)
        ql.addView(button("Queue dekhein (photo, GPS link, battery)") {
            startActivity(Intent(this, EventsActivity::class.java))
        })
        root.addView(ql)

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
        pw.addView(button("Photos dekhein / mitayein") {
            startActivity(Intent(this, PhotosActivity::class.java))
        })
        root.addView(pw)

        // ---- snatch / charger / siren ----
        val sn = card()
        sn.addView(title("Phone chheenne par"))
        sn.addView(toggle("Jhatka lagne par screen lock", { Prefs.snatchLockOn(this) }) {
            Prefs.setSnatchLockOn(this, it)
            restartGuardIfOn()
        })
        sn.addView(label("Sensitivity (Zyada = halke jhatke par bhi lock)", 13f, dim = true))
        val sensHelp = label(SensitivityProfile.describe(Prefs.sensitivity(this)), 12f, dim = true)
        sn.addView(radios(Prefs.snatchNames, Prefs.sensitivity(this)) {
            Prefs.setSensitivity(this, it)
            sensHelp.text = SensitivityProfile.describe(it)
            restartGuardIfOn()
        })
        sn.addView(sensHelp)
        sn.addView(toggle("Phone unlock/use me ho to jhatka ignore karein", { Prefs.ignoreWhileUnlocked(this) }) {
            Prefs.setIgnoreWhileUnlocked(this, it)
        })
        sn.addView(toggle("Charger nikalte hi alarm aur lock", { Prefs.chargerAlarmOn(this) }) {
            Prefs.setChargerAlarmOn(this, it)
            restartGuardIfOn()
        })
        sn.addView(toggle("Siren bajao", { Prefs.sirenOn(this) }) { Prefs.setSirenOn(this, it) })
        sn.addView(toggle("Siren ke saath torch aur vibration", { Prefs.sirenFlash(this) }) { Prefs.setSirenFlash(this, it) })
        sn.addView(button("Siren test (dobara dabane par band)") {
            if (Siren.isPlaying()) Siren.stop() else Siren.start(this, 10)
        })
        root.addView(sn)

        // ---- park mode ----
        val pk = card()
        pk.addView(title("Haath mat lagao mode"))
        pk.addView(label("Phone mez par rakh kar chalu karein. 10 second baad kisi ne uthaya ya hilaya to siren baje aur screen lock ho.", 13f, dim = true))
        pk.addView(toggle("Haath mat lagao mode", { Prefs.parkOn(this) }) { on ->
            Prefs.setParkOn(this, on)
            if (on && !Prefs.guardOn(this)) setGuard(true) else restartGuardIfOn()
            if (on) Toast.makeText(this, "10 second baad active hoga", Toast.LENGTH_LONG).show()
        })
        root.addView(pk)

        // ---- privacy ----
        val pv = card()
        pv.addView(title("Privacy"))
        pv.addView(label("Internet sirf tab use hota hai jab aap Telegram alerts chalu karein. Tab alert (photo, location) aapke apne bot ko jata hai. Baaki sab sirf is phone me rehta hai.", 13f, dim = true))
        pinBtnLabel = button("") { setOrChangePin() }
        pv.addView(pinBtnLabel)
        pinRemoveBtn = button("App PIN hatayein") { removePin() }
        pv.addView(pinRemoveBtn)
        pv.addView(toggle("Screenshot aur recent apps me chhupao", { Prefs.secureScreen(this) }) {
            Prefs.setSecureScreen(this, it)
            applySecure()
        })
        pv.addView(toggle("Photos gallery me dikhao", { Prefs.photosInGallery(this) }) {
            Prefs.setPhotosInGallery(this, it)
        })
        pv.addView(label("Band karne par nayi photos sirf is app ke andar rahengi (gallery me nahi dikhengi).", 12f, dim = true))
        pv.addView(label("Photos app ke andar encrypted (AES-256) rehti hain.", 12f, dim = true))
        pv.addView(label("App PIN ke baad kitni der khula rahe (minute)?", 13f, dim = true))
        val graceOpts = intArrayOf(1, 5, 15)
        pv.addView(radios(arrayOf("1", "5", "15"), graceOpts.indexOf(Prefs.lockGraceMin(this)).coerceAtLeast(0)) {
            Prefs.setLockGraceMin(this, graceOpts[it])
        })
        pv.addView(toggle("PIN ke saath fingerprint se bhi kholein", { Prefs.bioOn(this) }) {
            if (it && !Prefs.hasPin(this)) {
                Toast.makeText(this, "Pehle App PIN lagayein", Toast.LENGTH_LONG).show()
            }
            Prefs.setBioOn(this, it)
        })
        pv.addView(label("Purani photos apne aap mitayein:", 13f, dim = true))
        val retOpts = intArrayOf(0, 7, 30)
        pv.addView(radios(arrayOf("Kabhi nahi", "7 din", "30 din"), retOpts.indexOf(Prefs.retentionDays(this)).coerceAtLeast(0)) {
            Prefs.setRetentionDays(this, retOpts[it])
            SelfieCapture.purgeOld(this, retOpts[it])
            refresh()
        })
        pv.addView(button("Sab photos aur ghatnayein mitayein", danger = true) { confirmWipe() })
        root.addView(pv)

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

        mainView = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 38, 28), Color.BLACK, Color.BLACK),
            )
            isFillViewport = true
            addView(root)
        }
        lockView = buildLock()
        // The old full screen (mainView) is kept intact; it opens from Home as "Protection settings".
        shell = TabShell(this, mainView, this)
        setContentView(FrameLayout(this).apply {
            addView(shell.root)
            addView(lockView)
        })
    }

    override fun onBackPressed() {
        if (::shell.isInitialized && Gate.isOpen(this) && shell.back()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    /** PIN screen shown instead of the app while the app PIN has not been entered. */
    private fun buildLock(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(4, 38, 28), Color.BLACK),
            )
            isClickable = true
        }
        box.addView(label("auraX Guard", 28f, bold = true))
        box.addView(label("App PIN daalein", 15f, dim = true))
        val pin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 24f
            hint = "••••"
            setHintTextColor(Color.argb(90, 255, 255, 255))
        }
        val err = label("", 13f).apply { setTextColor(red) }
        box.addView(pin)
        box.addView(err)
        box.addView(button("Kholein") {
            val now = SystemClock.elapsedRealtime()
            if (now < pinBlockedUntil) {
                err.text = "Bahut galat koshish. ${(pinBlockedUntil - now) / 1000 + 1} second ruko."
                return@button
            }
            val salt = Prefs.pinSalt(this)
            val hash = Prefs.pinHash(this)
            if (salt != null && hash != null && PinHasher.verify(pin.text.toString(), salt, hash)) {
                pinFails = 0
                err.text = ""
                pin.text.clear()
                Gate.open(this)
                showGate()
                refresh()
            } else {
                pinFails++
                pin.text.clear()
                Prefs.log(this, "Galat App PIN ($pinFails)")
                if (pinFails == 3 && Prefs.selfieOn(this) && hasCamera()) {
                    EventRecorder.trigger(this, "Galat App PIN", "Galat App PIN", EventRecorder.PhotoMode.CAPTURE_NOW)
                }
                if (pinFails >= 5) {
                    pinFails = 0
                    pinBlockedUntil = now + 30_000
                    err.text = "5 galat PIN. 30 second ruko."
                } else {
                    err.text = "Galat PIN"
                }
            }
        })
        bioBtn = button("Fingerprint se kholein") { tryBiometric() }
        box.addView(bioBtn)
        return box
    }

    private fun tryBiometric() {
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("auraX Guard kholein")
                .setNegativeButton("PIN use karein", mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        Gate.open(this@MainActivity)
                        showGate()
                        refresh()
                    }
                },
            )
        } catch (e: Exception) {
            Prefs.log(this, "Fingerprint nahi chali: ${e.message}")
        }
    }

    private fun showGate() {
        val open = Gate.isOpen(this)
        lockView.visibility = if (open) View.GONE else View.VISIBLE
        shell.root.visibility = if (open) View.VISIBLE else View.GONE
        bioBtn.visibility = if (Prefs.bioOn(this)) View.VISIBLE else View.GONE
    }

    private fun applySecure() {
        if (Prefs.secureScreen(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun askPin(titleText: String, onPin: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            textSize = 22f
        }
        AlertDialog.Builder(this)
            .setTitle(titleText)
            .setView(input)
            .setPositiveButton("Theek hai") { _, _ -> onPin(input.text.toString()) }
            .setNegativeButton("Radd", null)
            .show()
    }

    private fun setOrChangePin() {
        askPin("Naya PIN (4 se 8 ank)") { first ->
            if (!PinHasher.isValidPin(first)) {
                Toast.makeText(this, "PIN 4 se 8 ank ka hona chahiye", Toast.LENGTH_LONG).show()
                return@askPin
            }
            askPin("PIN dobara daalein") { second ->
                if (first != second) {
                    Toast.makeText(this, "PIN match nahi hua", Toast.LENGTH_LONG).show()
                } else {
                    val salt = PinHasher.newSalt()
                    Prefs.setPin(this, salt, PinHasher.hash(first, salt))
                    Gate.open(this)
                    Prefs.log(this, "App PIN set kiya")
                    refresh()
                }
            }
        }
    }

    private fun removePin() {
        askPin("Abhi ka PIN daalein") { pin ->
            val salt = Prefs.pinSalt(this)
            val hash = Prefs.pinHash(this)
            if (salt != null && hash != null && PinHasher.verify(pin, salt, hash)) {
                Prefs.clearPin(this)
                Prefs.log(this, "App PIN hata diya")
                refresh()
            } else {
                Toast.makeText(this, "Galat PIN", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmWipe() {
        AlertDialog.Builder(this)
            .setMessage("Saari intruder photos aur ghatnayein hamesha ke liye mit jayengi. Mitayein?")
            .setPositiveButton("Mitayein") { _, _ ->
                SelfieCapture.listPhotos(this).forEach { SelfieCapture.deletePhoto(this, it) }
                Prefs.clearEvents(this)
                EventStore.clear(this)
                Toast.makeText(this, "Sab mita diya", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Rehne dein", null)
            .show()
    }

    override fun onPause() {
        ui.removeCallbacks(poll)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        applySecure()
        showGate()
        ui.post(poll)
        SelfieCapture.purgeOld(this, Prefs.retentionDays(this))
        if (!Gate.isOpen(this) && Prefs.bioOn(this)) tryBiometric()
        if (Gate.isOpen(this) && !Prefs.permsAsked(this)) {
            Prefs.setPermsAsked(this)
            askPermissions()
        }
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
        val locOn = LocationFetcher.hasPermission(this)
        mark(locRow, locOn, "Location ijazat")

        val on = Prefs.guardOn(this)
        statusTitle.text = if (on) "SMART PROTECTION: ON" else "SMART PROTECTION: OFF"
        statusTitle.setTextColor(if (on) green else red)
        statusSub.text = when {
            !adminOn -> "Pehle Device Admin chalu karein, warna photo aur lock kaam nahi karenge."
            !camOn -> "Camera ijazat dein, warna photo nahi aayegi."
            !locOn -> "Location ijazat dein, warna GPS location nahi milegi."
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
        pinBtnLabel.text = if (Prefs.hasPin(this)) "App PIN badlein" else "App PIN lagayein (app kholne ke liye)"
        pinRemoveBtn.visibility = if (Prefs.hasPin(this)) View.VISIBLE else View.GONE
        val queue = EventStore.readAll(this)
        queueInfo.text = "Queue me ${queue.size} alert (${queue.count { !it.sent }} abhi bhejna baaki)" +
            (queue.firstOrNull()?.let { "\nAakhri: ${it.reason} - ${EventRecord.formatTime(it.timeMs)}" } ?: "")
        events.text = Prefs.events(this).take(8).joinToString("\n").ifBlank { "Abhi kuch nahi hua" }
        if (::shell.isInitialized) shell.refresh()
    }

    private fun mark(v: TextView, ok: Boolean, name: String) {
        v.text = (if (ok) "✔ " else "✘ ") + name + if (ok) ": theek" else ": chalu karna baaki"
        v.setTextColor(if (ok) green else red)
    }

    private fun hasCamera() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    // ---- HomeActions: the new Home tab calls the existing logic above ----
    override fun testTrigger() = runTestTrigger()

    override fun togglePark() {
        val on = !Prefs.parkOn(this)
        Prefs.setParkOn(this, on)
        if (on && !Prefs.guardOn(this)) setGuard(true) else restartGuardIfOn()
        if (on) Toast.makeText(this, "10 second baad active hoga", Toast.LENGTH_LONG).show()
        refresh()
    }

    override fun fixSetup() {
        if (!hasCamera() || !LocationFetcher.hasPermission(this)) {
            askPermissions()
        } else if (!dpm.isAdminActive(admin)) {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Galat password pakadne aur screen lock karne ke liye zaroori hai.",
                    )
            )
        } else {
            shell.showControls()
        }
    }

    override fun openPhotos() = startActivity(Intent(this, PhotosActivity::class.java))
    override fun openQueue() = startActivity(Intent(this, EventsActivity::class.java))
    override fun openReadiness() = startActivity(Intent(this, com.aurax.guard.oem.OemReadinessActivity::class.java))
    override fun openTelegram() = startActivity(Intent(this, com.aurax.guard.telegram.TelegramSettingsActivity::class.java))
    override fun openControls() = shell.showControls()

    override fun stopSiren() {
        Siren.stop()
        Prefs.log(this, "App se siren band ki")
        refresh()
    }

    override fun quickLock() {
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            Prefs.log(this, "Quick Lock: screen lock ki")
        } else {
            Toast.makeText(this, "Quick Lock ke liye pehle Device Admin chalu karein", Toast.LENGTH_LONG).show()
        }
    }

    override fun panic() {
        val locked = dpm.isAdminActive(admin)
        if (locked) dpm.lockNow()
        Siren.start(this)
        Prefs.log(this, "Panic: " + (if (locked) "screen lock + " else "") + "siren chalu")
        EventRecorder.trigger(this, "Panic button", "Panic", EventRecorder.PhotoMode.NONE)
        refresh()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        restartGuardIfOn()
        refresh()
    }

    override fun setGuard(on: Boolean) {
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

    /** Runs the full pipeline once: photo + GPS + battery + network + time -> local queue. */
    private fun runTestTrigger() {
        testBtn.isEnabled = false
        testBtn.text = "Test chal raha hai (12 second tak)..."
        Prefs.log(this, "Test Trigger shuru")
        EventRecorder.trigger(this, "Test Trigger", "Test Trigger", EventRecorder.PhotoMode.CAPTURE_NOW) { rec ->
            testBtn.isEnabled = true
            testBtn.text = "Test Trigger (photo + GPS + log)"
            Toast.makeText(
                this,
                "Queue me save. Photo: " + (if (rec.photoName != null) "ok" else "nahi") +
                    ", GPS: " + (if (rec.mapsLink != null) "ok" else "nahi"),
                Toast.LENGTH_LONG,
            ).show()
            refresh()
        }
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

    private fun button(text: String, danger: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (danger) Color.argb(120, 255, 90, 90) else Color.argb(70, 80, 220, 160))
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
