package com.aurax.guard.telegram

import android.content.Context
import android.os.Build
import android.util.Base64
import com.aurax.guard.EventRecord
import com.aurax.guard.PhotoCrypto

/**
 * Telegram settings kept ONLY on this phone. The bot token is encrypted with a
 * key in the Android Keystore before it is saved, and is never written to
 * source code, logs or backups (allowBackup is off).
 */
object TelegramConfig {
    private fun p(c: Context) = c.getSharedPreferences("tg", Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)

    /** Turning ON starts a fresh "since" time so old test entries are not flooded to Telegram. */
    fun setEnabled(c: Context, v: Boolean) {
        val was = enabled(c)
        val e = p(c).edit().putBoolean("enabled", v)
        if (v && !was) e.putLong("since", System.currentTimeMillis())
        e.apply()
    }

    fun sinceMs(c: Context) = p(c).getLong("since", 0L)
    fun setSinceMs(c: Context, v: Long) = p(c).edit().putLong("since", v).apply()

    fun chatId(c: Context) = p(c).getString("chat", "") ?: ""
    fun setChatId(c: Context, v: String) = p(c).edit().putString("chat", TelegramRules.cleanChatId(v)).apply()

    /** The name shown as "Device: ..." in every alert. */
    fun label(c: Context): String =
        TelegramRules.cleanLabel(p(c).getString("label", "") ?: "", Build.MODEL ?: "Phone")

    fun rawLabel(c: Context) = p(c).getString("label", "") ?: ""
    fun setLabel(c: Context, v: String) = p(c).edit().putString("label", TelegramRules.cleanLabel(v, "")).apply()

    fun hasToken(c: Context) = !p(c).getString("token_enc", "").isNullOrEmpty()

    /** Last 4 characters only, so you can recognise which token is saved. */
    fun tokenTail(c: Context) = p(c).getString("token_tail", "") ?: ""

    fun token(c: Context): String? {
        return try {
        val enc = p(c).getString("token_enc", null) ?: return null
        String(PhotoCrypto.decrypt(PhotoCrypto.keystoreKey(), Base64.decode(enc, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns false if the phone's secure storage could not be used. */
    fun setToken(c: Context, raw: String): Boolean = try {
        val t = TelegramRules.cleanToken(raw)
        val enc = Base64.encodeToString(PhotoCrypto.encrypt(PhotoCrypto.keystoreKey(), t.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        p(c).edit().putString("token_enc", enc).putString("token_tail", t.takeLast(4)).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun clearToken(c: Context) = p(c).edit().remove("token_enc").remove("token_tail").apply()

    fun configured(c: Context) = enabled(c) && hasToken(c) && TelegramRules.isValidChatId(chatId(c))

    fun setStatus(c: Context, text: String) =
        p(c).edit().putString("status", text).putLong("status_ms", System.currentTimeMillis()).apply()

    fun status(c: Context): String {
        val s = p(c).getString("status", "") ?: ""
        if (s.isBlank()) return "Abhi tak kuch nahi bheja"
        return EventRecord.formatTime(p(c).getLong("status_ms", 0L)) + "  " + s
    }

    /** Short line for Home and the settings list. */
    fun summary(c: Context): String = when {
        !hasToken(c) || chatId(c).isBlank() -> "Setup baaki: BOT_TOKEN aur CHAT_ID daalein"
        !enabled(c) -> "Band hai (device: ${label(c)})"
        else -> "Chalu (device: ${label(c)})"
    }
}
