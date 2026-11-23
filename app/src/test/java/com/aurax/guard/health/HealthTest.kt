package com.aurax.guard.health

import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord
import com.aurax.guard.SnatchDetector
import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    private fun ok() = HealthInputs(true, true, true, true, true, "Adi-Main", false, 0, 0, "", true, true, true, true, true, true, false, false, true)

    @Test fun allGood() { val c = HealthRules.build(ok()); assertTrue(c.all { it.level == Level.OK }); assertEquals("Sab theek hai", HealthRules.headline(c)) }
    @Test fun missingTokenIsBad() = assertEquals(Level.BAD, HealthRules.build(ok().copy(tokenSaved = false)).first { it.name == "Bot Token" }.level)
    @Test fun badChatIsBad() = assertEquals(Level.BAD, HealthRules.build(ok().copy(chatFormatOk = false)).first { it.name == "Chat ID" }.level)
    @Test fun offlineQueueWarns() { val q = HealthRules.build(ok().copy(pending = 3, online = false)).first { it.name == "Queue" }; assertEquals(Level.WARN, q.level); assertTrue(q.detail.contains("offline")) }
    @Test fun failedQueueIsBad() = assertEquals(Level.BAD, HealthRules.build(ok().copy(failed = 1)).first { it.name == "Queue" }.level)
    @Test fun defaultLabelWarns() = assertEquals(Level.WARN, HealthRules.build(ok().copy(labelIsDefault = true)).first { it.name == "Device label" }.level)
    @Test fun micOnlyChecksWhenAudioWanted() {
        assertTrue(HealthRules.build(ok()).none { it.name == "Microphone" })
        assertEquals(Level.BAD, HealthRules.build(ok().copy(audioWanted = true, micPermission = false)).first { it.name == "Microphone" }.level)
    }

    @Test fun classify() {
        assertEquals(AlertType.MOTION, AlertType.classify("Jhatka pakda gaya"))
        assertEquals(AlertType.MOTION, AlertType.classify("Phone ko haath lagaya gaya"))
        assertEquals(AlertType.WRONG_UNLOCK, AlertType.classify("Galat password #3"))
        assertEquals(AlertType.WRONG_UNLOCK, AlertType.classify("Galat App PIN"))
        assertEquals(AlertType.CHARGER, AlertType.classify("Charger nikala gaya"))
        assertEquals(AlertType.SIM, AlertType.classify("SIM ALERT: x"))
        assertEquals(AlertType.MANUAL, AlertType.classify("Test Trigger"))
        assertEquals(AlertType.MANUAL, AlertType.classify("Panic button"))
    }

    @Test fun resetDropsStaleHalfRun() {
        val d = SnatchDetector(20f, 0, 10_000, 0, 3, 250, 100)
        for (i in 0 until 5) d.onSample(30f, 1000L + i * 20) // 80 ms of strong movement
        d.reset() // e.g. screen turned off
        var hit = false
        for (i in 0 until 5) hit = d.onSample(30f, 5000L + i * 20) || hit // new short bump must not borrow the old start time
        assertFalse(hit)
    }

    @Test fun oldRecordsHaveEmptyDevice() = assertEquals("", EventRecord.fromJson(org.json.JSONObject(EventRecord(id = "a", timeMs = 1, reason = "x", device = "D").toJson()).apply { remove("dev") }.toString()).device)
}
