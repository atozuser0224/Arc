package dev.arc.api.pdc

import org.bukkit.persistence.PersistentDataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PdcSchemaTest {

    @Test
    fun `defines typed fields with lazy defaults`() {
        var calls = 0
        lateinit var kills: PdcField<Int, Int>
        lateinit var title: PdcField<String, String>

        val schema = pdcSchema("arc") {
            kills = int("kills") {
                default { calls++; 0 }
                validate("kills must not be negative") { it >= 0 }
            }
            title = string("title") { default("Rookie") }
        }

        assertEquals("arc:kills", kills.key.toString())
        assertSame(PersistentDataType.INTEGER, kills.type)
        assertEquals(0, kills.defaultValue())
        assertEquals(0, kills.defaultValue())
        assertEquals(2, calls)
        assertEquals("Rookie", title.defaultValue())
        assertSame(kills, schema["kills"])
        assertSame(title, schema["arc:title"])
        assertNull(schema.find("missing"))
    }

    @Test
    fun `rejects invalid values duplicate fields and invalid names`() {
        lateinit var kills: PdcField<Int, Int>
        pdcSchema("arc") {
            kills = int("kills") {
                validate("kills must not be negative") { it >= 0 }
            }
        }

        assertFailsWith<IllegalArgumentException> { kills.validate(-1) }
        assertEquals(3, kills.validate(3))

        assertFailsWith<IllegalArgumentException> {
            pdcSchema("arc") {
                int("same")
                string("same")
            }
        }
        assertFailsWith<IllegalArgumentException> { pdcSchema("Arc Invalid") {} }
        assertFailsWith<IllegalArgumentException> {
            pdcSchema("arc") { int("../escape") }
        }
    }
}
