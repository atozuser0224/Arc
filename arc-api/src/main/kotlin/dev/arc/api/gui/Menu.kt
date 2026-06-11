@file:JvmName("Menus")

package dev.arc.api.gui

import dev.arc.api.event.MenuCloseEvent
import dev.arc.api.event.MenuOpenEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * A click-handled chest inventory. Build via [Plugin.menu], not directly.
 *
 * ```kotlin
 * val gui = plugin.menu("§eShop".component(), rows = 3) {
 *     item(0, ItemStack(Material.DIAMOND)) { e -> buy(e.whoClicked) }
 * }
 * gui.open(player)
 * ```
 */
public class Menu internal constructor(
    private val plugin: Plugin,
    private val inventory: Inventory,
) {
    private val handlers = HashMap<Int, (InventoryClickEvent) -> Unit>()
    private var closeListener: ((HumanEntity) -> Unit)? = null
    private var listener: Listener? = null

    /** Set the [ItemStack] and optional click handler for [slot]. */
    public fun item(slot: Int, item: ItemStack, onClick: (InventoryClickEvent) -> Unit = {}) {
        inventory.setItem(slot, item)
        handlers[slot] = onClick
    }

    /** Register a callback invoked when any viewer closes this menu. */
    public fun onClose(action: (HumanEntity) -> Unit) {
        closeListener = action
    }

    /** Open the menu for [viewer], firing [MenuOpenEvent]. */
    public fun open(viewer: HumanEntity) {
        val event = MenuOpenEvent(viewer, this)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return
        ensureListener()
        viewer.openInventory(inventory)
    }

    internal fun handleClick(event: InventoryClickEvent) {
        event.isCancelled = true
        handlers[event.rawSlot]?.invoke(event)
    }

    internal fun handleClose(viewer: HumanEntity) {
        closeListener?.invoke(viewer)
        Bukkit.getPluginManager().callEvent(MenuCloseEvent(viewer, this))
    }

    internal fun inventory(): Inventory = inventory

    private fun ensureListener() {
        if (listener != null) return
        val menu = this
        val l = object : Listener {
            @EventHandler
            fun onClick(event: InventoryClickEvent) {
                if (event.inventory !== menu.inventory()) return
                menu.handleClick(event)
            }

            @EventHandler
            fun onClose(event: InventoryCloseEvent) {
                if (event.inventory !== menu.inventory()) return
                menu.handleClose(event.player)
                // unregister when no viewers remain
                if (event.inventory.viewers.isEmpty()) {
                    HandlerList.unregisterAll(this)
                    menu.listener = null
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(l, plugin)
        listener = l
    }
}

/** DSL scope passed to the [Plugin.menu] builder block. */
public class MenuBuilder internal constructor(private val menu: Menu) {
    public fun item(slot: Int, item: ItemStack, onClick: (InventoryClickEvent) -> Unit = {}) =
        menu.item(slot, item, onClick)

    public fun onClose(action: (HumanEntity) -> Unit) = menu.onClose(action)
}

/** Build a chest [Menu] (1–6 [rows]) owned by this plugin. */
public fun Plugin.menu(title: Component, rows: Int = 3, block: MenuBuilder.() -> Unit = {}): Menu {
    val inv = Bukkit.createInventory(null, rows.coerceIn(1, 6) * 9, title)
    val menu = Menu(this, inv)
    MenuBuilder(menu).block()
    return menu
}
