package dev.arc.test.assert

import org.bukkit.event.Event
import kotlin.reflect.KClass

/**
 * Captured event record held by [EventCapture].
 */
data class CapturedEvent(val event: Event, val timestamp: Long)

/**
 * Records every event fired through the server's event bus during a test.
 * Injected automatically into [ArcTestScope][dev.arc.test.ArcTestScope].
 */
class EventCapture {
    private val captured = mutableListOf<CapturedEvent>()
    private var tick: Long = 0L

    internal fun record(event: Event) {
        captured += CapturedEvent(event, tick)
    }

    internal fun advanceTick() { tick++ }

    fun clear() { captured.clear(); tick = 0L }

    // ---- Query ----

    fun <T : Event> all(type: KClass<T>): List<T> =
        captured.filter { type.isInstance(it.event) }.map { @Suppress("UNCHECKED_CAST") it.event as T }

    inline fun <reified T : Event> all(): List<T> = all(T::class)

    fun <T : Event> count(type: KClass<T>): Int = all(type).size

    inline fun <reified T : Event> count(): Int = count(T::class)

    // ---- Assertions ----

    fun <T : Event> assertFired(type: KClass<T>, times: Int = 1) {
        val actual = count(type)
        check(actual >= times) {
            "Expected ${type.simpleName} to fire at least $times time(s), but fired $actual"
        }
    }

    inline fun <reified T : Event> assertFired(times: Int = 1) = assertFired(T::class, times)

    fun <T : Event> assertNotFired(type: KClass<T>) {
        val actual = count(type)
        check(actual == 0) { "Expected ${type.simpleName} NOT to fire but fired $actual time(s)" }
    }

    inline fun <reified T : Event> assertNotFired() = assertNotFired(T::class)

    /**
     * Assert events fired in the given order (other events may appear in between).
     *
     * ```kotlin
     * events.assertOrder(PlayerLoginEvent::class, PlayerJoinEvent::class)
     * ```
     */
    fun assertOrder(vararg types: KClass<out Event>) {
        val timeline = captured.map { it.event::class }
        var pos = 0
        for (expected in types) {
            val found = timeline.indexOfFirst { pos <= timeline.indexOf(it) && expected.isInstance(captured[timeline.indexOf(it)].event) }
            check(found >= pos) {
                val names = types.map { it.simpleName }
                "Expected events in order $names but order was violated at ${expected.simpleName}"
            }
            pos = found + 1
        }
    }
}
