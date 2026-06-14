package dev.arc.server.content

import dev.arc.api.content.runtime.ContentCapability
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcContentBackendTest {

    @Test
    fun `backend advertises only implemented baseline capabilities`() {
        assertTrue(ContentCapability.ARC_SYNC in ArcContentBackend.capabilities)
        assertFalse(ContentCapability.CREATIVE_SYNC in ArcContentBackend.capabilities)
        assertFalse(ContentCapability.PALETTE_PERSISTENCE in ArcContentBackend.capabilities)
    }
}
