package dev.arc.api.palette

import dev.arc.api.content.ContentId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArcPaletteTest {

    @Test
    fun `codec preserves unknown ids and opaque state bytes`() {
        val original = ArcPalette().apply {
            set(1, -64, 2, ArcBlockState(ContentId.parse("missing:altar"), byteArrayOf(3, 9)))
            set(15, 319, 15, ArcBlockState(ContentId.parse("magic:lamp"), byteArrayOf()))
        }

        val restored = ArcPaletteCodec.decode(ArcPaletteCodec.encode(original))

        assertEquals(ContentId.parse("missing:altar"), restored.get(1, -64, 2)?.id)
        assertContentEquals(byteArrayOf(3, 9), restored.get(1, -64, 2)?.state)
        assertEquals(ContentId.parse("magic:lamp"), restored.get(15, 319, 15)?.id)
        assertFalse(restored.dirty)
    }

    @Test
    fun `palette validates local coordinates and tracks mutations`() {
        val palette = ArcPalette()
        val state = ArcBlockState(ContentId.parse("magic:block"), byteArrayOf(1))

        assertFailsWith<IllegalArgumentException> { palette.set(16, 0, 0, state) }
        palette.set(0, 0, 0, state)
        assertTrue(palette.dirty)
        assertEquals(1, palette.size)

        palette.markClean()
        assertFalse(palette.dirty)
        assertEquals(state, palette.remove(0, 0, 0))
        assertTrue(palette.dirty)
        assertNull(palette.get(0, 0, 0))
    }
}
