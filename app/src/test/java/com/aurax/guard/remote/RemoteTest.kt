package com.aurax.guard.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteTest {
    private val now = 1_800_000_000_000L
    private fun upd(chat: Long?, from: Long?, text: String, priv: Boolean = true) = Upd(1, chat, from, text, now / 1000, priv)

    @Test fun parse() {
        assertEquals(Cmd("loc", ""), RemoteRules.parse("/loc"))
        assertEquals(Cmd("loc", ""), RemoteRules.parse("  /LOC@MyBot "))
        assertEquals(Cmd("saver", "on"), RemoteRules.parse("/saver ON"))
        assertNull(RemoteRules.parse("loc")); assertNull(RemoteRules.parse("/")); assertNull(RemoteRules.parse(null))
        assertNull(RemoteRules.parse("/averyveryverylongcommandname"))
    }

    @Test fun onlyConfiguredPrivateChat() {
        assertTrue(RemoteRules.authorized(upd(8241507452, 8241507452, "/loc"), "8241507452"))
        assertFalse(RemoteRules.authorized(upd(111, 111, "/loc"), "8241507452"))
        assertFalse(RemoteRules.authorized(upd(8241507452, 999, "/loc"), "8241507452")) // someone else in that chat
        assertFalse(RemoteRules.authorized(upd(-100123, 8241507452, "/loc", false), "-100123")) // groups are ignored
        assertFalse(RemoteRules.authorized(upd(null, null, "/loc"), "8241507452"))
        assertFalse(RemoteRules.authorized(upd(5, 5, "/loc"), ""))
    }

    @Test fun freshness() {
        assertTrue(RemoteRules.fresh(now / 1000 - 60, now))
        assertFalse(RemoteRules.fresh(now / 1000 - 3600, now))
        assertFalse(RemoteRules.fresh(0, now))
    }

    @Test fun decisions() {
        fun d(n: String, m: Boolean = true, l: Boolean = true, p: Boolean = true, mo: Boolean = true, last: Long = 0, runs: Int = 0) =
            RemoteRules.decide(Cmd(n, ""), m, l, p, mo, last, runs, now)
        assertEquals(Decision.RUN, d("loc"))
        assertEquals(Decision.DENY_MASTER_OFF, d("loc", m = false))
        assertEquals(Decision.DENY_FEATURE_OFF, d("photo", p = false))
        assertEquals(Decision.DENY_FEATURE_OFF, d("trace", l = false))
        assertEquals(Decision.DENY_FEATURE_OFF, d("saver", mo = false))
        assertEquals(Decision.RUN, d("help", l = false, p = false, mo = false))
        assertEquals(Decision.COOLDOWN, d("photo", last = now - 30_000))
        assertEquals(Decision.RUN, d("photo", last = now - 61_000))
        assertEquals(Decision.RATE_LIMIT, d("status", runs = 20))
        assertEquals(Decision.UNKNOWN, d("delete")) // there is no such command
        assertEquals(Decision.UNKNOWN, d("guardoff"))
    }

    @Test fun adaptivePolling() {
        assertEquals(60_000L, RemoteRules.pollIntervalMs(true, false, false, 0))
        assertEquals(5 * 60_000L, RemoteRules.pollIntervalMs(false, false, false, 0))
        assertEquals(15 * 60_000L, RemoteRules.pollIntervalMs(false, false, true, 0))
        assertEquals(5 * 60_000L, RemoteRules.pollIntervalMs(true, false, true, 0))
        assertEquals(120_000L, RemoteRules.pollIntervalMs(true, false, false, 1))
        assertEquals(30 * 60_000L, RemoteRules.pollIntervalMs(false, false, true, 5))
        assertTrue(RemoteRules.nextPollAt(now, now, 60_000) >= now + 20_000)
    }

    @Test fun updatesParsing() {
        val body = """{"ok":true,"result":[
          {"update_id":10,"message":{"date":1800000000,"text":"/loc","chat":{"id":5,"type":"private"},"from":{"id":5}}},
          {"update_id":11,"edited_message":{"text":"x"}},
          {"update_id":12,"message":{"date":1800000000,"text":"hi","chat":{"id":-9,"type":"group"},"from":{"id":7}}}]}"""
        val u = RemoteRules.parseUpdates(body)
        assertEquals(3, u.size); assertEquals(12L, u.maxOf { it.id })
        assertTrue(u[0].isPrivate && u[0].chatId == 5L && u[0].text == "/loc")
        assertEquals("", u[1].text); assertFalse(u[2].isPrivate)
        assertTrue(RemoteRules.parseUpdates("garbage").isEmpty())
    }

    @Test fun texts() {
        val s = RemoteRules.statusText(RemoteRules.StatusIn("Adi-Main", 38, false, true, true, false, false, true, true, true, false, 2, false, "WiFi"))
        assertTrue(s, s.contains("38%") && s.contains("Battery saver: ON") && s.contains("Queue me baaki: 2") && s.contains("trace chal raha"))
        val h = RemoteRules.helpText(true, true, false, true)
        assertTrue(h.contains("/photo - photo + location [OFF]") && h.contains("delete ya protection band nahi"))
    }
}
