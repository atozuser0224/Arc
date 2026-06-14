package dev.arc.api.channel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * NMS-free packet buffer for Bukkit plugin messaging and client-mod protocols.
 *
 * Primitive values use Java's network-order encoding. Strings and byte arrays
 * use a Minecraft-compatible VarInt byte-length prefix.
 */
public class ModPacketBuffer private constructor(
    private val inputBytes: ByteArrayInputStream?,
    private val input: DataInputStream?,
    private val outputBytes: ByteArrayOutputStream?,
    private val output: DataOutputStream?,
) {
    public val remaining: Int
        get() = requireReader().first.available()

    public fun writeBoolean(value: Boolean): ModPacketBuffer = write { it.writeBoolean(value) }
    public fun writeByte(value: Int): ModPacketBuffer = write { it.writeByte(value) }
    public fun writeShort(value: Int): ModPacketBuffer = write { it.writeShort(value) }
    public fun writeInt(value: Int): ModPacketBuffer = write { it.writeInt(value) }
    public fun writeLong(value: Long): ModPacketBuffer = write { it.writeLong(value) }
    public fun writeFloat(value: Float): ModPacketBuffer = write { it.writeFloat(value) }
    public fun writeDouble(value: Double): ModPacketBuffer = write { it.writeDouble(value) }

    public fun writeVarInt(value: Int): ModPacketBuffer {
        var current = value
        while (true) {
            if (current and -128 == 0) {
                writeByte(current)
                return this
            }
            writeByte(current and 127 or 128)
            current = current ushr 7
        }
    }

    public fun writeUtf(value: String, maxLength: Int = 32_767): ModPacketBuffer {
        require(maxLength >= 0) { "maxLength must not be negative" }
        require(value.length <= maxLength) {
            "String has ${value.length} characters, maximum is $maxLength"
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxLength * 4L) {
            "Encoded string has ${bytes.size} bytes, maximum is ${maxLength * 4L}"
        }
        writeVarInt(bytes.size)
        return writeRawBytes(bytes)
    }

    public fun writeUuid(value: UUID): ModPacketBuffer =
        writeLong(value.mostSignificantBits).writeLong(value.leastSignificantBits)

    public fun writeByteArray(value: ByteArray): ModPacketBuffer =
        writeVarInt(value.size).writeRawBytes(value)

    public fun writeRawBytes(value: ByteArray): ModPacketBuffer =
        write { it.write(value) }

    public fun readBoolean(): Boolean = requireReader().second.readBoolean()
    public fun readByte(): Byte = requireReader().second.readByte()
    public fun readUnsignedByte(): Int = requireReader().second.readUnsignedByte()
    public fun readShort(): Short = requireReader().second.readShort()
    public fun readUnsignedShort(): Int = requireReader().second.readUnsignedShort()
    public fun readInt(): Int = requireReader().second.readInt()
    public fun readLong(): Long = requireReader().second.readLong()
    public fun readFloat(): Float = requireReader().second.readFloat()
    public fun readDouble(): Double = requireReader().second.readDouble()

    public fun readVarInt(): Int {
        var value = 0
        var position = 0
        while (position < 35) {
            val current = readUnsignedByte()
            value = value or ((current and 127) shl position)
            if (current and 128 == 0) return value
            position += 7
        }
        error("VarInt is too large")
    }

    public fun readUtf(maxLength: Int = 32_767): String {
        require(maxLength >= 0) { "maxLength must not be negative" }
        val byteLength = readVarInt()
        check(byteLength >= 0) { "Negative encoded string length: $byteLength" }
        check(byteLength <= maxLength * 4L) {
            "Encoded string has $byteLength bytes, maximum is ${maxLength * 4L}"
        }
        val value = readRawBytes(byteLength).toString(Charsets.UTF_8)
        check(value.length <= maxLength) {
            "Decoded string has ${value.length} characters, maximum is $maxLength"
        }
        return value
    }

    public fun readUuid(): UUID = UUID(readLong(), readLong())

    public fun readByteArray(maxLength: Int = Int.MAX_VALUE): ByteArray {
        require(maxLength >= 0) { "maxLength must not be negative" }
        val length = readVarInt()
        check(length in 0..maxLength) {
            "Byte array length $length is outside 0..$maxLength"
        }
        return readRawBytes(length)
    }

    public fun readRawBytes(length: Int): ByteArray {
        require(length >= 0) { "length must not be negative" }
        return ByteArray(length).also { requireReader().second.readFully(it) }
    }

    public fun toByteArray(): ByteArray {
        val bytes = outputBytes ?: error("Buffer is in read mode")
        output?.flush()
        return bytes.toByteArray()
    }

    private inline fun write(block: (DataOutputStream) -> Unit): ModPacketBuffer {
        block(output ?: error("Buffer is in read mode"))
        return this
    }

    private fun requireReader(): Pair<ByteArrayInputStream, DataInputStream> =
        (inputBytes ?: error("Buffer is in write mode")) to
            (input ?: error("Buffer is in write mode"))

    public companion object {
        @JvmStatic
        public fun writing(initialCapacity: Int = 64): ModPacketBuffer {
            require(initialCapacity >= 0) { "initialCapacity must not be negative" }
            val bytes = ByteArrayOutputStream(initialCapacity)
            return ModPacketBuffer(null, null, bytes, DataOutputStream(bytes))
        }

        @JvmStatic
        public fun reading(bytes: ByteArray): ModPacketBuffer {
            val input = ByteArrayInputStream(bytes)
            return ModPacketBuffer(input, DataInputStream(input), null, null)
        }
    }
}
