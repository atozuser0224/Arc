package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Player transfer service. Coordinates with proxy to move players.
 */
object ArcPlayerTransfer {

    data class TransferResult(val success: Boolean, val message: String)

    fun send(sender: CommandSender, playerName: String, targetServer: String): String {
        val player = Bukkit.getPlayer(playerName)
        if (player == null) return "§cPlayer not found: $playerName"

        val config = ArcNetworkConfig
        if (!config.enabled) return "§cArc Network is not enabled"

        // Check target server
        val targetInfo = ArcServerRegistry.getServer(targetServer)
        if (targetInfo == null) return "§cServer '$targetServer' is offline or unknown"

        if (targetInfo.status == ArcServerRegistry.ServerStatus.OFFLINE) return "§cServer '$targetServer' is offline"
        if (targetInfo.status == ArcServerRegistry.ServerStatus.MAINTENANCE) return "§cServer '$targetServer' is under maintenance"
        if (targetInfo.status == ArcServerRegistry.ServerStatus.STOPPING) return "§cServer '$targetServer' is shutting down"
        if (targetInfo.status == ArcServerRegistry.ServerStatus.FULL && targetInfo.players >= targetInfo.maxPlayers)
            return "§eServer '$targetServer' is full. Use /arc queue join $targetServer"

        // Check group transfer permissions
        val targetGroup = targetInfo.group
        val allowedFrom = config.serverGroups[targetGroup]?.allowTransfersFrom ?: listOf("*")
        if ("*" !in allowedFrom && config.serverGroup !in allowedFrom)
            return "§cTransfer from '${config.serverGroup}' to '$targetGroup' is not allowed"

        // Permission check
        if (config.transfer.requirePermission && !sender.hasPermission("${config.transfer.permissionPrefix}.$targetGroup"))
            return "§cNo permission: ${config.transfer.permissionPrefix}.$targetGroup"

        // Save player data
        if (config.transfer.saveBeforeTransfer) player.saveData()

        // Send transfer via plugin messaging
        // Velocity uses "velocity:player_info" but also accepts the legacy BungeeCord channel for Connect.
        // For Velocity with modern forwarding, the standard approach is still BungeeCord channel "Connect"
        // (Velocity forwards it). We use the Arc plugin itself as carrier to avoid polluting other plugins.
        val arcPlugin = Bukkit.getPluginManager().getPlugin("Arc")
            ?: Bukkit.getPluginManager().plugins.firstOrNull { it.isEnabled }
            ?: return "§cArc plugin not found — cannot send transfer message"
        val messenger = Bukkit.getMessenger()
        val channel = "BungeeCord"
        if (!messenger.isOutgoingChannelRegistered(arcPlugin, channel)) {
            messenger.registerOutgoingPluginChannel(arcPlugin, channel)
        }
        val baos = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(baos)
        dos.writeUTF("Connect")
        dos.writeUTF(targetServer)
        player.sendPluginMessage(arcPlugin, channel, baos.toByteArray())

        // Fire network event before audit so handlers can add metadata
        org.bukkit.Bukkit.getPluginManager().callEvent(
            dev.arc.api.event.ArcNetworkPlayerTransferEvent(player, config.serverId, targetServer, sender.name)
        )

        ArcNetworkAudit.log("transfer", mapOf(
            "player" to player.uniqueId.toString(),
            "playerName" to player.name,
            "fromServer" to config.serverId,
            "toServer" to targetServer,
            "result" to "SUCCESS",
            "triggeredBy" to sender.name,
        ))

        return "§aTransferring ${player.name} to $targetServer..."
    }

    fun sendAll(sender: CommandSender, fromServer: String, toServer: String): String {
        if (fromServer != ArcNetworkConfig.serverId) return "§csendall must be run on the source server"

        val targetInfo = ArcServerRegistry.getServer(toServer)
        if (targetInfo == null) return "§cTarget server offline"

        val players = Bukkit.getOnlinePlayers().toList()
        if (players.isEmpty()) return "§7No players to transfer"

        var moved = 0
        val failed = mutableListOf<String>()

        for (p in players) {
            val result = send(sender, p.name, toServer)
            if (result.startsWith("§a")) moved++ else failed += p.name
        }

        return "§aTransferred $moved/${players.size} players to $toServer. ${if (failed.isNotEmpty()) "Failed: ${failed.joinToString()}" else ""}"
    }

    fun hub(sender: CommandSender): String {
        val player = sender as? Player ?: return "§cPlayer-only command"
        val hubGroup = ArcNetworkConfig.serverGroups.entries.firstOrNull { it.value.defaultHub }
        if (hubGroup == null) return "§cNo hub group configured"

        val hubServers = hubGroup.value.servers
        val onlineHub = hubServers.firstOrNull { ArcServerRegistry.getServer(it)?.status == ArcServerRegistry.ServerStatus.ONLINE }
            ?: return "§cNo hub server online"

        return send(sender, player.name, onlineHub)
    }

    fun queue(player: Player, targetServer: String): String {
        val targetInfo = ArcServerRegistry.getServer(targetServer)
        if (targetInfo?.status == ArcServerRegistry.ServerStatus.FULL) {
            ArcQueue.addToQueue(player, targetServer)
            return ArcQueue.getPosition(player)
        }
        return send(player, player.name, targetServer)
    }
}
