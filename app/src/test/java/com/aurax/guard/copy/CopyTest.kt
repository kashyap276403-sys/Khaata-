package com.aurax.guard.copy

import com.aurax.guard.EventRecord
import com.aurax.guard.smart.LockSnap
import com.aurax.guard.smart.LockdownRules
import com.aurax.guard.smart.Timeline
import com.aurax.guard.remote.Cmd
import com.aurax.guard.remote.Decision
import com.aurax.guard.remote.RemoteRules
import org.junit.Assert.*
import org.junit.Test

class CopyTest {
    @Test fun verdicts() {
        assertEquals(Verify.OK, CopyRules.verdict(100, 100, 100, 7, 7))
        assertEquals(Verify.SIZE_OK_ONLY, CopyRules.verdict(100, 100, -1, 7, null))
        assertEquals(Verify.SIZE_MISMATCH, CopyRules.verdict(100, 90, 90, 7, 7))
        assertEquals(Verify.SIZE_MISMATCH, CopyRules.verdict(100, 100, 80, 7, 7))
        assertEquals(Verify.CRC_MISMATCH, CopyRules.verdict(100, 100, 100, 7, 8))
        assertFalse(Verify.SIZE_MISMATCH.ok); assertTrue(Verify.OK.ok)
        assertEquals(Verify.OK, CopyRules.verdict(-1, 50, 50, 1, 1))
    }

    @Test fun importantAndChat() {
        assertTrue(CopyRules.isImportant("Report.PDF", 10)); assertFalse(CopyRules.isImportant("a.pdf", 0)); assertFalse(CopyRules.isImportant("a.tmp", 5))
        assertTrue(CopyRules.isChatExport("WhatsApp Chat with Mummy.zip")); assertFalse(CopyRules.isChatExport("holiday.zip"))
        assertTrue(CopyRules.sameFolder("content://a/tree/x", " content://a/tree/x "))
        assertTrue(CopyRules.summary(listOf("a" to Verify.OK, "b" to Verify.CRC_MISMATCH)).contains("1 me GADBAD"))
    }

    @Test fun lockdownSnapshotAndRestore() {
        val mine = LockSnap(false, true, false, false, false, false, false, false, true)
        val first = LockdownRules.snapshotToKeep(false, null, mine)
        assertEquals(mine, LockSnap.fromJson(first))
        // entering again while active must keep the ORIGINAL snapshot, not the lockdown values
        assertEquals(first, LockdownRules.snapshotToKeep(true, first, LockdownRules.TARGET))
        assertEquals(mine, LockdownRules.restoreTarget(first))
        val t = LockdownRules.TARGET
        assertTrue(t.charger && t.sim && t.wrongUnlock && t.stealth && t.tracePhoto && t.traceOn && t.saverAuto && !t.ignoreCharging)
        // lost snapshot -> stay on the safe side (protections ON, not guessed OFF)
        val safe = LockdownRules.restoreTarget("")
        assertTrue(safe.charger && safe.sim && safe.wrongUnlock && !safe.stealth)
        assertNull(LockSnap.fromJson("bad"))
    }

    @Test fun remoteLockdownCommands() {
        fun d(n: String, mode: Boolean = true) = RemoteRules.decide(Cmd(n, ""), true, true, true, mode, 0, 0, 1)
        assertEquals(Decision.RUN, d("lockdown")); assertEquals(Decision.RUN, d("normal"))
        assertEquals(Decision.DENY_FEATURE_OFF, d("lockdown", false))
        assertEquals(Decision.UNKNOWN, d("guardoff"))
        assertTrue(RemoteRules.helpText(true, true, true, true).contains("/lockdown"))
    }

    @Test fun timeline() {
        val r = EventRecord(id = "1", timeMs = 1_800_000_000_000L, reason = "SIM badla gaya", lat = 28.6, lon = 77.2, batteryPct = 41, charging = true,
            network = "WiFi", threat = 85, guard = "guard ON+lockdown")
        val a = Timeline.line(r, false); val b = Timeline.line(r, true)
        assertTrue(a, a.contains("41%+") && a.contains("WiFi") && a.contains("guard ON+lockdown") && a.contains("T85") && !a.contains("maps"))
        assertTrue(b.contains("google.com/maps") || b.contains("maps"))
        val e = Timeline.export(listOf(r), "Adi-Main", 1_800_000_100_000L, false)
        assertTrue(e.contains("Adi-Main") && e.contains("1 events") && e.contains("location: nahi"))
        assertTrue(Timeline.export(emptyList(), "X", 0, false).contains("Koi event nahi"))
        assertEquals("guard ON+lockdown+saver", Timeline.guardTag(true, true, true, true))
        assertEquals("guard OFF", Timeline.guardTag(false, false, false, false))
        // old records without the field still parse
        assertEquals("", EventRecord.fromJson(r.copy(guard = "").toJson().toString().replace(",\"gs\":\"\"", "")).guard)
    }
}
