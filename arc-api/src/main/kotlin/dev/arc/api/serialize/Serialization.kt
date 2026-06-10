@file:JvmName("Serialization")

package dev.arc.api.serialize

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.Base64

/**
 * Compact (de)serialization for the values plugins most often need to persist as plain text - locations and
 * item stacks - so they drop straight into a config string, database column or PDC entry.
 *
 * Item stacks use Paper's own NBT-backed [ItemStack.serializeAsBytes]/[ItemStack.deserializeBytes], so all
 * custom data (enchants, meta, components) round-trips losslessly; the bytes are Base64-wrapped for text use.
 */

/** Encode as `world;x;y;z;yaw;pitch`. */
public fun Location.encode(): String =
    listOf(world?.name ?: "", x, y, z, yaw, pitch).joinToString(separator = ";")

/** Decode an [encode]d location, or `null` if malformed or the world is not loaded. */
public fun String.decodeLocationOrNull(): Location? {
    val parts = split(";")
    if (parts.size != 6) return null
    val world = Bukkit.getWorld(parts[0]) ?: return null
    val x = parts[1].toDoubleOrNull() ?: return null
    val y = parts[2].toDoubleOrNull() ?: return null
    val z = parts[3].toDoubleOrNull() ?: return null
    val yaw = parts[4].toFloatOrNull() ?: return null
    val pitch = parts[5].toFloatOrNull() ?: return null
    return Location(world, x, y, z, yaw, pitch)
}

/** Encode this item stack (all NBT/components preserved) to a Base64 string. */
public fun ItemStack.encodeBase64(): String =
    Base64.getEncoder().encodeToString(serializeAsBytes())

/** Decode a [encodeBase64]'d item stack. Throws if the string is not valid item data. */
public fun String.decodeItemStack(): ItemStack =
    ItemStack.deserializeBytes(Base64.getDecoder().decode(this))

/** Decode a [encodeBase64]'d item stack, or `null` on any failure. */
public fun String.decodeItemStackOrNull(): ItemStack? =
    runCatching { decodeItemStack() }.getOrNull()
