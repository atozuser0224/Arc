@file:JvmName("ClientWorldStates")

package dev.arc.api.player

import org.bukkit.entity.Player
import java.util.UUID

/** Immutable per-player world state sent only to one player's client. */
public data class ClientWorldState(
    public val dayTime: Long? = null,
    public val rainLevel: Float? = null,
    public val thunderLevel: Float? = null,
) {
    init {
        require(dayTime == null || dayTime in 0L..23_999L) {
            "dayTime must be in 0..23999: $dayTime"
        }
        require(rainLevel == null || rainLevel in 0f..1f) {
            "rainLevel must be in 0..1: $rainLevel"
        }
        require(thunderLevel == null || thunderLevel in 0f..1f) {
            "thunderLevel must be in 0..1: $thunderLevel"
        }
    }
}

/** Server-side bridge that converts stable state values into versioned packets. */
public interface ClientWorldStateBackend {
    public fun apply(player: Player, state: ClientWorldState): Boolean
    public fun restore(player: Player): Boolean
}

@DslMarker
public annotation class ClientWorldStateDsl

/** Kotlin DSL for a normalized [ClientWorldState]. */
@ClientWorldStateDsl
public class ClientWorldStateBuilder internal constructor() {
    private var dayTime: Long? = null
    private var rainLevel: Float? = null
    private var thunderLevel: Float? = null

    /** Override the visible time, wrapping into the Minecraft day range. */
    public fun time(ticks: Long) {
        dayTime = Math.floorMod(ticks, 24_000L)
    }

    /** Override rain density without changing thunder. */
    public fun rain(level: Float = 1f) {
        rainLevel = level.coerceIn(0f, 1f)
    }

    /** Override thunder intensity without changing rain. */
    public fun thunder(level: Float) {
        thunderLevel = level.coerceIn(0f, 1f)
    }

    /** Override both rain and thunder intensities. */
    public fun storm(
        rain: Float = 1f,
        thunder: Float = 0.8f,
    ) {
        this.rainLevel = rain.coerceIn(0f, 1f)
        this.thunderLevel = thunder.coerceIn(0f, 1f)
    }

    /** Show clear weather while retaining any configured time override. */
    public fun clearWeather() {
        rainLevel = 0f
        thunderLevel = 0f
    }

    internal fun build(): ClientWorldState = ClientWorldState(dayTime, rainLevel, thunderLevel)
}

/** Build a normalized immutable client world state. */
public fun clientWorldState(block: ClientWorldStateBuilder.() -> Unit): ClientWorldState =
    ClientWorldStateBuilder().apply(block).build()

/**
 * A stacked per-player override. Closing the visible session reveals the
 * previous session, or restores the real world when no sessions remain.
 */
public class ClientWorldStateSession internal constructor(
    internal val player: Player,
    initialState: ClientWorldState,
) : AutoCloseable {
    @Volatile
    public var state: ClientWorldState = initialState
        private set

    @Volatile
    public var applied: Boolean = false
        internal set

    @Volatile
    public var isClosed: Boolean = false
        private set

    /** Replace this session's state. Covered sessions remain network-silent. */
    public fun update(block: ClientWorldStateBuilder.() -> Unit): Boolean {
        if (isClosed) return false
        val next = clientWorldState(block)
        return ArcClientWorldStates.update(this, next)
    }

    /** Idempotently remove this override from the player's session stack. */
    override fun close() {
        if (isClosed) return
        ArcClientWorldStates.close(this)
    }

    internal fun replace(next: ClientWorldState) {
        state = next
    }

    internal fun markClosed() {
        isClosed = true
    }
}

/** Global backend and stack coordinator installed by `arc-server`. */
public object ArcClientWorldStates {
    private val lock = Any()
    private val sessions = HashMap<UUID, MutableList<ClientWorldStateSession>>()

    @Volatile
    private var backend: ClientWorldStateBackend? = null

    /** Install or remove the server bridge. Removing it also clears stale stacks. */
    @JvmStatic
    public fun installBackend(backend: ClientWorldStateBackend?) {
        synchronized(lock) {
            this.backend = backend
            if (backend == null) {
                sessions.values.flatten().forEach { it.markClosed() }
                sessions.clear()
            }
        }
    }

    internal fun open(
        player: Player,
        state: ClientWorldState,
    ): ClientWorldStateSession = synchronized(lock) {
        val session = ClientWorldStateSession(player, state)
        sessions.getOrPut(player.uniqueId) { ArrayList() } += session
        session.applied = backend?.apply(player, state) ?: false
        session
    }

    internal fun update(
        session: ClientWorldStateSession,
        next: ClientWorldState,
    ): Boolean = synchronized(lock) {
        if (session.isClosed) return@synchronized false
        val stack = sessions[session.player.uniqueId] ?: return@synchronized false
        val index = stack.indexOf(session)
        if (index < 0) return@synchronized false
        session.replace(next)
        val result = if (index == stack.lastIndex) {
            backend?.apply(session.player, next) ?: false
        } else {
            backend != null
        }
        session.applied = result
        result
    }

    internal fun close(session: ClientWorldStateSession) {
        synchronized(lock) {
            if (session.isClosed) return
            val playerId = session.player.uniqueId
            val stack = sessions[playerId]
            val index = stack?.indexOf(session) ?: -1
            session.markClosed()
            if (stack == null || index < 0) return

            val wasVisible = index == stack.lastIndex
            stack.removeAt(index)
            if (!wasVisible) return

            if (stack.isEmpty()) {
                sessions.remove(playerId)
                backend?.restore(session.player)
            } else {
                val visible = stack.last()
                visible.applied = backend?.apply(visible.player, visible.state) ?: false
            }
        }
    }
}

/** Open and immediately apply a stacked client-only world state override. */
public fun Player.openClientWorldState(
    block: ClientWorldStateBuilder.() -> Unit,
): ClientWorldStateSession = ArcClientWorldStates.open(this, clientWorldState(block))
