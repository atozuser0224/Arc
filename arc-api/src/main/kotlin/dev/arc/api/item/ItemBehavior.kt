@file:JvmName("ItemBehaviors")

package dev.arc.api.item

import dev.arc.api.event.listen
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

private val BEHAVIOR_KEY_NS = "arc"
private val BEHAVIOR_PDC_KEY get() = NamespacedKey(BEHAVIOR_KEY_NS, "item_behavior")

private val registry = ConcurrentHashMap<String, ItemBehaviorDefinition>()
private var listenersInstalled = false

// ---------------------------------------------------------------------------

data class ItemInteractEvent(
    val player: Player,
    val item: ItemStack,
    val action: Action,
    val raw: PlayerInteractEvent,
)

data class ItemEquipEvent(val player: Player, val item: ItemStack, val slot: EquipmentSlot)
data class ItemUnequipEvent(val player: Player, val item: ItemStack, val slot: EquipmentSlot)
data class ItemDropEvent(val player: Player, val item: ItemStack, val raw: PlayerDropItemEvent)

class ItemBehaviorBuilder {
    internal var onRightClick: ((ItemInteractEvent) -> Unit)? = null
    internal var onLeftClick:  ((ItemInteractEvent) -> Unit)? = null
    internal var onEquip:      ((ItemEquipEvent)    -> Unit)? = null
    internal var onUnequip:    ((ItemUnequipEvent)  -> Unit)? = null
    internal var onDrop:       ((ItemDropEvent)     -> Unit)? = null

    fun onRightClick(block: (ItemInteractEvent) -> Unit) { onRightClick = block }
    fun onLeftClick (block: (ItemInteractEvent) -> Unit) { onLeftClick  = block }
    fun onEquip     (block: (ItemEquipEvent)    -> Unit) { onEquip      = block }
    fun onUnequip   (block: (ItemUnequipEvent)  -> Unit) { onUnequip    = block }
    fun onDrop      (block: (ItemDropEvent)     -> Unit) { onDrop       = block }
}

data class ItemBehaviorDefinition(
    val key: String,
    val onRightClick: ((ItemInteractEvent) -> Unit)?,
    val onLeftClick:  ((ItemInteractEvent) -> Unit)?,
    val onEquip:      ((ItemEquipEvent)    -> Unit)?,
    val onUnequip:    ((ItemUnequipEvent)  -> Unit)?,
    val onDrop:       ((ItemDropEvent)     -> Unit)?,
)

// ---------------------------------------------------------------------------

/**
 * Define behavior for items tagged with [key].
 * Handlers fire automatically whenever any player interacts with such an item.
 *
 * ```kotlin
 * val WAND = NamespacedKey(plugin, "magic_wand")
 *
 * plugin.defineItem(WAND) {
 *     onRightClick { e -> castSpell(e.player) }
 *     onDrop       { e -> e.raw.isCancelled = true }
 * }
 *
 * val wandItem = item(Material.BLAZE_ROD) { name("§dMagic Wand".mini()) }
 *                  .withBehavior(WAND, plugin)
 * ```
 */
public fun Plugin.defineItem(key: NamespacedKey, block: ItemBehaviorBuilder.() -> Unit) {
    val b = ItemBehaviorBuilder().apply(block)
    registry[key.toString()] = ItemBehaviorDefinition(
        key.toString(), b.onRightClick, b.onLeftClick, b.onEquip, b.onUnequip, b.onDrop,
    )
    ensureListeners(this)
}

/** Attach this behavior [key] to the item via PDC. Returns a defensive copy. */
public fun ItemStack.withBehavior(key: NamespacedKey, plugin: Plugin): ItemStack {
    val copy = clone()
    val meta = copy.itemMeta ?: return copy
    meta.persistentDataContainer.set(BEHAVIOR_PDC_KEY, PersistentDataType.STRING, key.toString())
    copy.itemMeta = meta
    return copy
}

/** Return the behavior key stored in this item's PDC, or null. */
public val ItemStack.behaviorKey: String?
    get() = itemMeta?.persistentDataContainer?.get(BEHAVIOR_PDC_KEY, PersistentDataType.STRING)

/** Return the registered [ItemBehaviorDefinition] for this item, or null. */
public val ItemStack.behavior: ItemBehaviorDefinition?
    get() = behaviorKey?.let { registry[it] }

// ---------------------------------------------------------------------------

private fun ensureListeners(plugin: Plugin) {
    if (listenersInstalled) return
    listenersInstalled = true

    plugin.listen<PlayerInteractEvent> { e ->
        val item = e.item ?: return@listen
        val def  = item.behavior ?: return@listen
        val wrapped = ItemInteractEvent(e.player, item, e.action, e)
        when {
            e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK ->
                def.onRightClick?.invoke(wrapped)
            e.action == Action.LEFT_CLICK_AIR  || e.action == Action.LEFT_CLICK_BLOCK  ->
                def.onLeftClick?.invoke(wrapped)
        }
    }

    plugin.listen<PlayerItemHeldEvent> { e ->
        val inv = e.player.inventory
        val prev = inv.getItem(e.previousSlot)
        val next = inv.getItem(e.newSlot)
        prev?.behavior?.onUnequip?.invoke(ItemUnequipEvent(e.player, prev, EquipmentSlot.HAND))
        next?.behavior?.onEquip  ?.invoke(ItemEquipEvent  (e.player, next, EquipmentSlot.HAND))
    }

    plugin.listen<PlayerSwapHandItemsEvent> { e ->
        e.mainHandItem?.behavior?.onEquip  ?.invoke(ItemEquipEvent  (e.player, e.mainHandItem!!, EquipmentSlot.HAND))
        e.offHandItem ?.behavior?.onUnequip?.invoke(ItemUnequipEvent(e.player, e.offHandItem!!, EquipmentSlot.OFF_HAND))
    }

    plugin.listen<PlayerDropItemEvent> { e ->
        val item = e.itemDrop.itemStack
        item.behavior?.onDrop?.invoke(ItemDropEvent(e.player, item, e))
    }
}
