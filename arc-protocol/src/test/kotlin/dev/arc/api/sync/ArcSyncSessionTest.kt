package dev.arc.api.sync

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ArcSyncSessionTest {

    @Test
    fun `session assembles bounded chunks and verifies hash`() {
        val payload = "arc-content".toByteArray()
        val hash = sha256(payload)
        val session = ArcSyncSession(maxBlobBytes = 32, maxChunkBytes = 8)

        session.begin(hash, payload.size)
        session.accept(hash, 0, payload.copyOfRange(0, 5))
        session.accept(hash, 5, payload.copyOfRange(5, payload.size))

        assertContentEquals(payload, session.complete(hash))
    }

    @Test
    fun `session rejects oversized and out of order chunks`() {
        val session = ArcSyncSession(maxBlobBytes = 16, maxChunkBytes = 8)
        session.begin("hash", 9)

        assertFailsWith<IllegalArgumentException> {
            session.accept("hash", 0, ByteArray(9))
        }
        assertFailsWith<IllegalStateException> {
            session.accept("hash", 1, byteArrayOf(1))
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
