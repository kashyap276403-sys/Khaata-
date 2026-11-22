package com.aurax.guard.telegram

import com.aurax.guard.EventRecord
import org.junit.Assert.*
import org.junit.Test

class TelegramTest {
    private fun rec(sent: Boolean = false, att: Int = 0, t: Long = 1000, next: Long = 0) =
        EventRecord(id = "a", timeMs = t, reason = "x", batteryPct = 50, network = "wifi",
            sent = sent, attempts = att, nextTryMs = next)

    @Test fun tokenAndChat() {
        assertTrue(TelegramRules.isValidToken("123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw1"))
        assertFalse(TelegramRules.isValidToken("hello"))
        assertTrue(TelegramRules.isValidChatId("8241507452"))
        assertFalse(TelegramRules.isValidChatId("abc"))
    }
    @Test fun labelFallback() {
        assertEquals("Adi-Main", TelegramRules.cleanLabel("  Adi-Main ", "Pixel"))
        assertEquals("Pixel", TelegramRules.cleanLabel("", "Pixel"))
    }
    @Test fun backoffCaps() {
        assertEquals(30_000L, TelegramRules.backoffMs(0))
        assertEquals(3_600_000L, TelegramRules.backoffMs(20))
    }
    @Test fun dueRules() {
        assertTrue(TelegramRules.isDue(rec(), 0, 5000, false))
        assertFalse(TelegramRules.isDue(rec(sent = true), 0, 5000, true))
        assertFalse(TelegramRules.isDue(rec(next = 9000), 0, 5000, false))
        assertTrue(TelegramRules.isDue(rec(next = 9000), 0, 5000, true))
        assertFalse(TelegramRules.isDue(rec(t = 100), 500, 5000, true))
        assertFalse(TelegramRules.isDue(rec(att = TelegramRules.MAX_ATTEMPTS), 0, 5000, true))
    }
    @Test fun classify() {
        assertEquals(TgResult.Ok, TelegramClient.classify(200, "{\"ok\":true}"))
        assertTrue(TelegramClient.classify(401, "{}") is TgResult.Fail)
        assertTrue((TelegramClient.classify(401, "{}") as TgResult.Fail).global)
        assertTrue(TelegramClient.classify(500, "") is TgResult.Retry)
        val r = TelegramClient.classify(429, "{\"parameters\":{\"retry_after\":7}}") as TgResult.Retry
        assertEquals(7000L, r.afterMs)
    }
    @Test fun deviceInText() {
        assertTrue(TelegramFormat.text("Mummy-Phone", rec()).startsWith("Device: Mummy-Phone"))
        assertTrue(TelegramFormat.caption("X", rec()).length <= TelegramFormat.CAPTION_MAX)
    }
    @Test fun redact() {
        assertEquals("bad [token] here", TelegramRules.redact("bad abc:123 here", "abc:123"))
    }
    @Test fun oldJsonStillReads() {
        val r = EventRecord.fromJson(org.json.JSONObject(rec().toJson()).apply { remove("tgAtt"); remove("tgErr"); remove("tgNext") }.toString())
        assertEquals(0, r.attempts)
    }
}
