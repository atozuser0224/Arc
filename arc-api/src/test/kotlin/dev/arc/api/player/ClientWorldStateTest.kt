package dev.arc.api.player

import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientWorldStateTest {

    @AfterTest
    fun resetBackend() {
        ArcClientWorldStates.installBackend(null)
    }

    @Test
    fun `normalizes time and weather values`() {
        val state = clientWorldState {
            time(-1)
            storm(rain = 2f, thunder = -0.5f)
        }

        assertEquals(23_999L, state.dayTime)
        assertEquals(1f, state.rainLevel)
        assertEquals(0f, state.thunderLevel)
    }

    @Test
    fun `closing top session reapplies previous state then restores world`() {
        val backend = RecordingBackend()
        ArcClientWorldStates.installBackend(backend)
        val player = player()

        val first = player.openClientWorldState { time(6_000) }
        val second = player.openClientWorldState { time(18_000) }

        assertEquals(
            listOf<Event>(
                Event.Apply(ClientWorldState(dayTime = 6_000)),
                Event.Apply(ClientWorldState(dayTime = 18_000)),
            ),
            backend.events,
        )

        second.close()
        first.close()
        first.close()

        assertEquals(
            listOf<Event>(
                Event.Apply(ClientWorldState(dayTime = 6_000)),
                Event.Apply(ClientWorldState(dayTime = 18_000)),
                Event.Apply(ClientWorldState(dayTime = 6_000)),
                Event.Restore,
            ),
            backend.events,
        )
    }

    @Test
    fun `covered session changes stay hidden until it becomes top`() {
        val backend = RecordingBackend()
        ArcClientWorldStates.installBackend(backend)
        val player = player()
        val first = player.openClientWorldState { time(1_000) }
        val second = player.openClientWorldState { time(2_000) }

        assertTrue(first.update { time(3_000) })
        assertEquals(2, backend.events.size)

        second.close()
        assertEquals(Event.Apply(ClientWorldState(dayTime = 3_000)), backend.events.last())

        first.close()
    }

    @Test
    fun `closing a covered session does not disturb visible state`() {
        val backend = RecordingBackend()
        ArcClientWorldStates.installBackend(backend)
        val player = player()
        val first = player.openClientWorldState { time(1_000) }
        val second = player.openClientWorldState { time(2_000) }

        first.close()
        assertEquals(2, backend.events.size)

        second.close()
        assertEquals(Event.Restore, backend.events.last())
    }

    @Test
    fun `missing backend is reported without throwing`() {
        val session = player().openClientWorldState {
            clearWeather()
        }

        assertFalse(session.applied)
        assertFalse(session.update { time(10) })
        session.close()
        assertTrue(session.isClosed)
    }

    private sealed interface Event {
        data class Apply(val state: ClientWorldState) : Event
        data object Restore : Event
    }

    private class RecordingBackend : ClientWorldStateBackend {
        val events = mutableListOf<Event>()

        override fun apply(player: Player, state: ClientWorldState): Boolean {
            events += Event.Apply(state)
            return true
        }

        override fun restore(player: Player): Boolean {
            events += Event.Restore
            return true
        }
    }

    private fun player(): Player {
        val id = UUID.randomUUID()
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getUniqueId" -> id
                "toString" -> "Player($id)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as Player
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
