package com.aurax.guard.telegram

import android.content.Context
import com.aurax.guard.PhotoCrypto
import com.aurax.guard.SelfieCapture

/** Reads a saved intruder photo (decrypting it if needed) so it can be sent. Read-only. */
object PhotoReader {
    fun read(ctx: Context, photoName: String): ByteArray? {
        return try {
        val p = SelfieCapture.listPhotos(ctx).firstOrNull { it.name == photoName } ?: return null
        val raw = ctx.contentResolver.openInputStream(p.uri)?.use { it.readBytes() } ?: return null
        if (p.encrypted) PhotoCrypto.decrypt(PhotoCrypto.keystoreKey(), raw) else raw
    } catch (_: Exception) {
        null
        }
    }
}
