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

    // ---- extra bots / chats (additive; the primary above keeps working on its own) ----
    const val MAX_EXTRA = 3
    const val PRIMARY_ID = "main"

    data class Target(val id: String, val name: String, val chatId: String, val token: String)

    private fun extraJson(c: Context): org.json.JSONArray =
        try { org.json.JSONArray(p(c).getString("extra", "[]")) } catch (_: Exception) { org.json.JSONArray() }

    /** Names and chats of the extra targets (no token). */
    fun extras(c: Context): List<Triple<String, String, String>> {
        val a = extraJson(c)
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Triple(o.getString("id"), o.optString("name"), o.optString("chat")) } }
    }

    fun addExtra(c: Context, name: String, chat: String, rawToken: String): Boolean = try {
        val a = extraJson(c)
        if (a.length() >= MAX_EXTRA) false else {
            val t = TelegramRules.cleanToken(rawToken)
            val enc = Base64.encodeToString(PhotoCrypto.encrypt(PhotoCrypto.keystoreKey(), t.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            a.put(org.json.JSONObject().put("id", "x" + System.currentTimeMillis().toString(36)).put("name", name.trim().take(24).ifBlank { "Extra" })
                .put("chat", TelegramRules.cleanChatId(chat)).put("tok", enc).put("tail", t.takeLast(4)).put("st", ""))
            p(c).edit().putString("extra", a.toString()).apply()
            true
        }
    } catch (_: Exception) {
        false
    }

    fun removeExtra(c: Context, id: String) {
        val a = extraJson(c)
        val out = org.json.JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) out.put(a.getJSONObject(i))
        p(c).edit().putString("extra", out.toString()).apply()
    }

    fun extraTail(c: Context, id: String): String {
        val a = extraJson(c)
        for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.getString("id") == id) return it.optString("tail") }
        return ""
    }

    fun setExtraStatus(c: Context, id: String, text: String) {
        val a = extraJson(c)
        for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.getString("id") == id) it.put("st", text) }
        p(c).edit().putString("extra", a.toString()).apply()
    }

    fun extraStatus(c: Context, id: String): String {
        val a = extraJson(c)
        for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.getString("id") == id) return it.optString("st") }
        return ""
    }

    /** Every place alerts go to: the primary first, then readable extras. */
    fun targets(c: Context): List<Target> {
        val out = mutableListOf<Target>()
        token(c)?.let { out.add(Target(PRIMARY_ID, "Primary", chatId(c), it)) }
        val a = extraJson(c)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            try {
                val tok = String(PhotoCrypto.decrypt(PhotoCrypto.keystoreKey(), Base64.decode(o.getString("tok"), Base64.NO_WRAP)), Charsets.UTF_8)
                out.add(Target(o.getString("id"), o.optString("name"), o.optString("chat"), tok))
            } catch (_: Exception) {
            }
        }
        return out
    }

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
