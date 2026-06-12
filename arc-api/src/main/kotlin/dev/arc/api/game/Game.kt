@file:JvmName("Games")

package dev.arc.api.game

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
//  State machine
// ---------------------------------------------------------------------------

/** Lifecycle states of an [ArcGame]. */
enum class GameState {
    /** Accepting players, not yet started. */
    WAITING,
    /** Countdown in progress — game starts soon. */
    STARTING,
    /** Match is live. */
    IN_GAME,
    /** Match finished, cleaning up. */
    ENDING,
    /** Fully stopped and removed. */
    STOPPED,
}

// ---------------------------------------------------------------------------
//  Base class
// ---------------------------------------------------------------------------

/**
 * Base class for a match or mini-game with a managed lifecycle.
 *
 * ```kotlin
 * class SurvivalGame(plugin: Plugin, id: String) : ArcGame(plugin, id) {
 *     override fun onStart() {
 *         broadcastAll("<green>Game started!")
 *         countdown(10) { start() }
 *     }
 *     override fun onEnd() = broadcastAll("<red>Game over!")
 *     override fun onPlayerJoin(p: Player) = p.sendMessage("<yellow>${p.name} joined.")
 *     override fun onPlayerLeave(p: Player) = p.sendMessage("<yellow>${p.name} left.")
 * }
 *
 * val game = SurvivalGame(plugin, "survival-1")
 * game.addPlayer(player)
 * game.start()
 * ```
 */
abstract class ArcGame(
    val plugin: Plugin,
    val id: String,
) {
    private val _players = ConcurrentHashMap.newKeySet<Player>()

    /** Current lifecycle state. */
    var state: GameState = GameState.WAITING
        private set

    /** Immutable snapshot of players currently in this game. */
    val players: Set<Player> get() = _players.toSet()

    /** Number of players currently in the game. */
    val playerCount: Int get() = _players.size

    // ---- Hooks (override in subclass) ----

    /** Called when [start] transitions the game to [GameState.IN_GAME]. */
    open fun onStart() {}

    /** Called when [end] transitions the game to [GameState.ENDING]. */
    open fun onEnd() {}

    /** Called when the game fully stops ([GameState.STOPPED]). Override to clean up resources. */
    open fun onStop() {}

    /** Called immediately after a player is added to this game. */
    open fun onPlayerJoin(player: Player) {}

    /** Called immediately before a player is removed from this game. */
    open fun onPlayerLeave(player: Player) {}

    /** Called each time [state] changes. */
    open fun onStateChange(from: GameState, to: GameState) {}

    // ---- Lifecycle ----

    /**
     * Transition to [GameState.STARTING] (intermediate state for countdowns).
     * Call [start] when ready to go live.
     */
    fun beginCountdown() = transition(GameState.STARTING)

    /** Transition to [GameState.IN_GAME] and invoke [onStart]. */
    fun start() {
        transition(GameState.IN_GAME)
        onStart()
    }

    /** Transition to [GameState.ENDING] and invoke [onEnd]. */
    fun end() {
        transition(GameState.ENDING)
        onEnd()
    }

    /** Fully stop the game: remove all players, transition to [GameState.STOPPED], invoke [onStop]. */
    fun stop() {
        players.forEach { removePlayer(it) }
        transition(GameState.STOPPED)
        onStop()
        ArcGameRegistry.unregister(id)
    }

    // ---- Player management ----

    /** Add [player] to this game and invoke [onPlayerJoin]. */
    fun addPlayer(player: Player) {
        _players.add(player)
        onPlayerJoin(player)
    }

    /** Remove [player] from this game and invoke [onPlayerLeave]. */
    fun removePlayer(player: Player) {
        if (_players.remove(player)) {
            onPlayerLeave(player)
        }
    }

    /** Whether [player] is in this game. */
    operator fun contains(player: Player): Boolean = player in _players

    // ---- Helpers ----

    /** Send [message] to every player in the game. */
    fun broadcastAll(message: String) {
        val comp = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
        _players.forEach { it.sendMessage(comp) }
    }

    /** Send a [net.kyori.adventure.text.Component] to every player in the game. */
    fun broadcastAll(message: net.kyori.adventure.text.Component) {
        _players.forEach { it.sendMessage(message) }
    }

    private fun transition(to: GameState) {
        val from = state
        state = to
        onStateChange(from, to)
    }
}

// ---------------------------------------------------------------------------
//  Registry
// ---------------------------------------------------------------------------

/**
 * Global registry of all live [ArcGame] instances.
 * Games self-register on construction and unregister on [ArcGame.stop].
 */
object ArcGameRegistry {
    @PublishedApi internal val games = ConcurrentHashMap<String, ArcGame>()

    /** Register a game. Called automatically in [ArcGame] constructor. */
    fun register(game: ArcGame) { games[game.id] = game }

    internal fun unregister(id: String) { games.remove(id) }

    /** Get a game by id, or null. */
    operator fun get(id: String): ArcGame? = games[id]

    /** All live games. */
    fun all(): List<ArcGame> = games.values.toList()

    /** All live games of type [T]. */
    inline fun <reified T : ArcGame> allOf(): List<T> = games.values.filterIsInstance<T>()

    /** Find the game [player] is currently in, or null. */
    fun gameOf(player: Player): ArcGame? = games.values.find { player in it }
}

// ---------------------------------------------------------------------------
//  DSL helpers
// ---------------------------------------------------------------------------

/**
 * Create and register a simple [ArcGame] inline without subclassing.
 *
 * ```kotlin
 * val game = plugin.game("lobby-1") {
 *     onStart { broadcastAll("<green>Go!") }
 *     onEnd   { broadcastAll("<red>Done.") }
 * }
 * game.addPlayer(player)
 * game.start()
 * ```
 */
fun Plugin.game(id: String, block: InlineGame.() -> Unit = {}): InlineGame =
    InlineGame(this, id).apply(block).also { ArcGameRegistry.register(it) }

/** [ArcGame] subclass used by the [Plugin.game] DSL. */
class InlineGame internal constructor(plugin: Plugin, id: String) : ArcGame(plugin, id) {
    private var startFn: (InlineGame.() -> Unit)? = null
    private var endFn:   (InlineGame.() -> Unit)? = null
    private var stopFn:  (InlineGame.() -> Unit)? = null
    private var joinFn:  (InlineGame.(Player) -> Unit)? = null
    private var leaveFn: (InlineGame.(Player) -> Unit)? = null
    private var stateFn: (InlineGame.(GameState, GameState) -> Unit)? = null

    fun onStart(block: InlineGame.() -> Unit)  { startFn = block }
    fun onEnd(block: InlineGame.() -> Unit)    { endFn   = block }
    fun onStop(block: InlineGame.() -> Unit)   { stopFn  = block }
    fun onJoin(block: InlineGame.(Player) -> Unit)  { joinFn  = block }
    fun onLeave(block: InlineGame.(Player) -> Unit) { leaveFn = block }
    fun onState(block: InlineGame.(GameState, GameState) -> Unit) { stateFn = block }

    override fun onStart()  { startFn?.invoke(this) }
    override fun onEnd()    { endFn?.invoke(this) }
    override fun onStop()   { stopFn?.invoke(this) }
    override fun onPlayerJoin(player: Player)  { joinFn?.invoke(this, player) }
    override fun onPlayerLeave(player: Player) { leaveFn?.invoke(this, player) }
    override fun onStateChange(from: GameState, to: GameState) { stateFn?.invoke(this, from, to) }
}
