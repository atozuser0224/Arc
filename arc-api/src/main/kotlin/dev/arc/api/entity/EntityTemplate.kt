@file:JvmName("EntityTemplates")

package dev.arc.api.entity

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import java.util.concurrent.ConcurrentHashMap

/**
 * Named, reusable entity configuration snapshots.
 *
 * Define a template once with a builder lambda, then spawn as many instances as you like
 * without repeating setup code.
 *
 * ```kotlin
 * val BOSS_ZOMBIE = EntityTemplate.define("boss_zombie", EntityType.ZOMBIE) {
 *     customName("§cBoss Zombie")
 *     isCustomNameVisible = true
 *     health = 100.0
 *     equipment.helmet = ItemStack(Material.DIAMOND_HELMET)
 * }
 *
 * // Spawn anywhere later
 * val zombie = BOSS_ZOMBIE.spawn(location)
 * ```
 */
object EntityTemplateRegistry {

    private val templates = ConcurrentHashMap<String, EntityTemplate<*>>()

    /** Define a template. Re-registering the same [name] overwrites the previous definition. */
    fun <T : Entity> define(name: String, type: EntityType, configure: T.() -> Unit): EntityTemplate<T> {
        val template = EntityTemplate(name, type, configure)
        templates[name] = template
        return template
    }

    /** Look up a template by name. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Entity> get(name: String): EntityTemplate<T>? = templates[name] as? EntityTemplate<T>

    /** All registered templates. */
    fun all(): Collection<EntityTemplate<*>> = templates.values

    /** Remove a template definition. */
    fun unregister(name: String) { templates.remove(name) }
}

/**
 * A named entity template that can spawn configured instances.
 *
 * @param T the Bukkit entity interface (e.g., [org.bukkit.entity.Zombie])
 */
class EntityTemplate<T : Entity> internal constructor(
    val name: String,
    val type: EntityType,
    private val configure: T.() -> Unit,
) {
    /**
     * Spawn a new entity at [location], applying this template's configuration.
     * The entity is spawned with [org.bukkit.World.spawnEntity] and then configured.
     */
    @Suppress("UNCHECKED_CAST")
    fun spawn(location: Location): T {
        val entity = location.world.spawnEntity(location, type) as T
        entity.configure()
        return entity
    }

    /**
     * Apply this template's configuration to an already-existing entity.
     * Useful for re-configuring entities without respawning.
     */
    fun applyTo(entity: T) = entity.configure()
}

/** Shorthand: spawn this template at [location]. */
fun <T : Entity> EntityTemplate<T>.spawnAt(location: Location): T = spawn(location)
