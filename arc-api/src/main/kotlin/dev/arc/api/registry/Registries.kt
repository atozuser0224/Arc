@file:JvmName("Registries")

package dev.arc.api.registry

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.Registry

/**
 * Read-only access to the server's data-driven registries (biomes, enchantments, damage types, trims, ...)
 * via Paper's [RegistryAccess] - the supported way to look these up. (Mutating registries at runtime is
 * explicitly unsafe and intentionally not offered here.)
 *
 * ```kotlin
 * val biome = RegistryKey.BIOME[NamespacedKey.minecraft("plains")]
 * ```
 */

/** The [Registry] for a given [key]. */
public fun <T : Keyed> registryOf(key: RegistryKey<T>): Registry<T> =
    RegistryAccess.registryAccess().getRegistry(key)

/** Look up a single entry in this registry by [key]. */
public operator fun <T : Keyed> RegistryKey<T>.get(key: NamespacedKey): T? =
    RegistryAccess.registryAccess().getRegistry(this).get(key)
