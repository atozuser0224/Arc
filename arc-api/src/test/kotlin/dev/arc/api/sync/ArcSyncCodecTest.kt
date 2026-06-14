package dev.arc.api.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArcSyncCodecTest {

    @Test
    fun `client hello round trips with stable feature encoding`() {
        val hello = ClientHello(
            protocolVersion = ArcSyncProtocol.VERSION,
            features = setOf(ArcSyncFeature.CUSTOM_ITEMS, ArcSyncFeature.CREATIVE_TABS),
            cachedRevision = "abc",
            cachedHashes = setOf("hash-b", "hash-a"),
        )

        assertEquals(hello, ArcSyncCodec.decodeClientHello(ArcSyncCodec.encode(hello)))
    }

    @Test
    fun `decoder rejects trailing bytes`() {
        val encoded = ArcSyncCodec.encode(
            ClientHello(ArcSyncProtocol.VERSION, emptySet(), null, emptySet()),
        )

        assertFailsWith<IllegalStateException> {
            ArcSyncCodec.decodeClientHello(encoded + byteArrayOf(1))
        }
    }
}
