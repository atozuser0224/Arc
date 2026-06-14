package dev.arc.server.content

import dev.arc.api.content.ContentId
import dev.arc.api.palette.ArcBlockState
import dev.arc.api.palette.ArcPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArcPaletteStoreTest {

    @Test
    fun `store round trips palette and clears empty data`() {
        var bytes: ByteArray? = null
        val store = ArcPaletteStore(
            readBytes = { bytes?.copyOf() },
            writeBytes = { bytes = it?.copyOf() },
        )
        val palette = ArcPalette().apply {
            set(1, 64, 2, ArcBlockState(ContentId.parse("magic:altar"), byteArrayOf(3)))
        }

        store.save(palette)
        assertEquals(ContentId.parse("magic:altar"), store.load().get(1, 64, 2)?.id)

        store.save(ArcPalette())
        assertNull(bytes)
    }
}
