package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * Shows network-wide player counts in tab list header/footer.
 * Updates every 5 seconds (100 ticks). Lightweight — no NMS required.
 *
 * Enable: arc-network.yml -> tab-list.enabled: true
 * Full fake-player tab injection (showing individual players from other servers)
 * requires NMS packet injection and is a separate, heavier feature.
 */
object ArcTabListSync {

    private var taskId: Int = -1

    fun start(plugin: Plugin) {
        stop()
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (!ArcRelayClient.connected) return@Runnable
            val servers = ArcServerRegistry.allServers()
            val networkTotal = servers.sumOf { it.players }
            val localPlayers = Bukkit.getOnlinePlayers().size

            val header = buildString {
                append("§b§lArc Network §r§8— §a$networkTotal §7players online\n")
                if (servers.size > 1) {
                    append(servers.joinToString("  §8|  ") { s ->
                        val color = when {
                            s.id == ArcNetworkConfig.serverId -> "§e"
                            s.players >= s.maxPlayers -> "§c"
                            else -> "§a"
                        }
                        "$color${s.id} §7${s.players}§8/§7${s.maxPlayers}"
                    })
                }
            }
            val footer = "§7This server: §f$localPlayers §7online  •  TPS §f${"%.1f".format(dev.arc.api.perf.ServerLoad.tps(1))}"

            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.getOnlinePlayers().forEach { it.setPlayerListHeaderFooter(header, footer) }
            })
        }, 20L, 100L).taskId
    }

    fun stop() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1 }
    }
}
