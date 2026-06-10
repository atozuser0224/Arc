@file:JvmName("PaginatedMenus")

package dev.arc.api.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/** One clickable slot in a [PaginatedMenu]. */
public class MenuEntry(
    public val item: ItemStack,
    public val onClick: (InventoryClickEvent) -> Unit = {},
)

/**
 * A paginated chest GUI built on [Menu]: content fills all but the last row, which holds prev/next navigation.
 * Pages are (re)rendered on demand, so the entry list can be any size.
 *
 * ```kotlin
 * plugin.paginatedMenu("<dark_gray>Shop".mini(), entries = items.map { MenuEntry(it) { e -> buy(e) } })
 *     .open(player)
 * ```
 */
public class PaginatedMenu internal constructor(
    private val plugin: Plugin,
    private val title: Component,
    private val rows: Int,
    private val entries: List<MenuEntry>,
) {
    private val perPage = (rows - 1).coerceAtLeast(1) * 9

    /** Current zero-based page. */
    public var page: Int = 0

    /** Total number of pages. */
    public val pageCount: Int
        get() = if (entries.isEmpty()) 1 else (entries.size + perPage - 1) / perPage

    /** Render the current page and open it for [viewer]. */
    public fun open(viewer: HumanEntity) {
        val start = page * perPage
        val menu = plugin.menu(title, rows) {
            entries.drop(start).take(perPage).forEachIndexed { slot, entry ->
                item(slot, entry.item, entry.onClick)
            }
            val navRow = (rows - 1).coerceAtLeast(1) * 9
            if (page > 0) {
                item(navRow, ItemStack(Material.ARROW)) {
                    page--
                    open(viewer)
                }
            }
            if (start + perPage < entries.size) {
                item(navRow + 8, ItemStack(Material.ARROW)) {
                    page++
                    open(viewer)
                }
            }
        }
        menu.open(viewer)
    }
}

/** Build a [PaginatedMenu] (1–6 [rows]) owned by this plugin. */
public fun Plugin.paginatedMenu(title: Component, rows: Int = 6, entries: List<MenuEntry>): PaginatedMenu =
    PaginatedMenu(this, title, rows.coerceIn(2, 6), entries)
