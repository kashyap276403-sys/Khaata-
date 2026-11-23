package com.aurax.guard.ai

import android.content.Context
import com.aurax.guard.EventRecord
import com.aurax.guard.EventStore
import com.aurax.guard.Prefs
import com.aurax.guard.cleaner.SizeFormat
import com.aurax.guard.telegram.TelegramConfig

/** Every function here is optional: on any problem it returns null and the normal alert goes out without AI. */
object AiSummarizer {
    const val MAX_TRIES = 2

    fun facts(ctx: Context, r: EventRecord, all: List<EventRecord>): EventFacts {
        val day = 24L * 3600 * 1000
        return EventFacts(
            type = r.reason,
            device = TelegramConfig.label(ctx),
            batteryPct = r.batteryPct,
            charging = r.charging,
            network = r.network,
            time = EventRecord.formatTime(r.timeMs),
            locationAccuracyM = r.accuracyM?.toInt(),
            hasLocation = r.mapsLink != null,
            mapsLink = if (AiConfig.shareLink(ctx)) r.mapsLink else null,
            triggersLast24h = all.count { r.timeMs - it.timeMs in 0..day },
        )
    }

    /** Called by the Telegram sender once per alert. Returns the alert with an AI line when possible. */
    fun ensure(ctx: Context, r: EventRecord): EventRecord {
        if (r.aiSummary.isNotBlank() || r.aiTries >= MAX_TRIES || !AiConfig.ready(ctx)) return r
        val key = AiConfig.key(ctx) ?: return r
        val all = try { EventStore.readAll(ctx) } catch (_: Exception) { emptyList() }
        val f = facts(ctx, r, all)
        val res = AiClient.complete(key, AiConfig.model(ctx), AiPrompt.alertSystem(AiConfig.lang(ctx), AiConfig.style(ctx)), AiPrompt.alertUser(f))
        return when (res) {
            is AiResult.Ok -> {
                val text = AiPrompt.sanitize(res.text)
                AiConfig.setStatus(ctx, "Summary ban gayi")
                EventStore.update(ctx, r.id) { it.copy(aiSummary = text) }
                r.copy(aiSummary = text)
            }
            is AiResult.Err -> {
                val why = AiPrompt.redact(res.why, key)
                AiConfig.setStatus(ctx, "AI nahi chali: $why (normal alert gaya)")
                Prefs.log(ctx, "AI summary skip: $why")
                val tries = r.aiTries + 1
                EventStore.update(ctx, r.id) { it.copy(aiTries = tries) }
                r.copy(aiTries = tries)
            }
        }
    }

    /** Explains one scanner finding. Only name, size and the app's own warning signs are sent. */
    fun explainFile(ctx: Context, name: String, size: Long, reasons: List<String>): AiResult {
        val key = AiConfig.key(ctx) ?: return AiResult.Err("API key saved nahi", false)
        val res = AiClient.complete(key, AiConfig.model(ctx), AiPrompt.fileSystem(AiConfig.lang(ctx)), AiPrompt.fileUser(name, SizeFormat.bytes(size), reasons), 160)
        return if (res is AiResult.Ok) AiResult.Ok(AiPrompt.sanitize(res.text, 400)) else res
    }

    fun explainHints(ctx: Context, hints: List<String>): AiResult {
        val key = AiConfig.key(ctx) ?: return AiResult.Err("API key saved nahi", false)
        val res = AiClient.complete(key, AiConfig.model(ctx), AiPrompt.hintsSystem(AiConfig.lang(ctx)), AiPrompt.hintsUser(hints), 240)
        return if (res is AiResult.Ok) AiResult.Ok(AiPrompt.sanitize(res.text, 600)) else res
    }
}
