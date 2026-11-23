package com.aurax.guard.ai

import android.content.Context
import android.util.Base64
import com.aurax.guard.EventRecord
import com.aurax.guard.PhotoCrypto

/** Optional AI settings, kept only on this phone. The API key is encrypted with the Android Keystore and never put in source code. */
object AiConfig {
    const val DEFAULT_MODEL = "claude-haiku-5-5"
    private fun p(c: Context) = c.getSharedPreferences("ai", Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun model(c: Context) = p(c).getString("model", DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
    fun setModel(c: Context, v: String) = p(c).edit().putString("model", v.trim().take(60)).apply()

    fun lang(c: Context) = AiLang.of(p(c).getString("lang", "") ?: "")
    fun setLang(c: Context, l: AiLang) = p(c).edit().putString("lang", l.key).apply()
    fun style(c: Context) = AiStyle.of(p(c).getString("style", "") ?: "")
    fun setStyle(c: Context, s: AiStyle) = p(c).edit().putString("style", s.key).apply()

    /** Sending the Google Maps link to the AI provider is OFF unless you allow it. */
    fun shareLink(c: Context) = p(c).getBoolean("share_link", false)
    fun setShareLink(c: Context, v: Boolean) = p(c).edit().putBoolean("share_link", v).apply()

    fun hasKey(c: Context) = !p(c).getString("key_enc", "").isNullOrEmpty()
    fun keyTail(c: Context) = p(c).getString("key_tail", "") ?: ""

    fun key(c: Context): String? {
        return try {
            val enc = p(c).getString("key_enc", null) ?: return null
            String(PhotoCrypto.decrypt(PhotoCrypto.keystoreKey(), Base64.decode(enc, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun setKey(c: Context, raw: String): Boolean = try {
        val t = raw.trim().replace(Regex("\\s+"), "")
        val enc = Base64.encodeToString(PhotoCrypto.encrypt(PhotoCrypto.keystoreKey(), t.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        p(c).edit().putString("key_enc", enc).putString("key_tail", t.takeLast(4)).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun clearKey(c: Context) = p(c).edit().remove("key_enc").remove("key_tail").putBoolean("enabled", false).apply()

    /** AI is only used when it is switched on AND a key is saved. Anything else = normal alerts. */
    fun ready(c: Context) = enabled(c) && hasKey(c)

    fun setStatus(c: Context, s: String) = p(c).edit().putString("status", s).putLong("status_ms", System.currentTimeMillis()).apply()
    fun status(c: Context): String {
        val s = p(c).getString("status", "") ?: ""
        return if (s.isBlank()) "Abhi tak AI use nahi hua" else EventRecord.formatTime(p(c).getLong("status_ms", 0L)) + "  " + s
    }
}
