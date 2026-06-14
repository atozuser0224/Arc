package dev.arc.api.content.runtime

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentRuntimeTest {

    @AfterTest
    fun reset() {
        ArcContentRuntime.installBackend(null)
    }

    @Test
    fun `runtime exposes installed backend capabilities`() {
        assertFalse(ArcContentRuntime.isAvailable)

        ArcContentRuntime.installBackend(
            object : ContentRuntimeBackend {
                override val capabilities: Set<ContentCapability> =
                    setOf(ContentCapability.ARC_SYNC, ContentCapability.PALETTE_PERSISTENCE)
            },
        )

        assertTrue(ArcContentRuntime.isAvailable)
        assertTrue(ArcContentRuntime.supports(ContentCapability.ARC_SYNC))
        assertFalse(ArcContentRuntime.supports(ContentCapability.CREATIVE_SYNC))
    }
}
