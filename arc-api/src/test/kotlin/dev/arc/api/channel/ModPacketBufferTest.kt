package dev.arc.api.channel

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModPacketBufferTest {

    @Test
    fun `round trips primitive and structured values`() {
        val uuid = UUID.fromString("12345678-1234-5678-9abc-def012345678")
        val encoded = ModPacketBuffer.writing()
            .writeBoolean(true)
            .writeByte(0x7f)
            .writeInt(0x12345678)
            .writeLong(0x123456789abcdefL)
            .writeFloat(1.25f)
            .writeDouble(42.5)
            .writeVarInt(300)
            .writeUtf("Arc 한글")
            .writeUuid(uuid)
            .writeByteArray(byteArrayOf(1, 2, 3))
            .toByteArray()

        val decoded = ModPacketBuffer.reading(encoded)
        assertEquals(true, decoded.readBoolean())
        assertEquals(0x7f, decoded.readUnsignedByte())
        assertEquals(0x12345678, decoded.readInt())
        assertEquals(0x123456789abcdefL, decoded.readLong())
        assertEquals(1.25f, decoded.readFloat())
        assertEquals(42.5, decoded.readDouble())
        assertEquals(300, decoded.readVarInt())
        assertEquals("Arc 한글", decoded.readUtf())
        assertEquals(uuid, decoded.readUuid())
        assertContentEquals(byteArrayOf(1, 2, 3), decoded.readByteArray())
        assertEquals(0, decoded.remaining)
    }

    @Test
    fun `rejects oversized strings and malformed varints`() {
        assertFailsWith<IllegalArgumentException> {
            ModPacketBuffer.writing().writeUtf("abcd", maxLength = 3)
        }
        assertFailsWith<IllegalStateException> {
            ModPacketBuffer.reading(byteArrayOf(-1, -1, -1, -1, -1, 1)).readVarInt()
        }
    }
}
