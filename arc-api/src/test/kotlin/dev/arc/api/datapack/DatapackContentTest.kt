package dev.arc.api.datapack

import com.google.gson.JsonParser
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatapackContentTest {

    @Test
    fun `uses 1_21 resource paths and escapes json values`() {
        val pack = DatapackContent("arc_pack")
        pack.tag("blocks", "arc:ores") {
            add("minecraft:diamond_ore")
            addTag("arc:extra_ores")
        }
        pack.lootTable("arc:chests/test") {
            pool(1) {
                entry("minecraft:paper", count = 1..2)
            }
        }
        pack.recipe("arc:quoted", """{"type":"arc:test","value":"a\"b"}""")

        assertTrue(pack.contains("data/arc/tags/block/ores.json"))
        assertTrue(pack.contains("data/arc/loot_table/chests/test.json"))
        assertTrue(pack.contains("data/arc/recipe/quoted.json"))

        val tag = JsonParser.parseString(pack.resource("data/arc/tags/block/ores.json")).asJsonObject
        assertEquals("minecraft:diamond_ore", tag["values"].asJsonArray[0].asString)
        assertEquals("#arc:extra_ores", tag["values"].asJsonArray[1].asString)
        JsonParser.parseString(pack.resource("data/arc/recipe/quoted.json"))
    }

    @Test
    fun `function builder writes one command per line`() {
        val pack = DatapackContent("arc_pack")
        pack.function("arc:load") {
            +"say loaded"
            command("scoreboard players set #arc state 1")
        }

        assertEquals(
            "say loaded\nscoreboard players set #arc state 1\n",
            pack.resource("data/arc/function/load.mcfunction"),
        )
        assertFailsWith<IllegalArgumentException> {
            pack.function("arc:bad") { command("say one\nsay two") }
        }
    }

    @Test
    fun `validates resource ids paths and builder ranges`() {
        assertFailsWith<IllegalArgumentException> { ResourceId.parse("missing_namespace") }
        assertFailsWith<IllegalArgumentException> { ResourceId.parse("Arc:upper") }
        assertFailsWith<IllegalArgumentException> { DatapackContent("../escape") }

        val pack = DatapackContent("arc_pack")
        assertFailsWith<IllegalArgumentException> { pack.raw("../escape.json", "{}") }
        assertFailsWith<IllegalArgumentException> {
            pack.lootTable("arc:bad") { pool(0) { empty() } }
        }
        assertFailsWith<IllegalArgumentException> {
            pack.lootTable("arc:bad") { pool(1) { entry("minecraft:stone", weight = 0) } }
        }
    }

    @Test
    fun `rejects duplicate resource paths`() {
        val pack = DatapackContent("arc_pack")
        pack.recipe("arc:test", "{}")

        assertFailsWith<IllegalArgumentException> {
            pack.recipe("arc:test", "{}")
        }
    }

    @Test
    fun `deploy writes metadata and only removes stale managed files`() {
        val root = Files.createTempDirectory("arc-datapack-test")
        try {
            val first = DatapackContent("arc_pack").apply {
                function("arc:first") { +"say first" }
                function("arc:stale") { +"say stale" }
            }
            val packDir = first.deployTo(root)
            val unrelated = packDir.resolve("data/arc/function/manual.mcfunction")
            unrelated.parent.createDirectories()
            unrelated.writeText("say manual\n")

            val second = DatapackContent("arc_pack").apply {
                function("arc:first") { +"say updated" }
            }
            second.deployTo(root)

            assertTrue(packDir.resolve("pack.mcmeta").exists())
            assertEquals(
                61,
                JsonParser.parseString(packDir.resolve("pack.mcmeta").readText())
                    .asJsonObject["pack"].asJsonObject["pack_format"].asInt,
            )
            assertTrue(packDir.resolve("data/arc/function/first.mcfunction").exists())
            assertFalse(packDir.resolve("data/arc/function/stale.mcfunction").exists())
            assertTrue(unrelated.exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
