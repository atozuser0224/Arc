@file:JvmName("ItemTransactions")

package dev.arc.api.player

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * NMS-level item movement tracker. Logs every inventory transaction for a player and
 * provides rollback to the last known-safe snapshot.
 *
 * Integrate with [org.dreeam.leaf.event.ContainerTransactionEvent] (Leaf fork) for full
 * NMS coverage, or fall back to Bukkit InventoryClickEvent for vanilla Paper servers.
 *
 * ```kotlin
 * val logger = plugin.itemTransactionLogger()
 *
 * // Start tracking a player (e.g. on entering a raid zone)
 * logger.startTracking(player)
 *
 * // Query the log
 * logger.getLog(player).takeLast(10).forEach { println(it) }
 *
 * // Roll back to snapshot taken when tracking started (or latest checkpoint)
 * logger.rollback(player)
 *
 * // Stop tracking and clear log
 * logger.stopTracking(player)
 * ```
 */
class ItemTransactionLogger(private val plugin: Plugin) : Listener {

    data class TransactionEntry(
        val timestamp: Instant,
        val playerId: UUID,
        val playerName: String,
        val type: TransactionType,
        val slotFrom: Int,
        val slotTo: Int,
        val item: ItemStack,
        val containerTitle: String,
    ) {
        override fun toString() =
            "[$timestamp] $playerName $type slot$slotFrom→slot$slotTo ${item.type}x${item.amount} ($containerTitle)"
    }

    enum class TransactionType { CLICK, DRAG, HOPPER }

    private data class PlayerState(
        val log: CopyOnWriteArrayList<TransactionEntry> = CopyOnWriteArrayList(),
        val checkpoints: CopyOnWriteArrayList<Map<Int, ItemStack>> = CopyOnWriteArrayList(),
    )

    private val tracked = ConcurrentHashMap<UUID, PlayerState>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // ── Tracking control ───────────────────────────────────────────────────────

    /** Begin tracking [player]. Takes an initial inventory snapshot as rollback point 0. */
    fun startTracking(player: Player) {
        val state = PlayerState()
        state.checkpoints.add(snapshotInventory(player))
        tracked[player.uniqueId] = state
    }

    /** Stop tracking [player] and discard their log. */
    fun stopTracking(player: Player) {
        tracked.remove(player.uniqueId)
    }

    /** Take a named checkpoint for [player]. Rollback will restore to this state. */
    fun checkpoint(player: Player) {
        tracked[player.uniqueId]?.checkpoints?.add(snapshotInventory(player))
    }

    /** Whether [player] is currently being tracked. */
    fun isTracking(player: Player): Boolean = tracked.containsKey(player.uniqueId)

    // ── Log access ─────────────────────────────────────────────────────────────

    /** Full transaction log for [player] (empty if not tracked). */
    fun getLog(player: Player): List<TransactionEntry> =
        tracked[player.uniqueId]?.log?.toList() ?: emptyList()

    /** All currently tracked players. */
    fun trackedPlayers(): Set<UUID> = tracked.keys.toSet()

    // ── Rollback ───────────────────────────────────────────────────────────────

    /**
     * Restore [player]'s inventory to the latest checkpoint.
     * Returns `true` if a checkpoint was available and applied.
     */
    fun rollback(player: Player): Boolean {
        val state = tracked[player.uniqueId] ?: return false
        val snapshot = state.checkpoints.lastOrNull() ?: return false
        applySnapshot(player, snapshot)
        state.log.add(
            TransactionEntry(
                Instant.now(), player.uniqueId, player.name,
                TransactionType.CLICK, -1, -1,
                org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR),
                "ROLLBACK"
            )
        )
        return true
    }

    /**
     * Restore [player] to a specific checkpoint index (0 = initial snapshot).
     */
    fun rollbackTo(player: Player, checkpointIndex: Int): Boolean {
        val state = tracked[player.uniqueId] ?: return false
        val snapshot = state.checkpoints.getOrNull(checkpointIndex) ?: return false
        applySnapshot(player, snapshot)
        return true
    }

    // ── Event listeners ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = tracked[player.uniqueId] ?: return
        val item = event.currentItem ?: event.cursor ?: return
        if (item.type == org.bukkit.Material.AIR) return
        state.log.add(
            TransactionEntry(
                Instant.now(), player.uniqueId, player.name,
                TransactionType.CLICK,
                event.rawSlot, event.slot,
                item.clone(),
                event.view.title,
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = tracked[player.uniqueId] ?: return
        val item = event.oldCursor
        if (item.type == org.bukkit.Material.AIR) return
        state.log.add(
            TransactionEntry(
                Instant.now(), player.uniqueId, player.name,
                TransactionType.DRAG,
                -1, event.rawSlots.firstOrNull() ?: -1,
                item.clone(),
                event.view.title,
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        val dest = event.destination
        val holder = dest.holder as? Player ?: return
        val state = tracked[holder.uniqueId] ?: return
        state.log.add(
            TransactionEntry(
                Instant.now(), holder.uniqueId, holder.name,
                TransactionType.HOPPER,
                -1, -1,
                event.item.clone(),
                "hopper→${dest.holder?.javaClass?.simpleName}",
            )
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun snapshotInventory(player: Player): Map<Int, ItemStack> =
        player.inventory.contents
            .mapIndexedNotNull { i, item ->
                if (item != null && item.type != org.bukkit.Material.AIR) i to item.clone() else null
            }
            .toMap()

    private fun applySnapshot(player: Player, snapshot: Map<Int, ItemStack>) {
        player.inventory.clear()
        snapshot.forEach { (slot, item) -> player.inventory.setItem(slot, item.clone()) }
    }
}

/** Create an [ItemTransactionLogger] registered to this plugin. */
fun Plugin.itemTransactionLogger(): ItemTransactionLogger = ItemTransactionLogger(this)
