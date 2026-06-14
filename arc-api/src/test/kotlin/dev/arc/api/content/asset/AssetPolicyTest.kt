package dev.arc.api.content.asset

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetPolicyTest {

    @Test
    fun `policy rejects traversal executables and oversized files`() {
        val policy = AssetPolicy(maxAssetBytes = 16, maxPackBytes = 24)

        assertFalse(policy.validate("../secret.png", byteArrayOf()).accepted)
        assertFalse(policy.validate("mods/payload.jar", byteArrayOf()).accepted)
        assertFalse(policy.validate("textures/large.png", ByteArray(17)).accepted)
        assertTrue(policy.validate("textures/item/ruby.png", ByteArray(8)).accepted)
    }

    @Test
    fun `collector rejects duplicate paths and aggregate overflow`() {
        val collector = AssetCollector(AssetPolicy(maxAssetBytes = 16, maxPackBytes = 12))

        assertTrue(collector.add("textures/a.png", ByteArray(8)).accepted)
        assertFalse(collector.add("textures/a.png", ByteArray(1)).accepted)
        assertFalse(collector.add("sounds/b.ogg", ByteArray(5)).accepted)
        assertTrue(collector.assets.single().sha256.length == 64)
    }
}
