@file:JvmName("BorderEvents")

package dev.arc.api.world

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Track when players enter or leave a virtual cuboid zone (not the world border).
 *
 * ```kotlin
 * ZoneTracker.create(plugin, from, to)
 *     .onEnter { player -> player.msg("<green>Entered zone!") }
 *     .onLeave { player -> player.msg("<red>Left zone!") }
 *     .start()
 * ```
 */
public class ZoneTracker private constructor(
    private val plugin: Plugin,
    private val from: Location,
    private val to: Location,
) {
    private var onEnter: ((Player) -> Unit)? = null
    private var onLeave: ((Player) -> Unit)? = null
    private val inside = mutableSetOf<Player>()

    public fun onEnter(action: (Player) -> Unit): ZoneTracker { onEnter = action; return this }
    public fun onLeave(action: (Player) -> Unit): ZoneTracker { onLeave = action; return this }

    public fun start(): ZoneTracker {
        object : BukkitRunnable() {
            override fun run() {
                val world = from.world ?: return
                val x1 = minOf(from.x, to.x); val x2 = maxOf(from.x, to.x)
                val y1 = minOf(from.y, to.y); val y2 = maxOf(from.y, to.y)
                val z1 = minOf(from.z, to.z); val z2 = maxOf(from.z, to.z)

                for (player in world.players) {
                    val loc = player.location
                    val inZone = loc.x in x1..x2 && loc.y in y1..y2 && loc.z in z1..z2
                    if (inZone && inside.add(player)) onEnter?.invoke(player)
                    else if (!inZone && inside.remove(player)) onLeave?.invoke(player)
                }
                // Clean up offline players
                inside.removeIf { !it.isOnline }
            }
        }.runTaskTimer(plugin, 5L, 5L)
        return this
    }

    public companion object {
        public fun create(plugin: Plugin, from: Location, to: Location): ZoneTracker =
            ZoneTracker(plugin, from, to)
    }
}
