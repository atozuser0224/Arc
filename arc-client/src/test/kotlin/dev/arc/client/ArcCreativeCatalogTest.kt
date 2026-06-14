package dev.arc.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArcCreativeCatalogTest {

    @Test
    fun `catalog parser creates deterministic creative entries`() {
        val catalog = ArcCreativeCatalog.parse(
            """
            {
              "revision":"abc",
              "title":"Arc: magic",
              "entries":[
                {"id":"magic:altar","type":"block","pack":"magic:pack","order":10,"fallback":"minecraft:stone"},
                {"id":"magic:wand","type":"item","pack":"magic:pack","order":20,"fallback":"minecraft:blaze_rod"}
              ]
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals("Arc: magic", catalog.title)
        assertEquals(listOf("magic:altar", "magic:wand"), catalog.entries.map { it.id })
    }

    @Test
    fun `catalog rejects duplicate entry ids`() {
        val json =
            """{"revision":"a","title":"Arc","entries":[
                {"id":"arc:x","type":"item","pack":"arc:pack","order":0,"fallback":"minecraft:paper"},
                {"id":"arc:x","type":"item","pack":"arc:pack","order":1,"fallback":"minecraft:paper"}
            ]}"""

        assertFailsWith<IllegalStateException> {
            ArcCreativeCatalog.parse(json.toByteArray())
        }
    }
}
