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

    @Test
    fun `owned packs merge and unregister independently`() {
        val registry = ContentRegistry()

        assertTrue(registry.install("plugin-a", contentPack("alpha", "1") { item("wand") { } }).accepted)
        assertTrue(registry.install("plugin-b", contentPack("beta", "1") { block("altar") { } }).accepted)
        assertTrue(registry.current!!.definitions.keys.map { it.toString() }.containsAll(
            listOf("alpha:wand", "beta:altar"),
        ))

        assertTrue(registry.uninstall("plugin-a").accepted)
        assertFalse(ContentId.parse("alpha:wand") in registry.current!!.definitions)
        assertTrue(ContentId.parse("beta:altar") in registry.current!!.definitions)
    }

    @Test
    fun `owned packs preserve directory published base packs`() {
        val registry = ContentRegistry()
        registry.publish(listOf(contentPack("files", "1") { item("file_item") { } }))

        val installed = registry.install(
            "plugin",
            contentPack("plugin", "1") { item("plugin_item") { } },
        )

        assertTrue(installed.accepted)
        assertTrue(ContentId.parse("files:file_item") in registry.current!!.definitions)
        assertTrue(ContentId.parse("plugin:plugin_item") in registry.current!!.definitions)
    }
}
