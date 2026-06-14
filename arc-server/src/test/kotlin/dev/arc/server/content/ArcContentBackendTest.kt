package dev.arc.server.content

import dev.arc.api.content.runtime.ContentCapability
import kotlin.test.Test
import kotlin.test.assertTrue

class ArcContentBackendTest {

    @Test
    fun `backend advertises implemented content capabilities`() {
        assertTrue(ContentCapability.ARC_SYNC in ArcContentBackend.capabilities)
        assertTrue(ContentCapability.CREATIVE_SYNC in ArcContentBackend.capabilities)
        assertTrue(ContentCapability.ITEM_FALLBACK in ArcContentBackend.capabilities)
        assertTrue(ContentCapability.BLOCK_FALLBACK in ArcContentBackend.capabilities)
        assertTrue(ContentCapability.PALETTE_PERSISTENCE in ArcContentBackend.capabilities)
    }
}
