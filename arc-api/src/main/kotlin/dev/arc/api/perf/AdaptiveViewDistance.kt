@file:JvmName("AdaptiveViewDistance")

package dev.arc.api.perf

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Per-player dynamic view/sim distance that scales down under load and back up during recovery.
 * Considers both server MSPT AND player ping to avoid sending far chunks to high-latency clients.
 *
 * ```kotlin
 * AdaptiveViewDistance.install(plugin) {
 *     maxViewDistance = 10
 *     minViewDistance = 4
 *     highMsptThreshold = 45.0
 *     highPingMs = 200
 * }
 * ```
 */
public object AdaptiveViewDistance {

    public class Config {
        public var maxViewDistance: Int = 10
        public var minViewDistance: Int = 4
        public var maxSimDistance: Int = 6
        public var minSimDistance: Int = 3
        public var highMsptThreshold: Double = 45.0
        public var highPingMs: Int = 200
        public var checkIntervalTicks: Long = 60L
    }

    public fun install(plugin: Plugin, block: Config.() -> Unit = {}) {
        val cfg = Config().apply(block)
        object : BukkitRunnable() {
            override fun run() {
                val mspt = ServerLoad.mspt
                val serverOverloaded = mspt >= cfg.highMsptThreshold
                for (player in plugin.server.onlinePlayers) {
                    val pingOverloaded = player.ping > cfg.highPingMs
                    val targetView = when {
                        serverOverloaded || pingOverloaded -> cfg.minViewDistance
                        else -> cfg.maxViewDistance
                    }
                    val targetSim = when {
                        serverOverloaded || pingOverloaded -> cfg.minSimDistance
                        else -> cfg.maxSimDistance
                    }
                    if (player.viewDistance != targetView) player.viewDistance = targetView
                    if (player.simulationDistance != targetSim) player.simulationDistance = targetSim
                }
            }
        }.runTaskTimer(plugin, cfg.checkIntervalTicks, cfg.checkIntervalTicks)
    }
}
