package com.aurax.guard.smart

import com.aurax.guard.AlertType
import com.aurax.guard.cleaner.*
import org.junit.Assert.*
import org.junit.Test

class SmartTest {
    private val now = 100L * 3600 * 1000
    private fun inp(type: AlertType, reason: String = "x", hist: List<Hist> = emptyList(), hour: Int = 14, batt: Int = 60, lat: Double? = 28.6, lon: Double? = 77.2, acc: Int? = 20) =
        SmartInput(type, reason, now, hour, batt, false, "wifi", acc, lat, lon, hist)

    @Test fun lonelyMotionIsLow() = assertEquals(Risk.LOW, RiskEngine.assess(inp(AlertType.MOTION)).risk)

    @Test fun repeatedUncorroboratedMotionIsFalseTrigger() {
        val h = (1..4).map { Hist(now - it * 600_000L, AlertType.MOTION, 28.6, 77.2) }
        val a = RiskEngine.assess(inp(AlertType.MOTION, hist = h))
        assertTrue(a.falseTriggerLikely); assertEquals(Risk.LOW, a.risk)
        assertTrue(a.actions.first().contains("Sensitivity"))
    }

    @Test fun simChangeIsHigh() = assertEquals(Risk.HIGH, RiskEngine.assess(inp(AlertType.SIM, "SIM ALERT")).risk)

    @Test fun wrongPasswordsEscalate() {
        val h = (1..2).map { Hist(now - it * 120_000L, AlertType.WRONG_UNLOCK, null, null) }
        assertEquals(Risk.MEDIUM, RiskEngine.assess(inp(AlertType.WRONG_UNLOCK)).risk)
        assertEquals(Risk.HIGH, RiskEngine.assess(inp(AlertType.WRONG_UNLOCK, hist = h, hour = 2)).risk)
    }

    @Test fun movementMakesMotionSerious() {
        val h = listOf(Hist(now - 600_000L, AlertType.MOTION, 28.60, 77.20))
        val a = RiskEngine.assess(inp(AlertType.MOTION, hist = h, lat = 28.65, lon = 77.25))
        assertTrue(a.movedMeters!! > 300); assertTrue(a.risk != Risk.LOW)
    }

    @Test fun lowBatteryAndNoLocationAdvice() {
        val a = RiskEngine.assess(inp(AlertType.WRONG_UNLOCK, batt = 9, lat = null, lon = null, acc = null))
        assertTrue(a.summary.contains("9%")); assertTrue(a.actions.any { it.contains("Location ON") })
    }

    @Test fun lineIsShortAndEnglishWorks() {
        val a = RiskEngine.assess(inp(AlertType.SIM).copy(english = true))
        assertTrue(a.summary.contains("SIM change")); assertTrue(a.line().length <= 240)
    }

    @Test fun distance() = assertTrue(RiskEngine.distanceM(28.6, 77.2, 28.6, 77.2) < 1.0)

    @Test fun autoTuneNeedsFourUncorroboratedAndWaitsADay() {
        val h = (1..4).map { Hist(now - it * 3_600_000L, AlertType.MOTION, 28.6, 77.2) }
        val t = AutoTuner.suggest(h, now, 0, 10, 0)!!
        assertEquals(1, t.newLevel)
        assertNull(AutoTuner.suggest(h, now, 0, 10, now - 1000)) // changed recently
        assertNull(AutoTuner.suggest(h.take(3), now, 0, 10, 0)) // too few
        assertNull(AutoTuner.suggest(h + Hist(now - 1000, AlertType.WRONG_UNLOCK, null, null), now, 0, 10, 0)) // backed by real sign
        assertNull(AutoTuner.suggest(h.take(2) + Hist(now - 5000, AlertType.MOTION, 28.9, 77.6) + Hist(now - 6000, AlertType.MOTION, 28.6, 77.2), now, 0, 10, 0)) // phone travelled
    }

    @Test fun autoTuneNeverRaisesSensitivityAndIsCapped() {
        val h = (1..4).map { Hist(now - it * 3_600_000L, AlertType.MOTION, null, null) }
        assertEquals(30, AutoTuner.suggest(h, now, 2, 20, 0)!!.newGrace)
        assertNull(AutoTuner.suggest(h, now, 2, 30, 0))
        assertTrue(AutoTuner.suggest(h, now, 1, 10, 0)!!.newLevel > 1)
    }

    @Test fun fileExplanationNeverTellsToDelete() {
        listOf("a.apk", "x.pdf.apk", "t.tmp", "big.mp4").forEach {
            val e = FileExplain.explain(it, FileRules.assess(it, if (it == "big.mp4") 90L * 1024 * 1024 else 10))
            assertFalse(e.lowercase().contains("delete kar"))
            assertTrue(e.contains("App khud kuch delete nahi karti"))
        }
        assertTrue(FileExplain.overview(listOf(FileRules.assess("a.apk", 1))).contains("APK"))
    }
}
