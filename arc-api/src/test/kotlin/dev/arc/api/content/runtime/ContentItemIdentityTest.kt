package dev.arc.api.content.runtime

import dev.arc.api.content.ContentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContentItemIdentityTest {

    @Test
    fun `identity round trips content id and sorted properties`() {
        val encoded = ContentItemIdentity.encode(
            ContentId.parse("magic:wand"),
            mapOf("charge" to "3", "mode" to "burst"),
        )
        val decoded = ContentItemIdentity.decode(encoded)

        assertEquals(ContentId.parse("magic:wand"), decoded.id)
        assertEquals(mapOf("charge" to "3", "mode" to "burst"), decoded.properties)
    }

    @Test
    fun `identity rejects oversized values and trailing bytes`() {
        assertFailsWith<IllegalArgumentException> {
            ContentItemIdentity.encode(
                ContentId.parse("magic:wand"),
                mapOf("key" to "x".repeat(1025)),
            )
        }
        val valid = ContentItemIdentity.encode(ContentId.parse("magic:wand"), emptyMap())
        assertFailsWith<IllegalStateException> {
            ContentItemIdentity.decode(valid + byteArrayOf(1))
        }
    }
}
