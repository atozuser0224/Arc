package dev.arc.api.sync

import dev.arc.api.channel.ModPacketBuffer

public sealed interface ArcSyncPacket

public data class ClientHelloPacket(public val hello: ClientHello) : ArcSyncPacket

public data class ServerManifestPacket(
    public val publicKey: ByteArray,
    public val signedManifest: SignedManifest,
) : ArcSyncPacket {
    override fun equals(other: Any?): Boolean =
        other is ServerManifestPacket &&
            publicKey.contentEquals(other.publicKey) &&
            signedManifest == other.signedManifest

    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + signedManifest.hashCode()
}

public data class BlobRequestPacket(public val hashes: List<String>) : ArcSyncPacket

public data class BlobChunkPacket(
    public val sha256: String,
    public val offset: Long,
    public val totalSize: Long,
    public val bytes: ByteArray,
) : ArcSyncPacket {
    override fun equals(other: Any?): Boolean =
        other is BlobChunkPacket &&
            sha256 == other.sha256 &&
            offset == other.offset &&
            totalSize == other.totalSize &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = sha256.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + totalSize.hashCode()
        return 31 * result + bytes.contentHashCode()
    }
}

public data class ActivatePacket(public val revision: String) : ArcSyncPacket

public data class FailurePacket(public val message: String) : ArcSyncPacket

public object ArcSyncPacketCodec {
    public const val MAX_CHUNK_BYTES: Int = 900 * 1024

    private const val CLIENT_HELLO = 0
    private const val SERVER_MANIFEST = 1
    private const val BLOB_REQUEST = 2
    private const val BLOB_CHUNK = 3
    private const val ACTIVATE = 4
    private const val FAILURE = 5
    private const val MAX_BLOBS = 16_384
    private const val MAX_FEATURES = 64
    private const val MAX_HASH_LENGTH = 128
    private const val MAX_PATH_LENGTH = 512
    private const val MAX_SERVER_ID_LENGTH = 256
    private const val MAX_REVISION_LENGTH = 128
    private const val MAX_KEY_BYTES = 1_024
    private const val MAX_SIGNATURE_BYTES = 256
    private const val MAX_FAILURE_LENGTH = 1_024

    @JvmStatic
    public fun encode(packet: ArcSyncPacket): ByteArray {
        val buffer = ModPacketBuffer.writing()
        when (packet) {
            is ClientHelloPacket -> {
                buffer.writeVarInt(CLIENT_HELLO)
                buffer.writeByteArray(ArcSyncCodec.encode(packet.hello))
            }
            is ServerManifestPacket -> {
                buffer.writeVarInt(SERVER_MANIFEST)
                require(packet.publicKey.size <= MAX_KEY_BYTES) { "Public key exceeds limit" }
                require(packet.signedManifest.signature.size <= MAX_SIGNATURE_BYTES) {
                    "Manifest signature exceeds limit"
                }
                buffer.writeByteArray(packet.publicKey)
                writeManifest(buffer, packet.signedManifest.manifest)
                buffer.writeByteArray(packet.signedManifest.signature)
            }
            is BlobRequestPacket -> {
                require(packet.hashes.size <= MAX_BLOBS) { "Blob request exceeds limit" }
                require(packet.hashes.distinct().size == packet.hashes.size) {
                    "Blob request hashes must be unique"
                }
                buffer.writeVarInt(BLOB_REQUEST)
                buffer.writeVarInt(packet.hashes.size)
                packet.hashes.forEach { buffer.writeUtf(it, MAX_HASH_LENGTH) }
            }
            is BlobChunkPacket -> {
                require(packet.offset >= 0) { "Chunk offset must not be negative" }
                require(packet.totalSize >= 0) { "Chunk total size must not be negative" }
                require(packet.bytes.size <= MAX_CHUNK_BYTES) { "Chunk exceeds limit" }
                require(packet.offset + packet.bytes.size <= packet.totalSize) {
                    "Chunk exceeds declared blob size"
                }
                buffer.writeVarInt(BLOB_CHUNK)
                buffer.writeUtf(packet.sha256, MAX_HASH_LENGTH)
                buffer.writeLong(packet.offset)
                buffer.writeLong(packet.totalSize)
                buffer.writeByteArray(packet.bytes)
            }
            is ActivatePacket -> {
                buffer.writeVarInt(ACTIVATE)
                buffer.writeUtf(packet.revision, MAX_REVISION_LENGTH)
            }
            is FailurePacket -> {
                buffer.writeVarInt(FAILURE)
                buffer.writeUtf(packet.message, MAX_FAILURE_LENGTH)
            }
        }
        return buffer.toByteArray()
    }

    @JvmStatic
    public fun decode(bytes: ByteArray): ArcSyncPacket {
        val buffer = ModPacketBuffer.reading(bytes)
        val packet = when (val kind = buffer.readVarInt()) {
            CLIENT_HELLO -> ClientHelloPacket(
                ArcSyncCodec.decodeClientHello(buffer.readByteArray(MAX_CHUNK_BYTES)),
            )
            SERVER_MANIFEST -> {
                val key = buffer.readByteArray(MAX_KEY_BYTES)
                val manifest = readManifest(buffer)
                val signature = buffer.readByteArray(MAX_SIGNATURE_BYTES)
                ServerManifestPacket(key, SignedManifest(manifest, signature))
            }
            BLOB_REQUEST -> {
                val count = buffer.readVarInt()
                check(count in 0..MAX_BLOBS) { "Blob request count exceeds limit: $count" }
                val hashes = List(count) { buffer.readUtf(MAX_HASH_LENGTH) }
                check(hashes.distinct().size == hashes.size) { "Duplicate blob request hash" }
                BlobRequestPacket(hashes)
            }
            BLOB_CHUNK -> {
                val hash = buffer.readUtf(MAX_HASH_LENGTH)
                val offset = buffer.readLong()
                val totalSize = buffer.readLong()
                val chunk = buffer.readByteArray(MAX_CHUNK_BYTES)
                check(offset >= 0 && totalSize >= 0 && offset + chunk.size <= totalSize) {
                    "Chunk is outside declared blob bounds"
                }
                BlobChunkPacket(hash, offset, totalSize, chunk)
            }
            ACTIVATE -> ActivatePacket(buffer.readUtf(MAX_REVISION_LENGTH))
            FAILURE -> FailurePacket(buffer.readUtf(MAX_FAILURE_LENGTH))
            else -> error("Unknown ArcSync packet kind: $kind")
        }
        check(buffer.remaining == 0) { "Trailing ArcSync packet bytes: ${buffer.remaining}" }
        return packet
    }

    private fun writeManifest(buffer: ModPacketBuffer, manifest: ArcSyncManifest) {
        require(manifest.requiredFeatures.size <= MAX_FEATURES) { "Feature count exceeds limit" }
        require(manifest.blobs.size <= MAX_BLOBS) { "Blob count exceeds limit" }
        buffer.writeVarInt(manifest.protocolVersion)
        buffer.writeUtf(manifest.serverId, MAX_SERVER_ID_LENGTH)
        buffer.writeUtf(manifest.revision, MAX_REVISION_LENGTH)
        val features = manifest.requiredFeatures.sortedBy { it.ordinal }
        buffer.writeVarInt(features.size)
        features.forEach { buffer.writeVarInt(it.ordinal) }
        buffer.writeVarInt(manifest.blobs.size)
        manifest.blobs.forEach {
            buffer.writeUtf(it.path, MAX_PATH_LENGTH)
            buffer.writeUtf(it.sha256, MAX_HASH_LENGTH)
            buffer.writeLong(it.size)
        }
    }

    private fun readManifest(buffer: ModPacketBuffer): ArcSyncManifest {
        val protocolVersion = buffer.readVarInt()
        val serverId = buffer.readUtf(MAX_SERVER_ID_LENGTH)
        val revision = buffer.readUtf(MAX_REVISION_LENGTH)
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
        val blobCount = buffer.readVarInt()
        check(blobCount in 0..MAX_BLOBS) { "Blob count exceeds limit: $blobCount" }
        val blobs = List(blobCount) {
            SyncBlob(
                buffer.readUtf(MAX_PATH_LENGTH),
                buffer.readUtf(MAX_HASH_LENGTH),
                buffer.readLong(),
            )
        }
        return ArcSyncManifest(protocolVersion, serverId, revision, features, blobs)
    }
}
