package com.aurax.guard.smart

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aurax.guard.Gate
import com.aurax.guard.Prefs
import com.aurax.guard.cleaner.SizeFormat
import com.aurax.guard.cleaner.TreeScanner
import com.aurax.guard.ui.Ui
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * APK / file check: pick a folder (like Downloads), see a risk report for every APK in it.
 * REPORT ONLY: nothing is deleted, moved or installed. To check an APK this screen makes a short-lived copy
 * in the app's OWN cache folder and removes only that copy; your files are never touched.
 */
class ApkScanActivity : Activity() {
    private lateinit var body: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val cancel = AtomicBoolean(false)
    private data class Row(val name: String, val size: Long, val v: ApkVerdict, val pkg: String)
    private var rows: List<Row> = emptyList()
    private var busy = false
    private var info = "Folder chuniye (jaise Downloads). Sirf report banegi, kuch delete ya install nahi hoga."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ApkScanActivity, 16), Ui.dp(this@ApkScanActivity, 24), Ui.dp(this@ApkScanActivity, 16), Ui.dp(this@ApkScanActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = Ui.backdrop(context); isFillViewport = true; addView(body) })
        render()
    }

    override fun onDestroy() { cancel.set(true); io.shutdownNow(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (!SmartPrefs.apkScanOn(this)) return
        if (requestCode != 41 || resultCode != RESULT_OK) return
        busy = true; rows = emptyList(); info = "Scan chal raha hai..."; cancel.set(false); render()
        io.execute {
            val out = try { scan(uri) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false; rows = out
                info = if (out.isEmpty()) "Is folder me koi APK nahi mila." else "${out.size} APK mile. Neeche report dekhein."
                render()
            }
        }
    }

    private fun scan(tree: android.net.Uri): List<Row> {
        val items = TreeScanner.scanTree(this, tree) { cancel.get() }
            .filter { val n = it.name.lowercase(); n.endsWith(".apk") }
            .take(40)
        val dir = File(cacheDir, "apkscan").apply { mkdirs() }
        val res = mutableListOf<Row>()
        for (it in items) {
            if (cancel.get()) break
            val tmp = File(dir, "check.apk")
            try {
                if (it.size > 400L * 1024 * 1024) { res.add(Row(it.name, it.size, ApkVerdict(ApkLevel.MEDIUM, 2, listOf("Bahut badi file: jaanch nahi hui")), "")); continue }
                contentResolver.openInputStream(it.uri ?: continue)?.use { ins -> tmp.outputStream().use { o -> ins.copyTo(o) } }
                res.add(inspect(it.name, it.size, tmp))
            } catch (_: Exception) {
                res.add(Row(it.name, it.size, ApkRules.assess(it.name, emptyList(), false, false, false), ""))
            } finally {
                try { tmp.delete() } catch (_: Exception) {} // our own temporary copy only
            }
        }
        return res.sortedByDescending { it.v.score }
    }

    @Suppress("DEPRECATION")
    private fun inspect(name: String, size: Long, f: File): Row {
        val pm = packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val pi = try { pm.getPackageArchiveInfo(f.absolutePath, flags) } catch (_: Exception) { null }
            ?: return Row(name, size, ApkRules.assess(name, emptyList(), false, false, false), "")
        var changed = false
        try {
            val installed = pm.getPackageInfo(pi.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val a = pi.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: emptySet()
            val b = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: emptySet()
            changed = a.isNotEmpty() && b.isNotEmpty() && a != b
        } catch (_: Exception) {
            // not installed: nothing to compare
        }
        val dbg = ((pi.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return Row(name, size, ApkRules.assess(name, pi.requestedPermissions?.toList() ?: emptyList(), changed, dbg, true), pi.packageName ?: "")
    }

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "APK / file check"))
        body.addView(Ui.dimText(c, "Download kiye APK ki jaanch: kaun si ijazat maangta hai, naam aur signature kaise hain. Sirf report; delete/install apne aap kabhi nahi."))
        val top = Ui.card(c, Ui.teal, 14)
        val enabled = SmartPrefs.apkScanOn(c)
        top.addView(Ui.tintSwitch(android.widget.Switch(c)).apply {
            text = "APK / file check chalu"; textSize = 15f; setTextColor(Color.WHITE); isChecked = enabled
            setOnCheckedChangeListener { v, b -> if (v.isPressed) { SmartPrefs.setApkScanOn(c, b); if (!b) cancel.set(true); render() } }
        })
        top.addView(Ui.text(c, if (!enabled) "Off" else if (busy) "Scan chal raha hai" else "Ready", 15f, bold = true))
        top.addView(Ui.dimText(c, info))
        if (!enabled) top.addView(Ui.dimText(c, "Band hai: koi scan nahi chalega. Upar switch se chalu karein.")) else if (!busy) top.addView(Ui.button(c, "Folder chunein aur scan karein", Ui.Btn.PRIMARY, 10) {
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 41) } catch (_: Exception) { Toast.makeText(c, "Folder chunne wali screen nahi khuli", Toast.LENGTH_LONG).show() }
        }) else top.addView(Ui.button(c, "Rokein", Ui.Btn.GHOST, 10) { cancel.set(true) })
        body.addView(top)
        rows.forEach { r ->
            val col = when (r.v.level) { ApkLevel.HIGH -> Ui.red; ApkLevel.MEDIUM -> Ui.amber; else -> Ui.emerald }
            val card = Ui.card(c, col, 10, drops = 0)
            card.addView(Ui.badge(c, r.v.level.label, col))
            card.addView(Ui.text(c, r.name, 15f, bold = true).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
            card.addView(Ui.dimText(c, SizeFormat.bytes(r.size) + (if (r.pkg.isNotEmpty()) "  |  ${r.pkg}" else "")))
            r.v.reasons.forEach { card.addView(Ui.dimText(c, "- $it")) }
            body.addView(card)
        }
        if (rows.isNotEmpty()) body.addView(Ui.button(c, "Report copy karein", Ui.Btn.GHOST, 12) {
            val txt = rows.joinToString("\n\n") { "${it.v.level.label}: ${it.name}\n" + it.v.reasons.joinToString("\n") { x -> "- $x" } }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("APK report", txt))
            Toast.makeText(c, "Copy ho gaya", Toast.LENGTH_SHORT).show()
        })
        body.addView(Ui.dimText(c, "Ye jaanch sirf ijazat aur naam dekhti hai: ye virus-scanner nahi hai. Install karne ka faisla aapka hai."))
    }
}
