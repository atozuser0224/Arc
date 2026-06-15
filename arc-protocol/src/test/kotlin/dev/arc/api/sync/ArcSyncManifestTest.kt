package dev.arc.api.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcSyncManifestTest {

    @Test
    fun `manifest signature verifies and detects tampering`() {
        val keys = ManifestSigner.generate()
        val signed = ManifestSigner.sign(sampleManifest(), keys.private)

        assertTrue(ManifestSigner.verify(signed, keys.public))
        assertFalse(
            ManifestSigner.verify(
                signed.copy(manifest = signed.manifest.copy(revision = "tampered")),
                keys.public,
            ),
        )
    }

    @Test
    fun `manifest canonical bytes are independent of input ordering`() {
        val first = sampleManifest()
        val second = first.copy(blobs = first.blobs.reversed())

        assertTrue(first.canonicalBytes().contentEquals(second.canonicalBytes()))
    }

    @Test
    fun `transfer plan includes only missing content hashes`() {
        val plan = TransferPlan.create(sampleManifest(), setOf("already-cached"))

        assertEquals(listOf("missing-hash"), plan.blobs.map { it.sha256 })
        assertEquals(7L, plan.totalBytes)
    }

    private fun sampleManifest(): ArcSyncManifest = ArcSyncManifest(
        protocolVersion = ArcSyncProtocol.VERSION,
        serverId = "example.test:25565",
        revision = "revision-1",
        requiredFeatures = setOf(ArcSyncFeature.CREATIVE_TABS),
        blobs = listOf(
            SyncBlob("textures/cached.png", "already-cached", 5),
            SyncBlob("textures/missing.png", "missing-hash", 7),
        ),
    )
}
