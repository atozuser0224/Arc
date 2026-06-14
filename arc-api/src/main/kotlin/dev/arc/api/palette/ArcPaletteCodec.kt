package dev.arc.api.palette

import dev.arc.api.content.ContentId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

public object ArcPaletteCodec {
    private const val MAGIC = 0x41524350
    private const val VERSION = 1
    private const val MAX_ENTRIES = 16 * 16 * 1024
    private const val MAX_ID_BYTES = 256
    private const val MAX_STATE_BYTES = 65_535

    @JvmStatic
    public fun encode(palette: ArcPalette): ByteArray {
        val entries = palette.entries()
        require(entries.size <= MAX_ENTRIES) { "Palette has too many entries: ${entries.size}" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(VERSION)
            data.writeInt(entries.size)
            for (entry in entries) {
                val id = entry.block.id.toString().toByteArray(Charsets.UTF_8)
                require(id.size <= MAX_ID_BYTES) { "Content ID is too long: ${entry.block.id}" }
                require(entry.block.state.size <= MAX_STATE_BYTES) {
                    "Block state is too large: ${entry.block.state.size}"
                }
                data.writeByte(entry.position.x)
                data.writeInt(entry.position.y)
                data.writeByte(entry.position.z)
                data.writeShort(id.size)
                data.write(id)
                data.writeShort(entry.block.state.size)
                data.write(entry.block.state)
            }
        }
        return output.toByteArray()
    }

    @JvmStatic
    public fun decode(bytes: ByteArray): ArcPalette {
        val inputBytes = ByteArrayInputStream(bytes)
        val palette = ArcPalette()
        DataInputStream(inputBytes).use { data ->
            check(data.readInt() == MAGIC) { "Invalid ArcPalette magic" }
            check(data.readUnsignedByte() == VERSION) { "Unsupported ArcPalette version" }
            val count = data.readInt()
            check(count in 0..MAX_ENTRIES) { "Invalid ArcPalette entry count: $count" }
            repeat(count) {
                val x = data.readUnsignedByte()
                val y = data.readInt()
                val z = data.readUnsignedByte()
                val idLength = data.readUnsignedShort()
                check(idLength <= MAX_ID_BYTES) { "Content ID exceeds limit: $idLength" }
                val idBytes = ByteArray(idLength).also(data::readFully)
                val stateLength = data.readUnsignedShort()
                check(stateLength <= MAX_STATE_BYTES) { "Block state exceeds limit: $stateLength" }
                val state = ByteArray(stateLength).also(data::readFully)
                palette.set(
                    x,
                    y,
                    z,
                    ArcBlockState(ContentId.parse(idBytes.toString(Charsets.UTF_8)), state),
                )
            }
            check(inputBytes.available() == 0) { "Trailing ArcPalette bytes: ${inputBytes.available()}" }
        }
        palette.markClean()
        return palette
    }
}
