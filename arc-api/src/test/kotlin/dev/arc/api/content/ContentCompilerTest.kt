package dev.arc.api.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContentCompilerTest {

    @Test
    fun `compiler creates deterministic revision and sorted creative catalog`() {
        val pack = contentPack("magic", "1.0.0") {
            item("wand") {
                fallback = "minecraft:blaze_rod"
                order = 20
            }
            block("altar") {
                fallback = "minecraft:enchanting_table"
                order = 10
            }
        }

        val first = assertNotNull(ContentCompiler.compile(listOf(pack)).revision)
        val second = assertNotNull(ContentCompiler.compile(listOf(pack)).revision)

        assertEquals(first.hash, second.hash)
        assertEquals(
            listOf("magic:altar", "magic:wand"),
            first.catalog.entries.map { it.id.toString() },
        )
        assertEquals("Arc: magic", first.catalog.title)
    }

    @Test
    fun `duplicate content ids reject the revision`() {
        val first = contentPack("first", "1") {
            item("shared") { id = ContentId.parse("arc:shared") }
        }
        val second = contentPack("second", "1") {
            block("shared") { id = ContentId.parse("arc:shared") }
        }

        val result = ContentCompiler.compile(listOf(first, second))

        assertNull(result.revision)
        assertEquals("duplicate", result.diagnostics.single().field)
    }
}
