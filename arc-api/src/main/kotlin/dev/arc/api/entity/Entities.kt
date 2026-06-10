@file:JvmName("Entities")

package dev.arc.api.entity

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * Type-safe spawning and nearby-entity queries.
 *
 * `World.spawn(loc, Class)` + a manual cast, and `getNearbyEntities` returning a wildcard collection, are the
 * usual friction points. The reified helpers below fold the class token and filtering into the call site.
 *
 * ```kotlin
 * val zombie = world.spawnEntity<Zombie>(loc) { it.isBaby = true }
 * val mobs = loc.nearbyOfType<Monster>(radius = 10.0)
 * ```
 */

/** Spawn an entity of type [T] at [location], optionally configuring it after spawn. */
public inline fun <reified T : Entity> World.spawnEntity(location: Location, configure: (T) -> Unit = {}): T {
    val entity = spawn(location, T::class.java)
    configure(entity)
    return entity
}

/** Entities of any type within a cubic [radius] of this location (excludes nothing). */
public fun Location.nearbyEntities(radius: Double): Collection<Entity> {
    val world = world ?: return emptyList()
    return world.getNearbyEntities(this, radius, radius, radius)
}

/** Entities of type [T] within a cubic [radius] of this location. */
public inline fun <reified T : Entity> Location.nearbyOfType(radius: Double): List<T> {
    val world = world ?: return emptyList()
    return world.getNearbyEntities(this, radius, radius, radius).filterIsInstance<T>()
}

/** The nearest entity of type [T] within [radius], or `null` if none. */
public inline fun <reified T : Entity> Location.nearestOfType(radius: Double): T? =
    nearbyOfType<T>(radius).minByOrNull { it.location.distanceSquared(this) }

/** Players within a cubic [radius] of this location. */
public fun Location.nearbyPlayers(radius: Double): List<Player> = nearbyOfType(radius)

/** Heal a living entity to its maximum health. */
@Suppress("DEPRECATION")
public fun LivingEntity.healFully() {
    health = maxHealth
}
