package com.aurax.guard.copy

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.aurax.guard.Gate
import com.aurax.guard.Prefs
import com.aurax.guard.cleaner.ScanItem
import com.aurax.guard.cleaner.SizeFormat
import com.aurax.guard.cleaner.TreeScanner
import com.aurax.guard.ui.Ui
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * Fast Imp Files Copy: 1) pick the source folder, 2) see the list and tick files, 3) pick a destination folder,
 * 4) confirm. Every copy is checked (size + checksum). ZERO AUTO-DELETE: source and destination files are never
 * deleted or overwritten; a bad copy is only marked so you can copy it again.
 */
class FastCopyActivity : Activity() {
    private lateinit var body: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val cancel = AtomicBoolean(false)
    private var srcTree: Uri? = null
    private var items: List<ScanItem> = emptyList()
    private val picked = mutableSetOf<String>()
    private var busy = false
    private var status = "Pehle source folder chunein (jaise Download ya chat export wala folder)."
    private var results: List<Pair<String, Verify>> = emptyList()
    private var progress = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@FastCopyActivity, 16), Ui.dp(this@FastCopyActivity, 24), Ui.dp(this@FastCopyActivity, 16), Ui.dp(this@FastCopyActivity, 48))
        }
        setContentView(ScrollView(this).apply { background = Ui.backdrop(context); isFillViewport = true; addView(body) })
        render()
    }

    override fun onDestroy() { cancel.set(true); io.shutdownNow(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == 51) scan(uri) else if (requestCode == 52) startCopy(uri)
    }

    private fun scan(tree: Uri) {
        srcTree = tree; busy = true; items = emptyList(); picked.clear(); results = emptyList(); status = "Folder scan ho raha hai..."; cancel.set(false); render()
        io.execute {
            val found = try {
                TreeScanner.scanTree(this, tree) { cancel.get() }.filter { CopyRules.isImportant(it.name, it.size) }
                    .sortedByDescending { it.modifiedMs }.take(CopyRules.MAX_FILES)
            } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false; items = found
                status = if (found.isEmpty()) "Is folder me koi zaroori file nahi mili." else "${found.size} zaroori files mili. Jo copy karni hain unhe tick karein."
                render()
            }
        }
    }

    private fun startCopy(dest: Uri) {
        val src = srcTree
        if (src == null || CopyRules.sameFolder(src.toString(), dest.toString())) {
            Toast.makeText(this, "Destination source se alag folder hona chahiye", Toast.LENGTH_LONG).show(); return
        }
        val chosen = items.filter { it.id in picked }
        if (chosen.isEmpty()) return
        busy = true; results = emptyList(); cancel.set(false); progress = "Shuru..."; status = "Copy chal rahi hai..."; render()
        io.execute {
            val out = mutableListOf<Pair<String, Verify>>()
            val destDoc = DocumentsContract.buildDocumentUriUsingTree(dest, DocumentsContract.getTreeDocumentId(dest))
            for ((i, it) in chosen.withIndex()) {
                if (cancel.get()) break
                runOnUiThread { progress = "${i + 1}/${chosen.size}: ${it.name}"; if (!isDestroyed) render() }
                out.add(it.name to copyOne(it, destDoc))
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false; results = out; status = CopyRules.summary(out)
                Prefs.log(this, "Fast Imp Copy: $status")
                render()
            }
        }
    }

    /** Copies one file without ever deleting or overwriting anything, then verifies it. */
    private fun copyOne(it: ScanItem, destDoc: Uri): Verify {
        val srcUri = it.uri ?: return Verify.SIZE_MISMATCH
        var destUri: Uri? = null
        return try {
            destUri = DocumentsContract.createDocument(contentResolver, destDoc, CopyRules.mime(it.name), it.name) // provider renames on a name clash: no overwrite
            val dst = destUri ?: return Verify.SIZE_MISMATCH
            val crc = CRC32()
            var copied = 0L
            contentResolver.openInputStream(srcUri)?.use { ins ->
                contentResolver.openOutputStream(dst, "w")?.use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancel.get()) return Verify.SIZE_MISMATCH
                        val n = ins.read(buf); if (n < 0) break
                        o.write(buf, 0, n); crc.update(buf, 0, n); copied += n
                    }
                    o.flush()
                } ?: return Verify.SIZE_MISMATCH
            } ?: return Verify.SIZE_MISMATCH
            val destSize = destSize(dst)
            var destCrc: Long? = null
            if (copied <= CopyRules.CRC_LIMIT) {
                val c2 = CRC32()
                contentResolver.openInputStream(dst)?.use { r ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = r.read(buf); if (n < 0) break; c2.update(buf, 0, n) }
                    destCrc = c2.value
                }
            }
            CopyRules.verdict(it.size, copied, destSize, crc.value, destCrc)
        } catch (_: Exception) {
            Verify.SIZE_MISMATCH // a half-written file may remain: it is NOT deleted, only reported
        }
    }

    private fun destSize(u: Uri): Long = try {
        contentResolver.query(u, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
    } catch (_: Exception) { -1L }

    private fun render() {
        val c = this
        body.removeAllViews()
        body.addView(Ui.heading(c, "Fast Imp Files Copy"))
        body.addView(Ui.dimText(c, "Zaroori files (documents, photos, chat export zip/txt) ko surakshit folder me copy karein. Har copy ki size + checksum se jaanch hoti hai. Kuch bhi delete ya overwrite nahi hota."))
        val top = Ui.card(c, Ui.teal, 14)
        top.addView(Ui.text(c, if (busy) "Chal raha hai" else "Ready", 15f, bold = true))
        top.addView(Ui.dimText(c, status))
        if (busy && progress.isNotEmpty() && results.isEmpty() && items.isNotEmpty()) top.addView(Ui.dimText(c, progress))
        if (busy) top.addView(Ui.button(c, "Rokein", Ui.Btn.GHOST, 10) { cancel.set(true) })
        else top.addView(Ui.button(c, if (items.isEmpty()) "Source folder chunein" else "Doosra source folder chunein", Ui.Btn.PRIMARY, 10) {
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 51) } catch (_: Exception) { Toast.makeText(c, "Folder screen nahi khuli", Toast.LENGTH_LONG).show() }
        })
        body.addView(top)

        if (!busy && items.isNotEmpty() && results.isEmpty()) {
            val sel = Ui.card(c, null, 12, drops = 0)
            sel.addView(Ui.chipRow(c, listOf(
                Ui.chip(c, "Sab chunein", false) { picked.addAll(items.map { it.id }); render() },
                Ui.chip(c, "Kuch nahi", false) { picked.clear(); render() },
                Ui.chip(c, "Sirf chat export", false) { picked.clear(); picked.addAll(items.filter { CopyRules.isChatExport(it.name) }.map { it.id }); render() },
            ), 8))
            items.forEach { it0 ->
                sel.addView(CheckBox(c).apply {
                    text = it0.name + "  (" + SizeFormat.bytes(it0.size) + ")"
                    setTextColor(Color.WHITE); textSize = 14f
                    isChecked = it0.id in picked
                    setOnCheckedChangeListener { v, b -> if (v.isPressed) { if (b) picked.add(it0.id) else picked.remove(it0.id); render() } }
                })
            }
            body.addView(sel)
            body.addView(Ui.button(c, "${picked.size} file copy karein: destination chunein", if (picked.isEmpty()) Ui.Btn.GHOST else Ui.Btn.PRIMARY, 12) {
                if (picked.isEmpty()) return@button
                Ui.showGlass(android.app.AlertDialog.Builder(c).setTitle("${picked.size} file copy karein?")
                    .setMessage("Agle step me destination folder chunein. Source aur destination me kuch delete ya overwrite nahi hoga.")
                    .setPositiveButton("Aage") { _, _ ->
                        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 52) } catch (_: Exception) {}
                    }.setNegativeButton("Wapas", null))
            })
        }
        results.forEach { (name, v) ->
            val card = Ui.card(c, if (v.ok) Ui.emerald else Ui.red, 10, drops = 0)
            card.addView(Ui.badge(c, if (v.ok) "Verified" else "Mismatch", if (v.ok) Ui.emerald else Ui.red))
            card.addView(Ui.text(c, name, 14f, bold = true).apply { setPadding(0, Ui.dp(c, 6), 0, 0) })
            card.addView(Ui.dimText(c, v.text))
            body.addView(card)
        }
        body.addView(Ui.dimText(c, "Ye files phone se bahar nahi jati: copy aapke chune folder me hoti hai. Mismatch ho to wo adhoori file wahin rehti hai (hatayi nahi jati): usse khud dekhkar hatayein aur dobara copy karein."))
    }
}
