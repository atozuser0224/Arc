package dev.arc.api.sync

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

public class ArcSyncSession(
    public val maxBlobBytes: Int,
    public val maxChunkBytes: Int,
) {
    private data class PendingBlob(
        val declaredSize: Int,
        val bytes: ByteArrayOutputStream,
    )

    private val pending = ConcurrentHashMap<String, PendingBlob>()

    init {
        require(maxBlobBytes > 0) { "maxBlobBytes must be positive" }
        require(maxChunkBytes > 0) { "maxChunkBytes must be positive" }
        require(maxChunkBytes <= maxBlobBytes) { "maxChunkBytes must not exceed maxBlobBytes" }
    }

    public fun begin(sha256: String, declaredSize: Int) {
        require(sha256.isNotBlank()) { "sha256 must not be blank" }
        require(declaredSize in 0..maxBlobBytes) { "Blob size exceeds limit: $declaredSize" }
        check(pending.putIfAbsent(sha256, PendingBlob(declaredSize, ByteArrayOutputStream(declaredSize))) == null) {
            "Blob transfer already started: $sha256"
        }
    }

    public fun accept(sha256: String, offset: Int, chunk: ByteArray) {
        require(chunk.size <= maxChunkBytes) { "Chunk size exceeds limit: ${chunk.size}" }
        val blob = pending[sha256] ?: error("Blob transfer not started: $sha256")
        check(offset == blob.bytes.size()) {
            "Unexpected chunk offset $offset, expected ${blob.bytes.size()}"
        }
        check(offset + chunk.size <= blob.declaredSize) {
            "Chunk exceeds declared blob size"
        }
        blob.bytes.write(chunk)
    }

    public fun complete(sha256: String): ByteArray {
        val blob = pending.remove(sha256) ?: error("Blob transfer not started: $sha256")
        val bytes = blob.bytes.toByteArray()
        check(bytes.size == blob.declaredSize) {
            "Blob has ${bytes.size} bytes, expected ${blob.declaredSize}"
        }
        check(digest(bytes) == sha256) { "Blob SHA-256 mismatch" }
        return bytes
    }

    public fun cancel(sha256: String): Boolean = pending.remove(sha256) != null

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
