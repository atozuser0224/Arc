package dev.arc.test.assert

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.Metadatable
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType

/**
 * Assert that [key] exists in this container with the given [type] and [value].
 *
 * ```kotlin
 * player.mock.assertPdc(plugin, "coins", PersistentDataType.INTEGER, 100)
 * item.assertPdc(plugin, "rarity", PersistentDataType.STRING, "EPIC")
 * ```
 */
fun <T : Any, Z : Any> PersistentDataHolder.assertPdc(
    plugin: org.bukkit.plugin.Plugin,
    key: String,
    type: PersistentDataType<T, Z>,
    expected: Z,
) {
    val nsKey = NamespacedKey(plugin, key)
    val pdc = persistentDataContainer
    check(pdc.has(nsKey, type)) {
        "Expected PDC key '${plugin.name}:$key' (${type}) but key is absent"
    }
    val actual = pdc.get(nsKey, type)
    check(actual == expected) {
        "PDC '${plugin.name}:$key': expected $expected but was $actual"
    }
}

/**
 * Assert that [key] is absent from this container.
 */
fun <T : Any, Z : Any> PersistentDataHolder.assertNoPdc(
    plugin: org.bukkit.plugin.Plugin,
    key: String,
    type: PersistentDataType<T, Z>,
) {
    val nsKey = NamespacedKey(plugin, key)
    check(!persistentDataContainer.has(nsKey, type)) {
        "Expected PDC key '${plugin.name}:$key' to be absent but it exists"
    }
}

/**
 * Read a PDC value by string key, returning null if absent.
 */
fun <T : Any, Z : Any> PersistentDataHolder.pdcOrNull(
    plugin: org.bukkit.plugin.Plugin,
    key: String,
    type: PersistentDataType<T, Z>,
): Z? = persistentDataContainer.get(NamespacedKey(plugin, key), type)

/**
 * Assert that this ItemStack has a PDC key (convenience for nullable ItemStack).
 */
fun <T : Any, Z : Any> ItemStack.assertPdc(
    plugin: org.bukkit.plugin.Plugin,
    key: String,
    type: PersistentDataType<T, Z>,
    expected: Z,
) {
    val pdc = itemMeta?.persistentDataContainer
        ?: error("ItemStack has no ItemMeta — cannot read PDC")
    val nsKey = NamespacedKey(plugin, key)
    check(pdc.has(nsKey, type)) {
        "Expected item PDC key '${plugin.name}:$key' (${type}) but key is absent"
    }
    val actual = pdc.get(nsKey, type)
    check(actual == expected) {
        "Item PDC '${plugin.name}:$key': expected $expected but was $actual"
    }
}

/**
 * Assert that this ItemStack does NOT have the given PDC key.
 */
fun <T : Any, Z : Any> ItemStack.assertNoPdc(
    plugin: org.bukkit.plugin.Plugin,
    key: String,
    type: PersistentDataType<T, Z>,
) {
    val pdc = itemMeta?.persistentDataContainer ?: return
    check(!pdc.has(NamespacedKey(plugin, key), type)) {
        "Expected item PDC key '${plugin.name}:$key' to be absent but it exists"
    }
}
