package dev.arc.api.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ArcSyncPacketCodecTest {

    @Test
    fun `wire packets round trip through one bounded codec`() {
        val keys = ManifestSigner.generate()
        val manifest = ArcSyncManifest(
            ArcSyncProtocol.VERSION,
            "example.test",
            "revision",
            setOf(ArcSyncFeature.CREATIVE_TABS, ArcSyncFeature.CUSTOM_ITEMS),
            listOf(SyncBlob("arc/catalog.json", "abc", 3)),
        )
        val packets = listOf<ArcSyncPacket>(
            ClientHelloPacket(ClientHello(1, setOf(ArcSyncFeature.CREATIVE_TABS), null, setOf("abc"))),
            ServerManifestPacket(keys.public.encoded, ManifestSigner.sign(manifest, keys.private)),
            BlobRequestPacket(listOf("abc")),
            BlobChunkPacket("abc", 0, 3, byteArrayOf(1, 2, 3)),
            ActivatePacket("revision"),
            FailurePacket("unsupported"),
        )

        packets.forEach { packet ->
            val decoded = ArcSyncPacketCodec.decode(ArcSyncPacketCodec.encode(packet))
            assertPacketEquals(packet, decoded)
        }
    }

    private fun assertPacketEquals(expected: ArcSyncPacket, actual: ArcSyncPacket) {
        if (expected is BlobChunkPacket && actual is BlobChunkPacket) {
            assertEquals(expected.copy(bytes = byteArrayOf()), actual.copy(bytes = byteArrayOf()))
            assertContentEquals(expected.bytes, actual.bytes)
        } else {
            assertEquals(expected, actual)
        }
    }
}
