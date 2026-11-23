package com.aurax.guard

import android.content.Context
import java.io.File
import java.util.Base64
import javax.crypto.SecretKey

/**
 * Persistent local queue of [EventRecord]s. One encrypted line per event
 * (AES-256-GCM, key in Android Keystore), so location history is unreadable
 * even if the phone's storage is copied. Nothing is lost when offline.
 */
object EventStore {
    private const val MAX = 500

    private fun file(ctx: Context): File =
        File(File(ctx.filesDir, "events").apply { mkdirs() }, "queue.log")

    fun encodeLine(key: SecretKey, r: EventRecord): String =
        Base64.getEncoder().encodeToString(PhotoCrypto.encrypt(key, r.toJson().toByteArray(Charsets.UTF_8)))

    fun decodeLine(key: SecretKey, line: String): EventRecord? = try {
        EventRecord.fromJson(String(PhotoCrypto.decrypt(key, Base64.getDecoder().decode(line.trim())), Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun append(ctx: Context, r: EventRecord) {
        val f = file(ctx)
        val line = encodeLine(PhotoCrypto.keystoreKey(), r)
        f.appendText(line + "\n")
        val lines = f.readLines().filter { it.isNotBlank() }
        if (lines.size > MAX) f.writeText(lines.takeLast(MAX).joinToString("\n") + "\n")
    }

    /** Newest first. Unreadable lines are skipped. */
    @Synchronized
    fun readAll(ctx: Context): List<EventRecord> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val key = try { PhotoCrypto.keystoreKey() } catch (_: Exception) { return emptyList() }
        return f.readLines().mapNotNull { if (it.isBlank()) null else decodeLine(key, it) }.reversed()
    }

    fun pendingCount(ctx: Context) = readAll(ctx).count { !it.sent }

    /** Marks an entry as delivered. */
    fun markSent(ctx: Context, id: String) = update(ctx, id) { it.copy(sent = true) }

    /**
     * Changes one entry in place. Lines that cannot be read (for example after a
     * Keystore problem) are kept exactly as they are instead of being lost.
     */
    @Synchronized
    fun update(ctx: Context, id: String, change: (EventRecord) -> EventRecord) {
        val f = file(ctx)
        if (!f.exists()) return
        val key = try { PhotoCrypto.keystoreKey() } catch (_: Exception) { return }
        var hit = false
        val out = f.readLines().filter { it.isNotBlank() }.map { line ->
            val r = decodeLine(key, line)
            if (r != null && r.id == id) { hit = true; encodeLine(key, change(r)) } else line
        }
        if (hit) writeAtomic(f, out)
    }

    /** Removes entries the user chose to clear (only those matching [pred]). Unreadable lines are kept. Returns how many were removed. */
    @Synchronized
    fun removeWhere(ctx: Context, pred: (EventRecord) -> Boolean): Int {
        val f = file(ctx)
        if (!f.exists()) return 0
        val key = try { PhotoCrypto.keystoreKey() } catch (_: Exception) { return 0 }
        var n = 0
        val out = f.readLines().filter { it.isNotBlank() }.filter { line ->
            val r = decodeLine(key, line)
            val drop = r != null && pred(r)
            if (drop) n++
            !drop
        }
        if (n > 0) writeAtomic(f, out)
        return n
    }

    /** Write to a temp file first so a crash mid-write cannot corrupt the queue. */
    private fun writeAtomic(f: File, lines: List<String>) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n")
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
