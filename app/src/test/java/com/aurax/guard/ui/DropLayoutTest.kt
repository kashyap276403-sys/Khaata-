package com.aurax.guard.ui

import org.junit.Assert.*
import org.junit.Test

class DropLayoutTest {
    @Test fun repeatableForSameSeed() = assertEquals(DropLayout.generate(5, 3), DropLayout.generate(5, 3))

    @Test fun dropsStayInTheRightHandBandAndAreSmall() {
        for (seed in 1L..40L) DropLayout.generate(seed, 3).forEach {
            assertTrue(it.xFrac in 0.66f..0.95f)
            assertTrue(it.yFrac in 0.14f..0.86f)
            assertTrue(it.rDp in 2.2f..5.6f)
        }
    }

    @Test fun dropsDoNotOverlap() {
        DropLayout.generate(9, 3).let { l ->
            for (a in l) for (b in l) if (a !== b) assertTrue(Math.hypot((a.xFrac - b.xFrac).toDouble(), (a.yFrac - b.yFrac).toDouble()) >= 0.11)
        }
    }

    @Test fun countIsRespected() = assertTrue(DropLayout.generate(3, 2).size <= 2)
}
