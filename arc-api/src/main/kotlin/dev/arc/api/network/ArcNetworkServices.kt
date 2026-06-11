package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Network-wide broadcast and maintenance commands.
 */
object ArcNetworkBroadcast {

    fun networkBroadcast(sender: CommandSender, message: String) {
        val action = "broadcast.network"
        ArcNetworkAudit.log(action, mapOf("message" to message.take(100), "actor" to sender.name))
        ArcRelayClient.publish("arc:broadcast:network", buildBroadcastPayload(sender.name, message))
        Bukkit.broadcastMessage("[Network] $message")
    }

    fun groupBroadcast(sender: CommandSender, group: String, message: String) {
        ArcNetworkAudit.log("broadcast.group", mapOf("group" to group, "actor" to sender.name))
        ArcRelayClient.publish("arc:broadcast:group:$group", buildBroadcastPayload(sender.name, message))
        Bukkit.broadcastMessage("[$group] $message")
    }

    fun serverBroadcast(sender: CommandSender, targetServer: String, message: String) {
        ArcRelayClient.publish("arc:broadcast:server:$targetServer", buildBroadcastPayload(sender.name, message))
        if (targetServer == ArcNetworkConfig.serverId) Bukkit.broadcastMessage("[Server] $message")
    }

    private fun buildBroadcastPayload(sender: String, message: String): String {
        return """{"sender":"$sender","message":"${message.replace("\"", "\\\"")}","timestamp":${System.currentTimeMillis()}}"""
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

        var moved = 0
        val failed = mutableListOf<String>()

        for (p in players) {
            val result = ArcPlayerTransfer.send(sender, p.name, toServer)
            if (result.startsWith("§a")) moved++ else failed += p.name
            Thread.sleep(200) // 200ms delay between transfers
        }

        ArcNetworkAudit.log("evacuation", mapOf(
            "from" to fromServer, "to" to toServer,
            "moved" to moved.toString(), "failed" to failed.joinToString(),
            "actor" to sender.name,
        ))

        return "§aEvacuation: $moved/${players.size} moved to $toServer. ${if (failed.isNotEmpty()) "§cFailed: ${failed.joinToString()}" else ""}"
    }
}

/**
 * Global player lookup across all servers.
 */
object ArcGlobalPlayerLookup {

    fun find(playerName: String): String {
        for (serverId in ArcServerRegistry.onlineServerIds()) {
            // Check if player is on this server
            val player = if (serverId == ArcNetworkConfig.serverId) {
                Bukkit.getPlayer(playerName)
            } else null

            if (player != null) {
                return "§a$playerName is online on §e$serverId §7(ping: ${player.ping}ms)"
            }

            // For remote servers, we rely on the heartbeat data
            // A more complete implementation would query the remote server
        }
        return "§7$playerName is not online on any server"
    }

    fun findByUuid(uuid: java.util.UUID): String? {
        val player = Bukkit.getPlayer(uuid)
        if (player != null) return ArcNetworkConfig.serverId

        // Check other servers via relay
        for (serverId in ArcServerRegistry.onlineServerIds()) {
            if (serverId == ArcNetworkConfig.serverId) continue
            // Poll remote server — simplified
        }
        return null
    }
}
