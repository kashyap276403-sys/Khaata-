package com.aurax.guard.telegram

import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

sealed class TgResult {
    object Ok : TgResult()

    /** Worth trying again later (no internet, Telegram busy). */
    data class Retry(val why: String, val afterMs: Long = 0) : TgResult()

    /** Will not work by retrying. [global] = wrong token/chat, so stop all sending. */
    data class Fail(val why: String, val global: Boolean) : TgResult()
}

/** Plain HTTPS calls to the Telegram Bot API. No extra libraries. */
object TelegramClient {
    private const val API = "https://api.telegram.org"

    /** Decides what a Telegram reply means. Pure, unit tested. */
    fun classify(http: Int, body: String): TgResult {
        val json = try { JSONObject(body) } catch (_: Exception) { null }
        val ok = json?.optBoolean("ok", false) ?: false
        val desc = json?.optString("description", "") ?: ""
        if (http == 200 && ok) return TgResult.Ok
        val retryAfter = json?.optJSONObject("parameters")?.optInt("retry_after", 0) ?: 0
        return when {
            http == 429 -> TgResult.Retry("Telegram ne thodi der rukne ko kaha", retryAfter * 1000L)
            http >= 500 -> TgResult.Retry("Telegram server me dikkat ($http)")
            http == 401 || http == 404 -> TgResult.Fail("BOT_TOKEN galat hai ($http)", true)
            http == 403 -> TgResult.Fail("Bot is chat me bhej nahi sakta. Telegram me apne bot ko /start bhejein.", true)
            http == 400 && desc.contains("chat not found", ignoreCase = true) ->
                TgResult.Fail("CHAT_ID galat hai, ya bot ne chat shuru nahi ki", true)
            http == 400 -> TgResult.Fail("Telegram ne message mana kiya: $desc", false)
            else -> TgResult.Retry("Anjaan jawab ($http)")
        }
    }

    private fun call(token: String, method: String, send: (HttpURLConnection) -> Unit): TgResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$API/bot$token/$method").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 25_000
                requestMethod = "POST"
                doOutput = true
            }
            send(conn)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            classify(code, body)
        } catch (e: IOException) {
            TgResult.Retry("Internet nahi ya Telegram tak nahi pahunch paya (${e.javaClass.simpleName})")
        } catch (e: Exception) {
            TgResult.Retry("Network error (${e.javaClass.simpleName})")
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    fun sendMessage(token: String, chatId: String, text: String): TgResult = call(token, "sendMessage") { c ->
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        val form = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
            "&text=" + URLEncoder.encode(text, "UTF-8") +
            "&disable_web_page_preview=true"
        c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
    }

    /** Sends a photo with a caption (one message per alert). */
    fun sendPhoto(token: String, chatId: String, caption: String, jpeg: ByteArray): TgResult =
        sendFile(token, "sendPhoto", "photo", chatId, caption, jpeg, "alert.jpg", "image/jpeg")

    /** For the audio clip step later. */
    fun sendAudioFile(token: String, chatId: String, caption: String, bytes: ByteArray, name: String, mime: String): TgResult =
        sendFile(token, "sendDocument", "document", chatId, caption, bytes, name, mime)

    private fun sendFile(
        token: String, method: String, field: String, chatId: String, caption: String,
        bytes: ByteArray, name: String, mime: String,
    ): TgResult = call(token, method) { c ->
        val boundary = "----auraX" + System.nanoTime()
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(c.outputStream).use { out ->
            fun part(n: String, v: String) {
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n")
                out.write(v.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            part("chat_id", chatId)
            part("caption", caption)
            out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$name\"\r\nContent-Type: $mime\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary--\r\n")
        }
    }
}
