package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Network-wide broadcast and maintenance commands.
 */
object ArcNetworkBroadcast {

    fun networkBroadcast(sender: CommandSender, message: String) {
        ArcNetworkAudit.log("broadcast.network", mapOf("message" to message.take(100), "actor" to sender.name))
        val payload = buildBroadcastPayload(sender.name, message)
        ArcServerRegistry.onlineServerIds()
            .filter { it != ArcNetworkConfig.serverId }
            .forEach { ArcRelayClient.lpush("arc:inbox:broadcast:$it", payload) }
        Bukkit.broadcastMessage("[§bNetwork§r] $message")
    }

    fun groupBroadcast(sender: CommandSender, group: String, message: String) {
        ArcNetworkAudit.log("broadcast.group", mapOf("group" to group, "actor" to sender.name))
        val payload = buildBroadcastPayload(sender.name, message)
        val groupServers = ArcNetworkConfig.serverGroups[group]?.servers ?: emptyList()
        groupServers
            .filter { it != ArcNetworkConfig.serverId }
            .forEach { ArcRelayClient.lpush("arc:inbox:broadcast:$it", payload) }
        if (ArcNetworkConfig.serverId in groupServers || groupServers.isEmpty()) {
            Bukkit.broadcastMessage("[§b$group§r] $message")
        }
    }

    fun serverBroadcast(sender: CommandSender, targetServer: String, message: String) {
        val payload = buildBroadcastPayload(sender.name, message)
        if (targetServer == ArcNetworkConfig.serverId) {
            Bukkit.broadcastMessage("[§bServer§r] $message")
        } else {
            ArcRelayClient.lpush("arc:inbox:broadcast:$targetServer", payload)
        }
    }

    private fun buildBroadcastPayload(sender: String, message: String): String {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"sender":"${sender.esc()}","message":"${message.esc()}","timestamp":${System.currentTimeMillis()}}"""
    }
}

/**
 * Network maintenance mode.
 */
object ArcNetworkMaintenance {

    fun setServerMaintenance(serverId: String, enabled: Boolean, message: String = ArcNetworkConfig.maintenanceDefaultMessage) {
        val status = if (enabled) ArcServerRegistry.ServerStatus.MAINTENANCE else ArcServerRegistry.ServerStatus.ONLINE
        ArcServerRegistry.setStatus(serverId, status)
        ArcRelayClient.setex("arc:maintenance:$serverId", 86400, if (enabled) message else "")
        ArcNetworkAudit.log("maintenance.server.${if (enabled) "on" else "off"}",
            mapOf("server" to serverId))
    }

    fun setGroupMaintenance(group: String, enabled: Boolean) {
        val servers = ArcNetworkConfig.serverGroups[group]?.servers ?: return
        servers.forEach { setServerMaintenance(it, enabled) }
        ArcNetworkAudit.log("maintenance.group.${if (enabled) "on" else "off"}", mapOf("group" to group))
    }

    fun setNetworkMaintenance(enabled: Boolean) {
        ArcServerRegistry.onlineServerIds().forEach { setServerMaintenance(it, enabled) }
        ArcNetworkAudit.log("maintenance.network.${if (enabled) "on" else "off"}", emptyMap())
    }

    fun status(): String = buildString {
        appendLine("===== Network Maintenance =====")
        ArcServerRegistry.allServers().filter { it.status == ArcServerRegistry.ServerStatus.MAINTENANCE }.forEach {
            appendLine("  ${it.id} (${it.group}): MAINTENANCE")
        }
        if (ArcServerRegistry.allServers().none { it.status == ArcServerRegistry.ServerStatus.MAINTENANCE })
            appendLine("  No servers in maintenance")
    }
}

/**
 * Evacuation — move all players from one server to another.
 */
object ArcEvacuation {

    data class EvacResult(val moved: Int, val total: Int, val failed: List<String>)

    fun evacuate(sender: CommandSender, fromServer: String, toServer: String): String {
        if (fromServer != ArcNetworkConfig.serverId) return "§cMust be run on the source server"

        val targetInfo = ArcServerRegistry.getServer(toServer)
        if (targetInfo == null) return "§cTarget server offline"

        // Set status to STOPPING
        ArcServerRegistry.setLocalStatus(ArcServerRegistry.ServerStatus.STOPPING)

        val players = Bukkit.getOnlinePlayers().toList()
        if (players.isEmpty()) {
            ArcServerRegistry.setLocalStatus(ArcServerRegistry.ServerStatus.ONLINE)
            return "§7No players to evacuate"
        }

        val plugin = Bukkit.getPluginManager().getPlugin("Arc")
            ?: return "§cArc plugin not found"
        val total = players.size
        val moved = IntArray(1)
        val failed = mutableListOf<String>()

        players.forEachIndexed { i, p ->
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val result = ArcPlayerTransfer.send(sender, p.name, toServer)
                if (result.startsWith("§a")) moved[0]++ else failed += p.name
                if (i == total - 1) {
                    ArcNetworkAudit.log("evacuation", mapOf(
                        "from" to fromServer, "to" to toServer,
                        "moved" to moved[0].toString(), "failed" to failed.joinToString(),
                        "actor" to sender.name,
                    ))
                    sender.sendMessage("§aEvacuation: ${moved[0]}/$total moved to $toServer. ${if (failed.isNotEmpty()) "§cFailed: ${failed.joinToString()}" else ""}")
                }
            }, (i * 4L)) // stagger transfers by 4 ticks (200ms) without blocking main thread
        }

        return "§eStarting evacuation of $total player(s) to $toServer…"
    }
}

/**
 * Global player lookup across all servers.
 */
object ArcGlobalPlayerLookup {

    fun find(playerName: String): String {
        // Check local first for exact ping data
        val local = Bukkit.getPlayer(playerName)
        if (local != null) {
            return "§a$playerName is online on §e${ArcNetworkConfig.serverId} §7(ping: ${local.ping}ms)"
        }
        // Redis key written every heartbeat by ArcServerRegistry
        val serverId = ArcRelayClient.get("arc:online:player:${playerName.lowercase()}")
            ?: return "§7$playerName is not online on any server"
        return "§a$playerName is online on §e$serverId"
    }

    fun findByUuid(uuid: java.util.UUID): String? {
        val local = Bukkit.getPlayer(uuid)
        if (local != null) return ArcNetworkConfig.serverId
        return ArcRelayClient.get("arc:online:player:${local?.name?.lowercase() ?: return null}")
    }
}
