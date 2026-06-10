package dev.arc.api.scheduling

import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * Optional server-specific scheduler for ownership models that Paper's
 * fallback RegionScheduler cannot represent, such as Leaf parallel world ticks.
 */
interface ArcThreadScheduler {

    /** Whether this scheduler should replace Paper's fallback schedulers now. */
    fun isActive(): Boolean

    /** Submit work to the thread that owns [world]. */
    fun executeWorld(plugin: Plugin, world: World, task: Runnable): Boolean

    /**
     * Submit work to the thread that owns [entity]. Implementations may reject
     * when the entity changes worlds before execution.
     */
    fun executeEntity(plugin: Plugin, entity: Entity, task: Runnable): Boolean =
        executeWorld(plugin, entity.world, task)
}
