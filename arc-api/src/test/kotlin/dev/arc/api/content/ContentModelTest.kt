package dev.arc.api.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentModelTest {

    @Test
    fun `content ids accept namespaced lowercase values`() {
        val id = ContentId.parse("arc:ruby_sword")

        assertEquals("arc", id.namespace)
        assertEquals("ruby_sword", id.path)
        assertEquals("arc:ruby_sword", id.toString())
    }

    @Test
    fun `content ids reject uppercase missing namespace and traversal`() {
        assertFailsWith<IllegalArgumentException> { ContentId.parse("Arc:ruby") }
        assertFailsWith<IllegalArgumentException> { ContentId.parse("ruby") }
        assertFailsWith<IllegalArgumentException> { ContentId.parse("arc:../ruby") }
    }

    @Test
    fun `compiler reports invalid item and block properties`() {
        val pack = contentPack("arc", "1.0.0") {
            item("oversized") {
                fallback = "minecraft:paper"
                maxStackSize = 65
            }
            block("negative_hardness") {
                fallback = "minecraft:redstone_block"
                hardness = -1.0f
            }
        }

        val result = ContentCompiler.compile(listOf(pack))

        assertTrue(result.diagnostics.any { it.contentId?.toString() == "arc:oversized" })
        assertTrue(result.diagnostics.any { it.contentId?.toString() == "arc:negative_hardness" })
        assertTrue(result.diagnostics.all { it.severity == DiagnosticSeverity.ERROR })
    }
}
