package dev.arc.api.content.pack

import dev.arc.api.content.ContentId
import dev.arc.api.content.ContentRegistry
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentPackDirectoryTest {

    @Test
    fun `directory discovery publishes all child packs atomically`() {
        val root = Files.createTempDirectory("arc-content")
        try {
            writePack(root.resolve("magic"), "magic", "wand")
            writePack(root.resolve("tech"), "tech", "wrench")
            root.resolve("magic/assets/textures/item").createDirectories()
            root.resolve("magic/assets/textures/item/wand.png").writeBytes(byteArrayOf(1))
            val registry = ContentRegistry()

            val result = ContentPackDirectory().publish(root, registry)

            assertTrue(result.accepted)
            assertTrue(ContentId.parse("magic:wand") in registry.current!!.definitions)
            assertTrue(ContentId.parse("tech:wrench") in registry.current!!.definitions)
            assertTrue(result.assets.single().path == "textures/item/wand.png")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `invalid child pack leaves previous revision active`() {
        val root = Files.createTempDirectory("arc-content")
        try {
            val registry = ContentRegistry()
            registry.publish(listOf(dev.arc.api.content.contentPack("stable", "1") { item("item") { } }))
            val previous = registry.current
            root.resolve("broken").createDirectories()

            val result = ContentPackDirectory().publish(root, registry)

            assertFalse(result.accepted)
            assertTrue(registry.current === previous)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun writePack(path: java.nio.file.Path, namespace: String, item: String) {
        path.createDirectories()
        path.resolve("pack.json").writeText(
            """{"namespace":"$namespace","version":"1","items":[{"id":"$item"}]}""",
        )
    }
}
