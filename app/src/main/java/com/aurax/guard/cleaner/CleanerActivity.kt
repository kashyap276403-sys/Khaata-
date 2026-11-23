package com.aurax.guard.cleaner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aurax.guard.Gate
import com.aurax.guard.Prefs
import com.aurax.guard.ui.Ui
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Safe cleaner + file scanner.  1 Scan  ->  2 Preview  ->  3 You select  ->  4 You confirm.
 * Nothing is selected or deleted by the app on its own, ever.
 */
class CleanerActivity : Activity() {
    private lateinit var body: LinearLayout
    private val sel = Selection()
    private var items: List<ScanItem> = emptyList()
    private var filter = "ALL"
    private var step = 1
    private var busy = false
    private var source = ""
    private val cancel = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor()
    private val PICK = 31

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@CleanerActivity, 16), Ui.dp(this@CleanerActivity, 24), Ui.dp(this@CleanerActivity, 16), Ui.dp(this@CleanerActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = com.aurax.guard.ui.Ui.backdrop(context); addView(body) })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) finish()
    }

    override fun onDestroy() {
        cancel.set(true)
        super.onDestroy()
    }

    private fun startScan(label: String, work: () -> List<ScanItem>) {
        busy = true; source = label; sel.clear(); items = emptyList(); cancel.set(false)
        render()
        io.execute {
            val r = try { work() } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false; items = r; step = 2; render()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == PICK && resultCode == RESULT_OK && uri != null) {
            val name = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { "folder" }
            startScan("Folder: $name") { TreeScanner.scanTree(this, uri) { cancel.get() } }
        }
    }

    private fun pick() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK)
        } catch (_: Exception) {
            AlertDialog.Builder(this).setMessage("Is phone par folder chunne wali screen nahi khuli.").setPositiveButton("OK", null).create().also { d -> d.setOnShowListener { com.aurax.guard.ui.Ui.styleDialog(d) } }.show()
        }
    }

    private fun visible(): List<ScanItem> {
        val f = items.filter {
            when (filter) {
                "APK" -> it.verdict.kind == FileKind.APK
                "LARGE" -> it.verdict.kind == FileKind.LARGE || it.size >= FileRules.LARGE_BYTES
                "TEMP" -> it.verdict.kind == FileKind.TEMP
                "RISK" -> it.verdict.level != FileLevel.INFO
                else -> true
            }
        }
        return f.sortedWith(compareByDescending<ScanItem> { it.verdict.level.ordinal }.thenByDescending { it.size }).take(300)
    }

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "Safe Cleaner"))
        body.addView(Ui.dimText(c, "1 Scan  >  2 Preview  >  3 Aap chunein  >  4 Confirm.   Step abhi: $step"))
        val safe = Ui.card(c, Ui.emerald, 12)
        safe.addView(Ui.text(c, "Zero auto-delete", 15f, bold = true))
        safe.addView(Ui.dimText(c, "Kuch bhi apne aap select ya delete nahi hota. Delete kiya hua wapas nahi aata (recycle bin nahi)."))
        body.addView(safe)

        val scan = Ui.card(c, null, 12)
        scan.addView(Ui.text(c, "1. Scan", 15f, bold = true))
        scan.addView(Ui.button(c, "Folder chunkar scan karein (Download, WhatsApp...)", Ui.Btn.PRIMARY, 8) { if (!busy) pick() })
        scan.addView(Ui.button(c, "Sirf auraX Guard ka apna cache scan karein", Ui.Btn.GHOST, 6) { if (!busy) startScan("auraX cache") { TreeScanner.scanOwnCache(this) } })
        scan.addView(Ui.dimText(c, "Scan sirf padhta hai. Warning 'Risky/Dhyan' sirf naam aur type par based sanket hai, malware ka faisla nahi."))
        body.addView(scan)

        if (busy) {
            body.addView(Ui.dimText(c, "Scan chal raha hai...").apply { setPadding(0, Ui.dp(c, 16), 0, 0) })
            body.addView(Ui.button(c, "Rokein", Ui.Btn.GHOST) { cancel.set(true) })
            return
        }
        if (step < 2) return

        val shown = visible()
        val risky = items.count { it.verdict.level != FileLevel.INFO }
        val prev = Ui.card(c, null, 12)
        prev.addView(Ui.text(c, "2. Preview: $source", 15f, bold = true))
        prev.addView(Ui.dimText(c, "${items.size} files mili, ${SizeFormat.bytes(items.sumOf { it.size })}. Dhyan wali: $risky." +
            if (items.size >= TreeScanner.MAX_ITEMS) " (Limit tak scan hua)" else ""))
        prev.addView(Ui.dimText(c, FileExplain.overview(items.map { it.verdict })))
        val chips = listOf("ALL" to "Sab", "RISK" to "Risky", "APK" to "APK", "LARGE" to "Badi", "TEMP" to "Temp")
        prev.addView(Ui.chipRow(c, chips.map { (k, l) -> Ui.chip(c, l, filter == k) { filter = k; render() } }))
        body.addView(prev)

        if (shown.isEmpty()) body.addView(Ui.dimText(c, "Is filter me kuch nahi mila.").apply { setPadding(0, Ui.dp(c, 12), 0, 0) })
        shown.forEach { it -> body.addView(row(it)) }
        if (items.size > shown.size && shown.size >= 300) body.addView(Ui.dimText(c, "Sirf top 300 dikh rahi hain. Filter badal kar baaki dekhein."))

        val sizes = items.associate { it.id to it.size }
        val bar = Ui.card(c, Ui.amber, 14)
        bar.addView(Ui.text(c, "3. Aapne chuna: ${sel.size} files, ${SizeFormat.bytes(sel.totalBytes(sizes))}", 15f, bold = true))
        bar.addView(Ui.button(c, "Chuni hui files delete karein...", Ui.Btn.DANGER, 8) { confirm() }.apply {
            isEnabled = sel.size > 0; alpha = if (sel.size > 0) 1f else 0.4f
        })
        bar.addView(Ui.button(c, "Chunav saaf karein", Ui.Btn.GHOST, 6) { sel.clear(); render() })
        body.addView(bar)
    }

    private fun row(it: ScanItem): LinearLayout {
        val c = this
        val col = when (it.verdict.level) { FileLevel.HIGH -> Ui.red; FileLevel.MEDIUM -> Ui.amber; else -> Ui.teal }
        return Ui.card(c, if (it.verdict.level == FileLevel.INFO) null else col, 8).apply {
            addView(CheckBox(c).apply {
                text = it.name
                setTextColor(Color.WHITE)
                isChecked = sel.isSelected(it.id)
                setOnCheckedChangeListener { _, _ -> sel.toggle(it.id); updateBar() }
            })
            addView(Ui.dimText(c, "${SizeFormat.bytes(it.size)}  |  ${it.path}"))
            addView(Ui.badge(c, it.verdict.level.label, col).apply { layoutParams = Ui.lp(c, 4).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT } })
            addView(Ui.dimText(c, it.verdict.reasons.joinToString("; ")))
            addView(Ui.dimText(c, FileExplain.explain(it.name, it.verdict), 12f))
            if (com.aurax.guard.ai.AiConfig.ready(c)) {
                addView(Ui.button(c, "AI se samjhao (sirf salah)", Ui.Btn.GHOST, 4) { explain(it) })
            }
        }
    }

    private fun updateBar() = render()

    /** Optional. Sends only the file name, size and the app's warning signs. The answer is advice; it never selects or deletes anything. */
    private fun explain(it: ScanItem) {
        val app = applicationContext
        android.widget.Toast.makeText(this, "AI se pooch rahe hain...", android.widget.Toast.LENGTH_SHORT).show()
        io.execute {
            val r = com.aurax.guard.ai.AiSummarizer.explainFile(app, it.name, it.size, it.verdict.reasons)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val msg = when (r) {
                    is com.aurax.guard.ai.AiResult.Ok -> r.text + "\n\nYe sirf salah hai. File aap hi chunoge aur confirm karoge, app kuch delete nahi karegi."
                    is com.aurax.guard.ai.AiResult.Err -> "AI abhi nahi chali (" + r.why + "). Upar di gayi wajah dekhein, scanner normal chalta hai."
                }
                Ui.showGlass(AlertDialog.Builder(this).setTitle(it.name).setMessage(msg).setPositiveButton("OK", null))
            }
        }
    }

    private fun confirm() {
        val chosen = items.filter { sel.isSelected(it.id) }
        if (chosen.isEmpty()) return
        val total = chosen.sumOf { it.size }
        val names = chosen.take(6).joinToString("\n") { "- ${it.name} (${SizeFormat.bytes(it.size)})" } +
            if (chosen.size > 6) "\n...aur ${chosen.size - 6} files" else ""
        AlertDialog.Builder(this)
            .setTitle("4. Confirm: ${chosen.size} files, ${SizeFormat.bytes(total)}")
            .setMessage("Ye files hamesha ke liye delete hongi:\n\n$names")
            .setPositiveButton("Haan, delete karo") { _, _ -> doDelete(chosen) }
            .setNegativeButton("Nahi", null)
            .create().also { d -> d.setOnShowListener { com.aurax.guard.ui.Ui.styleDialog(d) } }.show()
    }

    private fun doDelete(chosen: List<ScanItem>) {
        busy = true; render()
        io.execute {
            var ok = 0
            var freed = 0L
            val gone = mutableSetOf<String>()
            chosen.forEach { if (TreeScanner.delete(this, it)) { ok++; freed += it.size; gone.add(it.id) } }
            Prefs.log(this, "Cleaner: $ok/${chosen.size} files aap ke confirm par delete hui (${SizeFormat.bytes(freed)})")
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                items = items.filter { it.id !in gone }
                sel.clear(); busy = false; render()
                AlertDialog.Builder(this).setMessage("$ok / ${chosen.size} files delete hui. ${SizeFormat.bytes(freed)} jagah khaali.")
                    .setPositiveButton("OK", null).create().also { d -> d.setOnShowListener { com.aurax.guard.ui.Ui.styleDialog(d) } }.show()
            }
        }
    }
}
