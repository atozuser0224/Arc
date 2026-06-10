package dev.arc.server.nms

import dev.arc.api.Arc
import dev.arc.api.control.NmsThreadPolicy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NmsThreadGuardTest {

    @AfterTest
    fun reset() {
        Arc.settings.nmsThreadPolicy = NmsThreadPolicy.STRICT
        NmsThreadGuard.reset()
    }

    @Test
    fun `strict policy rejects non-owned access`() {
        Arc.settings.nmsThreadPolicy = NmsThreadPolicy.STRICT

        assertFailsWith<IllegalStateException> {
            NmsThreadGuard.requireOwned(false, "test")
        }
        assertEquals(1, NmsThreadGuard.rejectedCount())
    }

    @Test
    fun `unsafe policy permits non-owned access`() {
        Arc.settings.nmsThreadPolicy = NmsThreadPolicy.UNSAFE

        NmsThreadGuard.requireOwned(false, "test")
        assertEquals(0, NmsThreadGuard.rejectedCount())
    }
}
