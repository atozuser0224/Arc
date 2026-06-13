package dev.arc.api.pdc

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `holder operators validate mutate and increment fields`() {
        val holder = holder()
        lateinit var kills: PdcField<Int, Int>
        lateinit var score: PdcField<Long, Long>
        val schema = pdcSchema("arc") {
            kills = int("kills") {
                default(0)
                validate("kills must not be negative") { it >= 0 }
            }
            score = long("score") { default(0L) }
        }

        assertEquals(2, schema.size)
        assertEquals(0, holder.getOrDefault(kills))
        assertFalse(kills in holder)

        assertEquals(3, holder.getOrPut(kills) { 3 })
        assertTrue(kills in holder)
        assertEquals(5, holder.mutate(kills) { it + 2 })
        assertEquals(7, holder.increment(kills, 2))
        assertEquals(7, holder[kills])

        assertFailsWith<IllegalArgumentException> { holder[kills] = -1 }
        assertFailsWith<ArithmeticException> { holder.increment(kills, Int.MAX_VALUE) }
        assertEquals(7, holder[kills])

        assertEquals(4L, holder.increment(score, 4L))
        assertFailsWith<ArithmeticException> { holder.increment(score, Long.MAX_VALUE) }
        assertEquals(4L, holder[score])

        holder -= kills
        assertFalse(kills in holder)
    }

    private fun holder(): PersistentDataHolder {
        val values = LinkedHashMap<NamespacedKey, Any>()
        val container = Proxy.newProxyInstance(
            PersistentDataContainer::class.java.classLoader,
            arrayOf(PersistentDataContainer::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "set" -> {
                    values[args!![0] as NamespacedKey] = args[2]!!
                    null
                }
                "get" -> values[args!![0] as NamespacedKey]
                "has" -> values.containsKey(args!![0] as NamespacedKey)
                "remove" -> {
                    values.remove(args!![0] as NamespacedKey)
                    null
                }
                "getKeys" -> values.keys.toSet()
                "isEmpty" -> values.isEmpty()
                "toString" -> values.toString()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as PersistentDataContainer

        return Proxy.newProxyInstance(
            PersistentDataHolder::class.java.classLoader,
            arrayOf(PersistentDataHolder::class.java),
        ) { proxy, method, _ ->
            when (method.name) {
                "getPersistentDataContainer" -> container
                "toString" -> "TestHolder"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        } as PersistentDataHolder
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
