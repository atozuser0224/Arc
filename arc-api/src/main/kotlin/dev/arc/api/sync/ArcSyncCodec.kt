package dev.arc.api.sync

import dev.arc.api.channel.ModPacketBuffer

public data class ClientHello(
    public val protocolVersion: Int,
    public val features: Set<ArcSyncFeature>,
    public val cachedRevision: String?,
    public val cachedHashes: Set<String>,
)

public object ArcSyncCodec {
    private const val MAX_FEATURES = 64
    private const val MAX_HASHES = 16_384
    private const val MAX_HASH_LENGTH = 128
    private const val MAX_REVISION_LENGTH = 128

    @JvmStatic
    public fun encode(hello: ClientHello): ByteArray {
        val buffer = ModPacketBuffer.writing()
        buffer.writeVarInt(hello.protocolVersion)
        val features = hello.features.sortedBy { it.ordinal }
        buffer.writeVarInt(features.size)
        features.forEach { buffer.writeVarInt(it.ordinal) }
        buffer.writeBoolean(hello.cachedRevision != null)
        hello.cachedRevision?.let { buffer.writeUtf(it, MAX_REVISION_LENGTH) }
        val hashes = hello.cachedHashes.sorted()
        buffer.writeVarInt(hashes.size)
        hashes.forEach { buffer.writeUtf(it, MAX_HASH_LENGTH) }
        return buffer.toByteArray()
    }

    @JvmStatic
    public fun decodeClientHello(bytes: ByteArray): ClientHello {
        val buffer = ModPacketBuffer.reading(bytes)
        val protocolVersion = buffer.readVarInt()
        val featureCount = buffer.readVarInt()
        check(featureCount in 0..MAX_FEATURES) { "Feature count exceeds limit: $featureCount" }
        val features = buildSet {
            repeat(featureCount) {
                val ordinal = buffer.readVarInt()
                val feature = ArcSyncFeature.entries.getOrNull(ordinal)
                    ?: error("Unknown ArcSync feature: $ordinal")
                check(add(feature)) { "Duplicate ArcSync feature: $feature" }
            }
        }
        val cachedRevision = if (buffer.readBoolean()) {
            buffer.readUtf(MAX_REVISION_LENGTH)
        } else {
            null
        }
        val hashCount = buffer.readVarInt()
        check(hashCount in 0..MAX_HASHES) { "Hash count exceeds limit: $hashCount" }
        val hashes = buildSet {
            repeat(hashCount) {
                val hash = buffer.readUtf(MAX_HASH_LENGTH)
                check(add(hash)) { "Duplicate cached hash: $hash" }
            }
        }
        check(buffer.remaining == 0) { "Trailing ArcSync client hello bytes: ${buffer.remaining}" }
        return ClientHello(protocolVersion, features, cachedRevision, hashes)
    }
}
