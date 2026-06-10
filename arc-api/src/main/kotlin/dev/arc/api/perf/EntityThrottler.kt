@file:JvmName("EntityThrottler")

package dev.arc.api.perf

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.WeakHashMap

/**
 * Reduces entity AI activity under server load by toggling [LivingEntity.setAI] based on
 * [ServerLoad]. Automatically re-enables AI when load drops.
 *
 * ```kotlin
 * EntityThrottler.install(plugin) {
 *     minTps = 15.0          // start throttling below this TPS
 *     maxEntitiesPerChunk = 10
 * }
 * ```
 */
public object EntityThrottler {

    public class Config {
        public var minTps: Double = 15.0
        public var maxEntitiesPerChunk: Int = 10
        public var checkIntervalTicks: Long = 40L
    }

    private val throttled = WeakHashMap<Entity, Boolean>()

    public fun install(plugin: Plugin, block: Config.() -> Unit = {}) {
        val cfg = Config().apply(block)
        object : BukkitRunnable() {
            override fun run() {
                val shouldThrottle = ServerLoad.tps < cfg.minTps
                for (world in plugin.server.worlds) {
                    for (chunk in world.loadedChunks) {
                        val mobs = chunk.entities.filterIsInstance<Mob>()
                        mobs.forEachIndexed { i, mob ->
                            val disable = shouldThrottle && i >= cfg.maxEntitiesPerChunk
                            if (disable && throttled[mob] != true) {
                                mob.setAI(false)
                                throttled[mob] = true
                            } else if (!disable && throttled[mob] == true) {
                                mob.setAI(true)
                                throttled.remove(mob)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, cfg.checkIntervalTicks, cfg.checkIntervalTicks)
    }
}
