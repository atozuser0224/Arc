@file:JvmName("Services")

package dev.arc.api.service

import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight cross-plugin service registry.
 * Services are registered under their interface/class type and retrieved globally.
 *
 * ```kotlin
 * // Provider plugin onEnable:
 * plugin.provideService<Economy>(MyEconomyImpl())
 *
 * // Consumer plugin (any time after provider enables):
 * val eco = plugin.service<Economy>() ?: error("Economy not available")
 * eco.deposit(player, 100.0)
 * ```
 *
 * Unlike Bukkit's ServicesManager, this API is type-safe and requires no cast.
 */
@PublishedApi internal val registry = ConcurrentHashMap<Class<*>, Any>()

/** Register [implementation] as the provider for service type [T]. */
public inline fun <reified T : Any> Plugin.provideService(implementation: T): Unit =
    provideService(T::class.java, implementation)

/** Register [implementation] as the provider for service type [type]. */
public fun <T : Any> Plugin.provideService(type: Class<T>, implementation: T) {
    registry[type] = implementation
}

/** Retrieve the registered service of type [T], or null if none is registered. */
public inline fun <reified T : Any> Plugin.service(): T? = service(T::class.java)

/** Retrieve the registered service of type [type], or null if none is registered. */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> Plugin.service(type: Class<T>): T? = registry[type] as? T

/** Retrieve the registered service of type [T] — throws if not registered. */
public inline fun <reified T : Any> Plugin.requireService(): T =
    service<T>() ?: error("Service ${T::class.simpleName} is not registered")

/** Remove the registered service of type [T]. */
public inline fun <reified T : Any> Plugin.revokeService(): Unit =
    revokeService(T::class.java)

/** Remove the registered service of type [type]. */
public fun <T : Any> Plugin.revokeService(type: Class<T>) {
    registry.remove(type)
}

/** Returns true if a service of type [T] is currently registered. */
public inline fun <reified T : Any> Plugin.hasService(): Boolean = registry.containsKey(T::class.java)
