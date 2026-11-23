package com.aurax.guard.cleaner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

data class ScanItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val modifiedMs: Long,
    val uri: Uri?,
    val file: File?,
    val verdict: Verdict,
)

/** Read-only listing. Scanning never changes or deletes anything. */
object TreeScanner {
    const val MAX_ITEMS = 5000
    private const val MAX_DEPTH = 6

    /** A folder the user picked (Storage Access Framework, no extra permission needed). */
    fun scanTree(ctx: Context, tree: Uri, cancelled: () -> Boolean): List<ScanItem> {
        val out = mutableListOf<ScanItem>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        walk(ctx.contentResolver, tree, root, "", 0, out, cancelled)
        return out
    }

    private fun walk(cr: ContentResolver, tree: Uri, docId: String, rel: String, depth: Int, out: MutableList<ScanItem>, cancelled: () -> Boolean) {
        if (depth > MAX_DEPTH || out.size >= MAX_ITEMS || cancelled()) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        try {
            cr.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                val dirs = mutableListOf<Pair<String, String>>()
                while (c.moveToNext() && out.size < MAX_ITEMS && !cancelled()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: "?"
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs.add(id to name)
                        continue
                    }
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val mod = if (c.isNull(4)) 0L else c.getLong(4)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    out.add(ScanItem(uri.toString(), name, rel + name, size, mod, uri, null, FileRules.assess(name, size)))
                }
                dirs.forEach { (id, name) -> walk(cr, tree, id, "$rel$name/", depth + 1, out, cancelled) }
            }
        } catch (_: Exception) {
            // a folder we cannot read is simply skipped
        }
    }

    /** This app's own cache (safe, always allowed). */
    fun scanOwnCache(ctx: Context): List<ScanItem> {
        val out = mutableListOf<ScanItem>()
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { root ->
            root.walkTopDown().filter { it.isFile }.take(MAX_ITEMS).forEach { f ->
                val v = Verdict(FileKind.TEMP, FileLevel.INFO, listOf("auraX Guard ka apna cache (dobara ban jata hai)"))
                out.add(ScanItem("file:" + f.absolutePath, f.name, f.relativeTo(root).path, f.length(), f.lastModified(), null, f, v))
            }
        }
        return out
    }

    /** Deletes ONE item the user selected and confirmed. Returns true on success. */
    fun delete(ctx: Context, item: ScanItem): Boolean = try {
        when {
            item.uri != null -> DocumentsContract.deleteDocument(ctx.contentResolver, item.uri)
            item.file != null -> item.file.delete()
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}
