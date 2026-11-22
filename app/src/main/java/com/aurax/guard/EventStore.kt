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

    /** For the Telegram step later: marks an entry as delivered. */
    @Synchronized
    fun markSent(ctx: Context, id: String) {
        val key = PhotoCrypto.keystoreKey()
        val all = readAll(ctx).reversed().map { if (it.id == id) it.copy(sent = true) else it }
        file(ctx).writeText(all.joinToString("") { encodeLine(key, it) + "\n" })
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
