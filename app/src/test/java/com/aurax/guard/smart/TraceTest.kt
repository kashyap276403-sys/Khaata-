package com.aurax.guard.smart

import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceTest {
    private val cfg = TraceCfg(true, 40, 60, 15)
    private val H = 3_600_000L

    @Test fun startsOnceBelowThreshold() {
        val (s, a) = TraceRules.step(TraceState(), 38, false, 1000, cfg)
        assertEquals(listOf(TraceAction.START), a); assertTrue(s.active)
        val (_, a2) = TraceRules.step(s, 37, false, 2000, cfg)
        assertTrue(a2.isEmpty())
    }

    @Test fun periodicAfterInterval() {
        val (s, _) = TraceRules.step(TraceState(), 38, false, 0, cfg)
        val (s2, a) = TraceRules.step(s, 35, false, H, cfg)
        assertEquals(listOf(TraceAction.PERIODIC), a); assertEquals(H, s2.lastTraceMs)
    }

    @Test fun criticalOncePerCycleAndReplacesPeriodic() {
        val (s, _) = TraceRules.step(TraceState(), 38, false, 0, cfg)
        val (s2, a) = TraceRules.step(s, 14, false, 10, cfg)
        assertEquals(listOf(TraceAction.CRITICAL), a)
        val (_, a2) = TraceRules.step(s2, 10, false, 20, cfg)
        assertTrue(a2.isEmpty())
    }

    @Test fun chargingPausesAndHighChargeResets() {
        val (s, _) = TraceRules.step(TraceState(), 38, false, 0, cfg)
        val (s2, a) = TraceRules.step(s, 30, true, 2 * H, cfg)
        assertTrue(a.isEmpty()); assertEquals(s, s2)
        val (s3, a3) = TraceRules.step(s2, 43, true, 3 * H, cfg)
        assertEquals(listOf(TraceAction.RESET), a3); assertFalse(s3.active); assertFalse(s3.criticalSent)
        // hysteresis: 41 is still above threshold but inside the band: no reset, no start
        val (_, a4) = TraceRules.step(s, 41, false, 5, cfg)
        assertTrue(a4.isEmpty())
    }

    @Test fun disabledOrUnknownDoesNothing() {
        assertTrue(TraceRules.step(TraceState(), 10, false, 0, cfg.copy(enabled = false)).second.isEmpty())
        assertTrue(TraceRules.step(TraceState(), -1, false, 0, cfg).second.isEmpty())
    }

    @Test fun nextAlarm() {
        val s = TraceState(true, 1000, false)
        assertEquals(1000 + H, TraceRules.nextTraceAt(s, 2000, cfg))
        assertEquals(H + 100_000 + 60_000, TraceRules.nextTraceAt(s, H + 100_000, cfg))
        assertNull(TraceRules.nextTraceAt(TraceState(), 0, cfg))
    }

    @Test fun trailNote() {
        val now = 10 * H
        assertTrue(TraceRules.note(null, emptyList(), now).contains("nahi mili"))
        val cur = Pt(now, 28.6200, 77.2000)
        assertTrue(TraceRules.note(cur, emptyList(), now).contains("pehla"))
        val n = TraceRules.note(cur, listOf(Pt(now - H, 28.6000, 77.2000)), now)
        assertTrue(n, n.contains("2 points") && n.contains("km"))
        assertTrue(TraceRules.note(cur, listOf(Pt(now - H, 28.6200, 77.2000)), now).contains("wahin"))
    }

    private fun input(type: AlertType, reason: String, wrong: Int = 0, sim: Boolean = false, falseMotion: Int = 0): SmartInput {
        val now = 1_000_000_000L
        val hist = mutableListOf<Hist>()
        repeat(wrong) { hist.add(Hist(now - 60_000L * (it + 1), AlertType.WRONG_UNLOCK, null, null)) }
        if (sim) hist.add(Hist(now - 120_000, AlertType.SIM, null, null))
        repeat(falseMotion) { hist.add(Hist(now - 3 * H - it * 60_000L, AlertType.MOTION, null, null)) }
        return SmartInput(type, reason, now, 14, 80, false, "WiFi", 20, null, null, hist)
    }

    @Test fun threatMatchesBands() {
        val low = RiskEngine.assess(input(AlertType.MANUAL, "test"))
        assertEquals(Risk.LOW, low.risk); assertTrue(low.threat in 0..34)
        val med = RiskEngine.assess(input(AlertType.WRONG_UNLOCK, "wrong"))
        assertEquals(Risk.MEDIUM, med.risk); assertTrue(med.threat in 35..69)
        val high = RiskEngine.assess(input(AlertType.SIM, "sim", sim = true))
        assertEquals(Risk.HIGH, high.risk); assertTrue(high.threat in 70..100)
        val fl = RiskEngine.assess(input(AlertType.MOTION, "jhatka", falseMotion = 4))
        assertTrue(fl.falseTriggerLikely); assertTrue(fl.threat < 35)
    }

    @Test fun traceAndDigestClassifyAndStayLow() {
        assertEquals(AlertType.TRACE, AlertType.classify("Emergency trace: battery 38%"))
        assertEquals(AlertType.TRACE, AlertType.classify("Guard Boost: snapshot 1/6"))
        assertEquals(AlertType.DIGEST, AlertType.classify("Daily digest"))
        assertEquals(AlertType.DIGEST, AlertType.classify("Heartbeat: guard chalu hai"))
        assertEquals(Risk.LOW, RiskEngine.assess(input(AlertType.DIGEST, "Daily digest")).risk)
    }

    private fun rec(t: Long, reason: String, risk: Int = 1, threat: Int = 40, lat: Double? = null, lon: Double? = null) =
        EventRecord(id = "i$t", timeMs = t, reason = reason, lat = lat, lon = lon, batteryPct = 50, charging = false, network = "WiFi", risk = risk, threat = threat)

    @Test fun incidentGroupsRecentAlerts() {
        val now = 100 * H
        assertNull(Insights.incident(emptyList(), now))
        assertNull(Insights.incident(listOf(rec(now - 1000, "Jhatka")), now))
        val inc = Insights.incident(listOf(rec(now - 20 * 60_000, "Jhatka", lat = 28.60, lon = 77.2), rec(now - 60_000, "SIM badla", 2, 85, 28.62, 77.2)), now)!!
        assertEquals(2, inc.count); assertEquals(85, inc.maxThreat); assertTrue(inc.movedMeters!! > 1000)
        assertNull(Insights.incident(listOf(rec(now - 20 * H, "Jhatka"), rec(now - 20 * H + 1000, "Jhatka")), now))
    }

    @Test fun digestText() {
        val now = 100 * H
        assertTrue(Insights.digest(emptyList(), now, 0, 80).contains("koi alert nahi"))
        val d = Insights.digest(listOf(rec(now - H, "Jhatka"), rec(now - 2 * H, "Jhatka")), now, 1, 55)
        assertTrue(d, d.contains("2 alerts") && d.contains("55%") && d.contains("baaki: 1"))
    }

    @Test fun modesAndReadiness() {
        assertEquals(4, Mode.ALL.size); assertTrue(Mode.SILENT.stealth && !Mode.SILENT.siren); assertTrue(Mode.NIGHT.park)
        val ok = ReadyInputs(true, true, true, true, true, true, true, true, true, true, true, true, true)
        assertTrue(FeatureReadiness.rows(ok).all { it.ok })
        val bad = FeatureReadiness.rows(ok.copy(bgLocation = false, cameraService = false))
        assertFalse(bad.first { it.feature.startsWith("Emergency Trace") }.ok)
        assertFalse(bad.first { it.feature.startsWith("Intruder") }.ok)
    }
}
