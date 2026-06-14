package dev.arc.api.sync

import dev.arc.api.content.ContentCompiler
import dev.arc.api.content.asset.ContentAsset
import dev.arc.api.content.contentPack
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.google.gson.JsonParser

class ArcSyncServiceTest {

    @Test
    fun `service signs revision assets and plans missing transfers`() {
        val revision = requireNotNull(
            ContentCompiler.compile(
                listOf(contentPack("magic", "1") { item("wand") { } }),
            ).revision,
        )
        val asset = ContentAsset("textures/item/wand.png", byteArrayOf(1, 2, 3))
        val keys = ManifestSigner.generate()
        val service = ArcSyncService("example.test:25565", keys)

        val signed = service.publish(revision, listOf(asset))
        val plan = service.plan(ClientHello(ArcSyncProtocol.VERSION, emptySet(), null, emptySet()))

        assertTrue(ManifestSigner.verify(signed, keys.public))
        assertEquals(revision.hash, signed.manifest.revision)
        assertTrue(plan.blobs.any { it.sha256 == asset.sha256 })
        val catalogBlob = signed.manifest.blobs.single { it.path == "arc/catalog.json" }
        val catalog = JsonParser.parseString(
            requireNotNull(service.blob(catalogBlob.sha256)).toString(Charsets.UTF_8),
        ).asJsonObject
        assertEquals("Arc: magic", catalog["title"].asString)
        assertEquals("magic:wand", catalog["entries"].asJsonArray.single().asJsonObject["id"].asString)
    }

    @Test
    fun `key store reuses generated server identity`() {
        val root = Files.createTempDirectory("arc-sync-keys")
        try {
            val first = ManifestKeyStore.loadOrCreate(root)
            val second = ManifestKeyStore.loadOrCreate(root)

            assertContentEquals(first.public.encoded, second.public.encoded)
            assertContentEquals(first.private.encoded, second.private.encoded)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `global facade exposes installed service`() {
        val service = ArcSyncService("example.test:25565", ManifestSigner.generate())
        try {
            ArcSync.install(service)
            assertSame(service, ArcSync.service)
        } finally {
            ArcSync.install(null)
        }
    }
}
