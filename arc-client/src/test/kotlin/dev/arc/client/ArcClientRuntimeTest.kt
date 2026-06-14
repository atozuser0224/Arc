package dev.arc.client

import dev.arc.api.sync.ArcSyncFeature
import dev.arc.api.sync.ArcSyncManifest
import dev.arc.api.sync.ArcSyncProtocol
import dev.arc.api.sync.ManifestSigner
import dev.arc.api.sync.SyncBlob
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ArcClientRuntimeTest {

    @TempDir
    lateinit var root: Path

    @Test
    fun `connection activates server catalog and disconnect clears it`() {
        val catalog = """{"revision":"r1","title":"Arc: magic","entries":[]}""".toByteArray()
        val hash = sha256(catalog)
        val keys = ManifestSigner.generate()
        val manifest = ArcSyncManifest(
            protocolVersion = ArcSyncProtocol.VERSION,
            serverId = "example.test",
            revision = "r1",
            requiredFeatures = setOf(ArcSyncFeature.CREATIVE_TABS),
            blobs = listOf(SyncBlob("arc/catalog.json", hash, catalog.size.toLong())),
        )
        val runtime = ArcClientRuntime(root)

        val plan = runtime.begin("example.test", keys.public, ManifestSigner.sign(manifest, keys.private))
        assertEquals(listOf(hash), plan.missingHashes)
        runtime.accept(hash, catalog)
        val pack = runtime.activate()

        assertTrue(runtime.active)
        assertEquals("Arc: magic", runtime.catalog?.title)
        assertTrue(pack.resolve("pack.mcmeta").exists())
        assertContentEquals(catalog, pack.resolve("assets/arc/catalog.json").readBytes())

        runtime.disconnect()
        assertFalse(runtime.active)
        assertNull(runtime.catalog)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
