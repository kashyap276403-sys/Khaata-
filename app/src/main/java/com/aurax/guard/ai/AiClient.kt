package com.aurax.guard.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    data class Err(val why: String, val retryable: Boolean) : AiResult()
}

/** Plain HTTPS call to the Anthropic Messages API. Never throws. */
object AiClient {
    private const val URL_API = "https://api.anthropic.com/v1/messages"

    fun classify(http: Int, body: String): AiResult {
        val json = try { JSONObject(body) } catch (_: Exception) { null }
        if (http == 200 && json != null) {
            val arr = json.optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("type") == "text") sb.append(o.optString("text"))
            }
            val t = sb.toString().trim()
            return if (t.isNotEmpty()) AiResult.Ok(t) else AiResult.Err("AI ne khaali jawab diya", true)
        }
        val msg = json?.optJSONObject("error")?.optString("message").orEmpty()
        return when {
            http == 401 || http == 403 -> AiResult.Err("API key galat ya bina permission ($http)", false)
            http == 404 -> AiResult.Err("Model ka naam galat ho sakta hai", false)
            http == 400 -> AiResult.Err("AI ne request mana ki: ${msg.take(80)}", false)
            http == 429 -> AiResult.Err("AI limit/rate: thodi der baad", true)
            http >= 500 -> AiResult.Err("AI server me dikkat ($http)", true)
            else -> AiResult.Err("Anjaan jawab ($http)", true)
        }
    }

    fun complete(key: String, model: String, system: String, user: String, maxTokens: Int = 220, timeoutMs: Int = 12_000): AiResult {
        var conn: HttpURLConnection? = null
        return try {
            val payload = JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                .toString()
            conn = (URL(URL_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = timeoutMs
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", key)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            classify(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: IOException) {
            AiResult.Err("Internet nahi ya AI tak nahi pahunch paya", true)
        } catch (e: Exception) {
            AiResult.Err("AI error (${e.javaClass.simpleName})", true)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
