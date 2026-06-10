@file:JvmName("Structures")

package dev.arc.api.world

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.structure.Structure

/**
 * Saved-structure access/placement via Bukkit's structure manager.
 *
 * ```kotlin
 * structure(NamespacedKey.minecraft("igloo/top"))?.place(location, includeEntities = true)
 * ```
 */

/** Look up a registered/loaded structure by [key]. */
public fun structure(key: NamespacedKey): Structure? = Bukkit.getStructureManager().getStructure(key)

/** Place this structure at [location] with default (no rotation/mirror) transform. */
public fun Structure.place(location: Location, includeEntities: Boolean = true, integrity: Float = 1.0f) {
    place(
        location,
        includeEntities,
        org.bukkit.block.structure.StructureRotation.NONE,
        org.bukkit.block.structure.Mirror.NONE,
        0,
        integrity,
        java.util.Random(),
    )
}
