package com.aurax.guard.ai

import com.aurax.guard.EventRecord
import com.aurax.guard.telegram.*
import org.junit.Assert.*
import org.junit.Test

class AiTest {
    private val facts = EventFacts("Jhatka pakda gaya", "Adi-Main", 42, false, "wifi", "t", 18, true, null, 3)

    @Test fun promptHasFactsButNoLinkByDefault() {
        val u = AiPrompt.alertUser(facts)
        assertTrue(u.contains("Adi-Main") && u.contains("42%") && u.contains("18 m") && u.contains("last 24 hours: 3"))
        assertFalse(u.contains("maps"))
        assertTrue(AiPrompt.alertUser(facts.copy(mapsLink = "https://maps.google.com/?q=1,2")).contains("Map link"))
    }
    @Test fun systemPromptsForbidDeleteAdvice() = assertTrue(AiPrompt.fileSystem(AiLang.HINGLISH).contains("never say 'delete it'"))
    @Test fun sanitizeCleansAndCaps() {
        assertEquals("Phone chheena gaya", AiPrompt.sanitize("**Phone**\n  chheena   gaya"))
        assertTrue(AiPrompt.sanitize("a".repeat(900)).length <= AiPrompt.MAX_SUMMARY)
    }
    @Test fun redactKey() = assertEquals("bad [key] x", AiPrompt.redact("bad sk-123 x", "sk-123"))
    @Test fun classify() {
        assertEquals("hi", (AiClient.classify(200, "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}") as AiResult.Ok).text)
        assertFalse((AiClient.classify(401, "{}") as AiResult.Err).retryable)
        assertTrue((AiClient.classify(529, "{}") as AiResult.Err).retryable)
        assertTrue(AiClient.classify(200, "{\"content\":[]}") is AiResult.Err)
    }
    @Test fun langStyleDefaults() { assertEquals(AiLang.HINGLISH, AiLang.of("zzz")); assertEquals(AiStyle.SHORT, AiStyle.of("")) }

    private fun rec(ai: String = "") = EventRecord(id = "a", timeMs = 1, reason = "x", aiSummary = ai)
    @Test fun aiLineIsOptionalAndCaptionFits() {
        assertFalse(TelegramFormat.text("L", rec()).contains("AI:"))
        assertTrue(TelegramFormat.text("L", rec("khatra kam")).endsWith("AI: khatra kam"))
        val long = rec("z".repeat(300)).copy(reason = "r".repeat(2000))
        assertTrue(TelegramFormat.caption("L", long).length <= TelegramFormat.CAPTION_MAX)
        assertTrue(TelegramFormat.caption("L", long).contains("AI: "))
    }
    @Test fun oldJsonHasNoAi() = assertEquals("", EventRecord.fromJson("{\"id\":\"a\",\"timeMs\":1,\"reason\":\"x\"}").aiSummary)

    @Test fun badges() {
        val base = rec()
        assertEquals(BadgeKind.SENT, Badges.forRecord(base.copy(sent = true), true, 0, true).kind)
        assertEquals(BadgeKind.LOCAL, Badges.forRecord(base, false, 0, true).kind)
        assertEquals(BadgeKind.QUEUED, Badges.forRecord(base.copy(timeMs = 5), true, 0, true).kind)
        assertEquals(BadgeKind.OFFLINE, Badges.forRecord(base.copy(timeMs = 5), true, 0, false).kind)
        assertEquals(BadgeKind.ERROR, Badges.forRecord(base.copy(timeMs = 5, lastError = "BOT_TOKEN galat hai (401)", attempts = 1), true, 0, true).kind)
        assertEquals(BadgeKind.ERROR, Badges.forRecord(base.copy(timeMs = 5, attempts = TelegramRules.MAX_ATTEMPTS), true, 0, true).kind)
    }

    @Test fun advisorSuggestsLowerSensitivityOnManyAlerts() {
        val now = 10_000_000L
        val ev = (1..5).map { EventRecord(id = "$it", timeMs = now - it * 1000L, reason = "Jhatka pakda gaya") }
        val h = SensitivityAdvisor.hints(ev, now, 0, false, true)
        assertTrue(h.any { it.contains("Madhyam") })
        assertTrue(SensitivityAdvisor.hints(emptyList(), now, 1, false, true).isNotEmpty())
        assertTrue(SensitivityAdvisor.hints(emptyList(), now, 1, false, false).any { it.contains("band") })
    }
}
