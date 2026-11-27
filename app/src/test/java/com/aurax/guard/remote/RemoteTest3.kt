package com.aurax.guard.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteTest3 {
    @Test fun urgentCadenceIsFasterButGentle() {
        val idle = RemoteRules.pollIntervalMs(false, false, true, 0) // saver, screen off
        assertEquals(15 * 60_000L, idle)
        val lock = RemoteRules.pollIntervalMs(false, false, true, 0, Urgency.LOCKDOWN, 60)
        assertEquals(120_000L, lock) // saver does not delay urgent recovery
        assertEquals(180_000L, RemoteRules.pollIntervalMs(false, false, false, 0, Urgency.TRACE, 35))
        assertEquals(60_000L, RemoteRules.pollIntervalMs(true, false, false, 0, Urgency.RECENT_CMD, 80))
        // nearly dead battery: relax to 5 minutes
        assertEquals(5 * 60_000L, RemoteRules.pollIntervalMs(false, false, false, 0, Urgency.LOCKDOWN, 8))
        assertEquals(2 * 60_000L, RemoteRules.pollIntervalMs(false, true, false, 0, Urgency.LOCKDOWN, 8) .coerceAtMost(2 * 60_000L)) // charging: not relaxed
        // never aggressive: always >= 1 minute, and offline backoff is capped at 10 min when urgent
        assertTrue(RemoteRules.pollIntervalMs(true, true, false, 0, Urgency.RECENT_CMD) >= 60_000L)
        assertEquals(10 * 60_000L, RemoteRules.pollIntervalMs(false, false, false, 5, Urgency.LOCKDOWN, 60))
        // old behaviour unchanged
        assertEquals(60_000L, RemoteRules.pollIntervalMs(true, false, false, 0))
        assertEquals(30 * 60_000L, RemoteRules.pollIntervalMs(false, false, true, 5))
    }

    @Test fun urgencyPriority() {
        assertEquals(Urgency.NONE, RemoteRules.urgency(false, false, false, false))
        assertEquals(Urgency.TRACE, RemoteRules.urgency(false, true, false, false))
        assertEquals(Urgency.TRACE, RemoteRules.urgency(false, false, true, false))
        assertEquals(Urgency.LOCKDOWN, RemoteRules.urgency(true, true, false, false))
        assertEquals(Urgency.RECENT_CMD, RemoteRules.urgency(true, true, true, true))
        assertNull(RemoteRules.urgentBaseMs(Urgency.NONE, true, 50, false))
    }

    @Test fun photoReasons() {
        assertNull(RemoteRules.photoReason(true, true))
        assertTrue(RemoteRules.photoReason(true, false)!!.contains("ijazat"))
        assertTrue(RemoteRules.photoReason(false, true)!!.contains("Guard app se"))
        assertTrue(RemoteRules.photoReason(false, false)!!.contains("ijazat")) // permission is reported first
    }
}
