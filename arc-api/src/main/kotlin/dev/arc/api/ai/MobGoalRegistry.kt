@file:JvmName("MobGoalRegistry")

package dev.arc.api.ai

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mob
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.plugin.Plugin
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Auto-injects custom [Goal]s into mobs when they spawn, filtered by entity class.
 *
 * Instead of handling [CreatureSpawnEvent] yourself for every goal, register once and
 * the registry handles all spawns for the lifetime of your plugin.
 *
 * ```kotlin
 * ArcMobGoalRegistry.register(plugin, Zombie::class.java, priority = 3) { mob ->
 *     object : Goal<Zombie> {
 *         override fun getKey() = GoalKey.of(Zombie::class.java, NamespacedKey(plugin, "flee_sunlight"))
 *         override fun getTypes() = setOf(GoalType.MOVE)
 *         override fun shouldActivate() = mob.world.isDayTime && mob.fireTicks <= 0
 *         override fun tick() { mob.moveTo(mob.location.add(0.0, -1.0, 0.0), 1.0) }
 *     }
 * }
 * ```
 */
object ArcMobGoalRegistry : Listener {

    private data class Entry<T : Mob>(
        val plugin: Plugin,
        val mobClass: Class<T>,
        val priority: Int,
        val factory: (T) -> Goal<T>,
    )

    private val entries = CopyOnWriteArrayList<Entry<*>>()
    private var registered = false

    /**
     * Register a goal factory for mobs of type [T].
     *
     * [priority] follows Paper's goal priority convention — lower numbers run first.
     * [factory] receives the spawned mob and must return a configured [Goal].
     */
    fun <T : Mob> register(plugin: Plugin, mobClass: Class<T>, priority: Int = 5, factory: (T) -> Goal<T>) {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin)
            registered = true
        }
        entries.add(Entry(plugin, mobClass, priority, factory))
    }

    /** Remove all goals registered by [plugin]. */
    fun unregister(plugin: Plugin) {
        entries.removeIf { it.plugin == plugin }
    }

    @EventHandler
    @Suppress("UNCHECKED_CAST")
    fun onSpawn(event: CreatureSpawnEvent) {
        val mob = event.entity as? Mob ?: return
        val goals = Bukkit.getMobGoals()
        for (entry in entries) {
            if (entry.mobClass.isInstance(mob)) {
                val typedMob = entry.mobClass.cast(mob)
                val typedEntry = entry as Entry<Mob>
                @Suppress("UNCHECKED_CAST")
                val goal = (entry.factory as (Mob) -> Goal<Mob>)(typedMob)
                goals.addGoal(typedMob, entry.priority, goal)
            }
        }
    }
}

/** Inline DSL: register a goal for mobs of reified type [T]. */
inline fun <reified T : Mob> Plugin.mobGoal(priority: Int = 5, noinline factory: (T) -> Goal<T>) {
    ArcMobGoalRegistry.register(this, T::class.java, priority, factory)
}
