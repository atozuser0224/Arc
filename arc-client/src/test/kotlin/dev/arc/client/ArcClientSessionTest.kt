package dev.arc.client

import dev.arc.api.sync.ArcSyncFeature
import dev.arc.api.sync.ArcSyncManifest
import dev.arc.api.sync.ManifestSigner
import dev.arc.api.sync.SignedManifest
import dev.arc.api.sync.SyncBlob
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcClientSessionTest {

    @Test
    fun `session pins server key verifies manifest and activates cached blobs`() {
        val root = Files.createTempDirectory("arc-client")
        try {
            val keys = ManifestSigner.generate()
            val payload = byteArrayOf(1, 2, 3)
            val hash = sha256(payload)
            val signed = ManifestSigner.sign(manifest(hash, payload.size.toLong()), keys.private)
            val session = ArcClientSession(root)

            val plan = session.begin("example.test:25565", keys.public, signed)
            assertEquals(listOf(hash), plan.missingHashes)
            session.store(hash, payload)
            session.activate()

            assertTrue(session.active)
            assertContentEquals(payload, session.read(hash))
            session.disconnect()
            assertFalse(session.active)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `changed pinned key requires explicit trust replacement`() {
        val root = Files.createTempDirectory("arc-client")
        try {
            val first = ManifestSigner.generate()
            val second = ManifestSigner.generate()
            val session = ArcClientSession(root)
            val signed = ManifestSigner.sign(manifest("empty", 0), first.private)
            session.begin("example.test:25565", first.public, signed)
            session.disconnect()

            assertFailsWith<ServerTrustException> {
                session.begin(
                    "example.test:25565",
                    second.public,
                    ManifestSigner.sign(manifest("empty", 0), second.private),
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun manifest(hash: String, size: Long): ArcSyncManifest = ArcSyncManifest(
        protocolVersion = 1,
        serverId = "example.test:25565",
        revision = "revision",
        requiredFeatures = setOf(ArcSyncFeature.CREATIVE_TABS),
        blobs = listOf(SyncBlob("textures/item/test.png", hash, size)),
    )

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
