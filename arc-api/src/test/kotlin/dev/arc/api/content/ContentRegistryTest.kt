package dev.arc.api.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentRegistryTest {

    @Test
    fun `failed publish retains previous revision`() {
        val registry = ContentRegistry()
        val accepted = registry.publish(
            listOf(contentPack("magic", "1") { item("wand") { } }),
        )
        val previous = registry.current

        val rejected = registry.publish(
            listOf(contentPack("magic", "2") { item("wand") { maxStackSize = 0 } }),
        )

        assertTrue(accepted.accepted)
        assertFalse(rejected.accepted)
        assertSame(previous, registry.current)
    }

    @Test
    fun `listeners observe only accepted revisions`() {
        val registry = ContentRegistry()
        val hashes = mutableListOf<String>()
        val subscription = registry.subscribe { hashes += it.hash }

        registry.publish(listOf(contentPack("magic", "1") { item("wand") { } }))
        registry.publish(listOf(contentPack("magic", "2") { item("bad") { maxStackSize = 0 } }))
        subscription.close()
        registry.publish(listOf(contentPack("magic", "3") { item("staff") { } }))

        assertTrue(hashes.size == 1)
    }
}
