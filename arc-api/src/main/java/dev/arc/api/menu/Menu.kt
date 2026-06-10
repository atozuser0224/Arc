package dev.arc.api.menu

import dev.arc.api.Arc
import dev.arc.api.control.ArcFeatures
import dev.arc.api.event.on
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.WeakHashMap

/**
 * A tiny chest-GUI DSL. Clicks inside the menu are cancelled automatically and
 * routed to the per-slot handler.
 *
 * ```
 * plugin.menu(3, Component.text("Shop")) {
 *     slot(13, item(Material.DIAMOND) { name(Component.text("Buy")) }) { e ->
 *         (e.whoClicked as Player).send("<green>Purchased!")
 *     }
 * }.openFor(player)
 * ```
 */
class Menu internal constructor(val inventory: Inventory) {

    @PublishedApi
    internal val handlers = HashMap<Int, (InventoryClickEvent) -> Unit>()

    /** Place [item] at [index] and run [onClick] when it is clicked. */
    fun slot(index: Int, item: ItemStack, onClick: (InventoryClickEvent) -> Unit = {}) {
        inventory.setItem(index, item)
        handlers[index] = onClick
    }

    /** Open this menu for [player]. */
    fun openFor(player: Player) {
        player.openInventory(inventory)
    }
}

/** Build a [rows]-row chest menu. Each row is 9 slots. */
fun Plugin.menu(rows: Int, title: Component, build: Menu.() -> Unit): Menu {
    val inventory = Bukkit.createInventory(null, rows * 9, title)
    val menu = Menu(inventory).apply(build)
    MenuRegistry.track(this, inventory, menu)
    return menu
}

/** Routes [InventoryClickEvent]s to the owning [Menu]. Listener installed once. */
private object MenuRegistry {

    private val menus = WeakHashMap<Inventory, Menu>()

    @Volatile
    private var installed = false

    fun track(plugin: Plugin, inventory: Inventory, menu: Menu) {
        ensureListener(plugin)
        synchronized(menus) { menus[inventory] = menu }
    }

    private fun ensureListener(plugin: Plugin) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            plugin.on<InventoryClickEvent> { event ->
                if (!Arc.features.isEnabled(ArcFeatures.MENUS)) return@on
                val menu = synchronized(menus) { menus[event.inventory] } ?: return@on
                event.isCancelled = true
                if (event.clickedInventory === event.inventory) {
                    menu.handlers[event.slot]?.invoke(event)
                }
            }
            installed = true
        }
    }
}
