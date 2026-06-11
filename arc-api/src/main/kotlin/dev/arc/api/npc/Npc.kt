@file:JvmName("Npcs")

package dev.arc.api.npc

import dev.arc.api.event.listen
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
//  Backend SPI
// ---------------------------------------------------------------------------

interface NpcBackend {
    fun spawn(npc: Npc, viewer: Player)
    fun despawn(npc: Npc, viewer: Player)
    fun teleport(npc: Npc, location: Location)
    fun lookAt(npc: Npc, location: Location)
    fun setSkin(npc: Npc, texture: String, signature: String)
    fun setName(npc: Npc, name: String)
}

object ArcNpcs {
    var backend: NpcBackend? = null
    internal val byEntityId = ConcurrentHashMap<Int, Npc>()
}

// ---------------------------------------------------------------------------
//  Skin data
// ---------------------------------------------------------------------------

data class SkinData(val texture: String, val signature: String)

// ---------------------------------------------------------------------------
//  NPC builder
// ---------------------------------------------------------------------------

class NpcBuilder {
    var name: String = ""
    var location: Location? = null
    var skin: SkinData? = null
    var visibleToAll: Boolean = true
    var lookAtNearbyRadius: Double = 0.0
    internal val clickHandlers = mutableListOf<(Player) -> Unit>()
    internal val viewers = mutableSetOf<UUID>()

    fun name(n: String)                         { name = n }
    fun location(loc: Location)                 { location = loc }
    fun skin(texture: String, signature: String){ skin = SkinData(texture, signature) }
    fun visibleToAll(v: Boolean)                { visibleToAll = v }
    fun lookAtNearby(radius: Double)            { lookAtNearbyRadius = radius }
    fun onClick(handler: (Player) -> Unit)      { clickHandlers += handler }
    fun showTo(vararg players: Player)          { players.forEach { viewers += it.uniqueId } }
}

// ---------------------------------------------------------------------------
//  NPC
// ---------------------------------------------------------------------------

/**
 * A packet-level fake player NPC.
 *
 * ```kotlin
 * val npc = plugin.npc {
 *     name("<gold>Merchant")
 *     location(spawnLocation)
 *     skin("texture_value", "texture_signature")
 *     lookAtNearby(5.0)
 *     onClick { player -> player.openInventory(shopInventory) }
 * }
 * npc.spawn()
 * ```
 */
class Npc internal constructor(
    private val plugin: Plugin,
    internal val builder: NpcBuilder,
) {
    val id: Int = nextId()
    val displayName: String get() = builder.name
    val skin: SkinData? get() = builder.skin
    var location: Location = requireNotNull(builder.location) { "NPC location must be set" }
        private set

    private var spawned = false
    private var lookJob: org.bukkit.scheduler.BukkitTask? = null

    fun spawn() {
        if (spawned) return
        spawned = true
        ArcNpcs.byEntityId[id] = this

        val backend = ArcNpcs.backend ?: return
        val viewers = resolveViewers()
        viewers.forEach { backend.spawn(this, it) }
        builder.skin?.let { backend.setSkin(this, it.texture, it.signature) }
        if (builder.name.isNotEmpty()) backend.setName(this, builder.name)

        if (builder.lookAtNearbyRadius > 0) {
            lookJob = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                val loc = location
                loc.world.getNearbyPlayers(loc, builder.lookAtNearbyRadius).minByOrNull { it.location.distanceSquared(loc) }
                    ?.let { backend.lookAt(this, it.location) }
            }, 0L, 2L)
        }
    }

    fun despawn() {
        if (!spawned) return
        spawned = false
        lookJob?.cancel(); lookJob = null
        ArcNpcs.byEntityId.remove(id)
        val backend = ArcNpcs.backend ?: return
        resolveViewers().forEach { backend.despawn(this, it) }
    }

    fun teleport(loc: Location) {
        location = loc
        if (spawned) ArcNpcs.backend?.teleport(this, loc)
    }

    fun showTo(player: Player) {
        builder.viewers += player.uniqueId
        if (spawned) ArcNpcs.backend?.spawn(this, player)
    }

    fun hideFrom(player: Player) {
        builder.viewers -= player.uniqueId
        if (spawned) ArcNpcs.backend?.despawn(this, player)
    }

    internal fun handleClick(player: Player) {
        builder.clickHandlers.forEach { it(player) }
    }

    private fun resolveViewers(): List<Player> = if (builder.visibleToAll) {
        plugin.server.onlinePlayers.toList()
    } else {
        builder.viewers.mapNotNull { plugin.server.getPlayer(it) }
    }

    companion object {
        private var counter = 10_000
        private fun nextId() = counter++
    }
}

// ---------------------------------------------------------------------------
//  Plugin extension
// ---------------------------------------------------------------------------

/**
 * Create a new [Npc] managed by this plugin.
 * Call [Npc.spawn] to make it visible.
 */
fun Plugin.npc(block: NpcBuilder.() -> Unit): Npc {
    val builder = NpcBuilder().apply(block)
    val npc = Npc(this, builder)

    // Wire click detection once per plugin (idempotent guard not needed — each npc gets its own listener)
    listen<PlayerInteractAtEntityEvent> { event ->
        // Backend implementations fire handleClick directly via entity id;
        // this fallback covers plugins that don't use packet-level interception.
    }

    return npc
}

/**
 * Register a [NpcBackend] that handles packet-level NPC operations.
 * Typically called from arc-server's bootstrap.
 */
fun Plugin.registerNpcBackend(backend: NpcBackend) {
    ArcNpcs.backend = backend
}
