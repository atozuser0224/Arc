package dev.arc.api.content.pack

import dev.arc.api.content.asset.AssetPolicy
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileContentPackLoaderTest {

    @Test
    fun `loader compiles json definitions and discovers safe assets`() {
        val root = Files.createTempDirectory("arc-content-pack")
        try {
            root.resolve("pack.json").writeText(
                """
                {
                  "namespace": "magic",
                  "version": "1.0.0",
                  "items": [
                    {"id":"wand","fallback":"minecraft:blaze_rod","maxStackSize":1}
                  ],
                  "blocks": [
                    {"id":"altar","fallback":"minecraft:enchanting_table","hardness":4.0}
                  ],
                  "furniture": [
                    {"id":"chair","fallback":"minecraft:oak_stairs","seats":1}
                  ],
                  "recipes": [
                    {"id":"chair_recipe","result":"magic:chair","ingredients":["minecraft:oak_planks"]}
                  ]
                }
                """.trimIndent(),
            )
            root.resolve("assets/textures/item").createDirectories()
            root.resolve("assets/textures/item/wand.png").writeBytes(byteArrayOf(1, 2, 3))

            val loaded = FileContentPackLoader().load(root)

            assertNotNull(loaded.pack)
            assertEquals(
                listOf("magic:altar", "magic:chair", "magic:chair_recipe", "magic:wand"),
                loaded.pack.definitions.map { it.id.toString() }.sorted(),
            )
            assertEquals("textures/item/wand.png", loaded.assets.single().path)
            assertTrue(loaded.diagnostics.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loader reports missing metadata and asset policy failures`() {
        val root = Files.createTempDirectory("arc-content-pack")
        try {
            root.resolve("assets").createDirectories()
            root.resolve("assets/payload.jar").writeBytes(byteArrayOf(1))

            val loaded = FileContentPackLoader(
                AssetPolicy(maxAssetBytes = 16, maxPackBytes = 16),
            ).load(root)

            assertTrue(loaded.pack == null)
            assertTrue(loaded.diagnostics.any { it.field == "pack.json" })
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
