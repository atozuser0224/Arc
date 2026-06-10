package dev.arc.server.nms

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectTest {

    @AfterTest
    fun clear() {
        Reflect.clearCaches()
    }

    @Test
    fun `selects overload by compatible argument type`() {
        val target = Overloaded()

        assertEquals("string", Reflect.invoke(target, "pick", "value"))
        assertEquals("number", Reflect.invoke(target, "pick", 42))
        assertEquals("pair", Reflect.invoke(target, "pick", "value", 42))
        assertEquals("sequence", Reflect.invoke(target, "generic", StringBuilder("value")))
    }

    @Test
    fun `supports primitive constructor arguments`() {
        val result = Reflect.constructByArity(PrimitiveConstructor::class.java, 1, 7)

        assertEquals(7, (result as PrimitiveConstructor).value)
    }

    @Test
    fun `uses cached field getter handles`() {
        val target = FieldTarget()
        val field = Reflect.field(FieldTarget::class.java, "value")!!

        assertEquals(11, Reflect.intField(field, target))
        assertEquals(11, Reflect.intField(field, target))
        assertTrue(Reflect.stats().methodHandles > 0)
    }

    @Test
    fun `negative lookups are cached`() {
        assertNull(Reflect.cls("dev.arc.server.nms.DoesNotExist"))
        val afterMiss = Reflect.stats()
        assertNull(Reflect.cls("dev.arc.server.nms.DoesNotExist"))
        val afterHit = Reflect.stats()

        assertEquals(afterMiss.misses, afterHit.misses)
        assertTrue(afterHit.hits > afterMiss.hits)
        assertEquals(1, afterHit.resolutionFailures)
    }

    @Test
    fun `failed invocation is counted`() {
        assertNull(Reflect.invoke(Overloaded(), "explode"))

        assertEquals(1, Reflect.stats().invocationFailures)
    }

    @Test
    fun `warm invocation path does not add lookup misses`() {
        val target = Overloaded()
        assertEquals("string", Reflect.invoke(target, "pick", "value"))
        val cold = Reflect.stats()

        repeat(10_000) {
            assertEquals("string", Reflect.invoke(target, "pick", "value"))
        }
        val warm = Reflect.stats()

        assertEquals(cold.misses, warm.misses)
        assertTrue(warm.hits >= cold.hits + 10_000)
    }

    @Test
    fun `cache remains consistent under concurrent access`() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val calls = List(1_000) {
                Callable {
                    Reflect.invoke(Overloaded(), "pick", "value")
                }
            }
            val results = executor.invokeAll(calls).map { it.get() }

            assertTrue(results.all { it == "string" })
            assertTrue(Reflect.stats().hits > 0)
            assertTrue(Reflect.stats().methodHandles > 0)
        } finally {
            executor.shutdownNow()
        }
    }

    private class Overloaded {
        fun pick(value: String): String = "string"
        fun pick(value: Number): String = "number"
        fun pick(value: String, count: Int): String = "pair"
        fun generic(value: Any): String = "any"
        fun generic(value: CharSequence): String = "sequence"
        fun explode(): String = error("expected")
    }

    private class PrimitiveConstructor(val value: Int)
    private class FieldTarget {
        private val value = 11
    }
}
