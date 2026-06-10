@file:JvmName("LootTables")

package dev.arc.api.world

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable

/**
 * Vanilla loot-table access via Bukkit API (the server's loot manager, exposed safely).
 *
 * ```kotlin
 * chestState.applyLootTable(NamespacedKey.minecraft("chests/simple_dungeon"))
 * ```
 */

/** Look up a registered loot table by [key]. */
public fun lootTable(key: NamespacedKey): LootTable? = Bukkit.getLootTable(key)

/** Assign the loot table identified by [key] to this lootable (chest, entity, ...). */
public fun Lootable.applyLootTable(key: NamespacedKey) {
    lootTable = Bukkit.getLootTable(key)
}
