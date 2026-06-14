package dev.arc.api.content.runtime

import dev.arc.api.channel.ModPacketBuffer
import dev.arc.api.content.ContentId

public data class DecodedContentItemIdentity(
    public val id: ContentId,
    public val properties: Map<String, String>,
)

public object ContentItemIdentity {
    private const val MAGIC = 0x41524349
    private const val VERSION = 1
    private const val MAX_ID_LENGTH = 256
    private const val MAX_PROPERTIES = 64
    private const val MAX_KEY_LENGTH = 128
    private const val MAX_VALUE_LENGTH = 1024

    @JvmStatic
    public fun encode(id: ContentId, properties: Map<String, String>): ByteArray {
        require(properties.size <= MAX_PROPERTIES) {
            "Too many item identity properties: ${properties.size}"
        }
        val buffer = ModPacketBuffer.writing()
        buffer.writeInt(MAGIC)
        buffer.writeByte(VERSION)
        buffer.writeUtf(id.toString(), MAX_ID_LENGTH)
        buffer.writeVarInt(properties.size)
        properties.toSortedMap().forEach { (key, value) ->
            require(key.isNotBlank()) { "Item identity property key must not be blank" }
            buffer.writeUtf(key, MAX_KEY_LENGTH)
            buffer.writeUtf(value, MAX_VALUE_LENGTH)
        }
        return buffer.toByteArray()
    }

    @JvmStatic
    public fun decode(bytes: ByteArray): DecodedContentItemIdentity {
        val buffer = ModPacketBuffer.reading(bytes)
        check(buffer.readInt() == MAGIC) { "Invalid content item identity magic" }
        check(buffer.readUnsignedByte() == VERSION) { "Unsupported content item identity version" }
        val id = ContentId.parse(buffer.readUtf(MAX_ID_LENGTH))
        val count = buffer.readVarInt()
        check(count in 0..MAX_PROPERTIES) { "Invalid item identity property count: $count" }
        val properties = linkedMapOf<String, String>()
        repeat(count) {
            val key = buffer.readUtf(MAX_KEY_LENGTH)
            check(key.isNotBlank()) { "Item identity property key must not be blank" }
            val value = buffer.readUtf(MAX_VALUE_LENGTH)
            check(properties.putIfAbsent(key, value) == null) {
                "Duplicate item identity property: $key"
            }
        }
        check(buffer.remaining == 0) { "Trailing content item identity bytes: ${buffer.remaining}" }
        return DecodedContentItemIdentity(id, properties)
    }
}
