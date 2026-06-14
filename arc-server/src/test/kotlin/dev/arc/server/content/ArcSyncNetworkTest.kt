package dev.arc.server.content

import dev.arc.api.content.ContentCompiler
import dev.arc.api.content.asset.ContentAsset
import dev.arc.api.content.contentPack
import dev.arc.api.sync.ActivatePacket
import dev.arc.api.sync.ArcSync
import dev.arc.api.sync.ArcSyncFeature
import dev.arc.api.sync.ArcSyncPacketCodec
import dev.arc.api.sync.ArcSyncProtocol
import dev.arc.api.sync.ArcSyncService
import dev.arc.api.sync.BlobChunkPacket
import dev.arc.api.sync.BlobRequestPacket
import dev.arc.api.sync.ClientHello
import dev.arc.api.sync.ClientHelloPacket
import dev.arc.api.sync.ManifestSigner
import dev.arc.api.sync.ServerManifestPacket
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArcSyncNetworkTest {

    @AfterTest
    fun cleanup() {
        ArcSync.install(null)
        ArcSyncNetwork.disconnect(PLAYER)
    }

    @Test
    fun `network negotiates manifest and streams requested blobs`() {
        val revision = requireNotNull(
            ContentCompiler.compile(listOf(contentPack("magic", "1") { item("wand") { } })).revision,
        )
        val asset = ContentAsset("textures/item/wand.png", byteArrayOf(1, 2, 3))
        ArcSync.install(ArcSyncService("example.test", ManifestSigner.generate()).apply {
            publish(revision, listOf(asset))
        })
        val sent = mutableListOf<ByteArray>()

        ArcSyncNetwork.handle(
            PLAYER,
            ArcSyncPacketCodec.encode(
                ClientHelloPacket(
                    ClientHello(
                        ArcSyncProtocol.VERSION,
                        ArcSyncFeature.entries.toSet(),
                        null,
                        emptySet(),
                    ),
                ),
            ),
            { sent += it },
        )

        val manifest = assertIs<ServerManifestPacket>(ArcSyncPacketCodec.decode(sent.single()))
        val requested = manifest.signedManifest.manifest.blobs.map { it.sha256 }
        sent.clear()
        ArcSyncNetwork.handle(
            PLAYER,
            ArcSyncPacketCodec.encode(BlobRequestPacket(requested)),
            { sent += it },
        )

        assertIs<ActivatePacket>(ArcSyncPacketCodec.decode(sent.last()))
        val chunks = sent.dropLast(1).map { assertIs<BlobChunkPacket>(ArcSyncPacketCodec.decode(it)) }
        assertEquals(requested.toSet(), chunks.map { it.sha256 }.toSet())
        val assetBytes = chunks.filter { it.sha256 == asset.sha256 }
            .sortedBy { it.offset }
            .flatMap { it.bytes.asIterable() }
            .toByteArray()
        assertContentEquals(asset.bytes, assetBytes)
        assertTrue(chunks.all { it.bytes.size <= ArcSyncPacketCodec.MAX_CHUNK_BYTES })
    }

    private companion object {
        val PLAYER: UUID = UUID.fromString("a2f2042a-4718-40f6-a96c-a254fde8d4ca")
    }
}
