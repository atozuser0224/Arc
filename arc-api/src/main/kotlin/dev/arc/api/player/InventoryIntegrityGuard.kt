@file:JvmName("InventoryIntegrityGuards")

package dev.arc.api.player

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mathematical item-count integrity guard. Before a container opens, records the
 * total item count in the player's inventory. After it closes, verifies the count
 * has not inflated. Any increase triggers a rollback and calls the violation handler.
 *
 * Pairs naturally with [ItemTransactionLogger] for detailed audit logs and
 * [org.dreeam.leaf.event.ContainerTransactionEvent] for NMS-level per-click checks.
 *
 * ```kotlin
 * plugin.inventoryIntegrityGuard {
 *     onViolation { player, before, after, delta ->
 *         player.sendMessage("§cDuplicate detected (+$delta items) — inventory rolled back")
 *         plugin.logger.warning("${player.name} had +$delta items after container close")
 *     }
 * }
 * ```
 */
class InventoryIntegrityGuard(private val plugin: Plugin) : Listener {

    data class IntegrityViolation(
        val player: Player,
        val snapshot: Map<Int, ItemStack>,
        val beforeTotal: Int,
        val afterTotal: Int,
        val delta: Int,
    )

    private var violationHandler: ((IntegrityViolation) -> Unit)? = null

    /** If true, the player's inventory is automatically rolled back on violation. */
    var autoRollback: Boolean = true

    /** Minimum item count increase to consider a violation. Default 1. */
    var minDelta: Int = 1

    // snapshot per viewer: Map<slot, copy of ItemStack>
    private val snapshots = ConcurrentHashMap<UUID, Pair<Map<Int, ItemStack>, Int>>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Register a violation handler. */
    fun onViolation(handler: (IntegrityViolation) -> Unit) {
        violationHandler = handler
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val snap = snapshotInventory(player)
        snapshots[player.uniqueId] = snap to countItems(snap)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val (snap, before) = snapshots.remove(player.uniqueId) ?: return

        val currentSnap = snapshotInventory(player)
        val after = countItems(currentSnap)
        val delta = after - before

        if (delta >= minDelta) {
            val violation = IntegrityViolation(player, snap, before, after, delta)
            if (autoRollback) applySnapshot(player, snap)
            violationHandler?.invoke(violation)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun snapshotInventory(player: Player): Map<Int, ItemStack> =
        player.inventory.contents
            .mapIndexedNotNull { i, item ->
                if (item != null && !item.type.isAir) i to item.clone() else null
            }
            .toMap()

    private fun countItems(snap: Map<Int, ItemStack>): Int =
        snap.values.sumOf { it.amount }

    private fun applySnapshot(player: Player, snapshot: Map<Int, ItemStack>) {
        player.inventory.clear()
        snapshot.forEach { (slot, item) -> player.inventory.setItem(slot, item.clone()) }
    }
}

/** Create and register an [InventoryIntegrityGuard] for this plugin. */
fun Plugin.inventoryIntegrityGuard(configure: InventoryIntegrityGuard.() -> Unit): InventoryIntegrityGuard =
    InventoryIntegrityGuard(this).apply(configure)
