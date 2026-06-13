package dev.arc.server.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArcBrandingTest {
    @Test
    fun `banner identifies Arc Bucket and build versions`() {
        val banner = ArcBranding.startupBanner("1.21.4-DEV", "1.21.4")

        assertEquals(7, banner.size)
        assertTrue(banner.any { it.contains("A R C   B U C K E T") })
        assertTrue(banner.last().contains("1.21.4-DEV"))
        assertTrue(banner.last().contains("Minecraft 1.21.4"))
    }
}
