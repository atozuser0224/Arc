package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Polls Redis queues every [QueueConfig.transferCheckIntervalSeconds] seconds and
 * transfers waiting players when a server has open capacity.
 * Also sends position announcements every [QueueConfig.positionMessageInterval] seconds.
 *
 * All callbacks run on the main Bukkit thread so ArcPlayerTransfer.send() can call
 * player.sendPluginMessage() safely.
 */
object ArcQueueDrainer {

    private var drainTaskId: Int = -1
    private var announceTaskId: Int = -1

    fun start(plugin: Plugin) {
        stop()
        val drainTicks = (ArcNetworkConfig.queue.transferCheckIntervalSeconds * 20).toLong()
        drainTaskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (ArcRelayClient.connected) drain()
        }, drainTicks, drainTicks).taskId

        val announceTicks = (ArcNetworkConfig.queue.positionMessageInterval * 20).toLong()
        announceTaskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (ArcRelayClient.connected) announcePositions()
        }, announceTicks, announceTicks).taskId
    }

    fun stop() {
        if (drainTaskId != -1) { Bukkit.getScheduler().cancelTask(drainTaskId); drainTaskId = -1 }
        if (announceTaskId != -1) { Bukkit.getScheduler().cancelTask(announceTaskId); announceTaskId = -1 }
    }

    private fun drain() {
        for (serverInfo in ArcServerRegistry.allServers()) {
            if (serverInfo.status != ArcServerRegistry.ServerStatus.ONLINE) continue
            if (ArcQueue.isPaused(serverInfo.id)) continue
            val available = (serverInfo.maxPlayers - serverInfo.players).coerceAtLeast(0)
            if (available == 0) continue

            val uuids = ArcRelayClient.zpopmin("arc:queue:${serverInfo.id}", available)
            for (uuidStr in uuids) {
                ArcRelayClient.del("arc:queue:player:$uuidStr:server")
                ArcRelayClient.del("arc:queue:${serverInfo.id}:player:$uuidStr:name")
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                val player = Bukkit.getPlayer(uuid) ?: continue
                ArcPlayerTransfer.send(Bukkit.getConsoleSender(), player.name, serverInfo.id)
                ArcNetworkAudit.log("queue.drain", mapOf("player" to player.name, "server" to serverInfo.id))
            }
        }
    }

    private fun announcePositions() {
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId.toString()
            val serverId = ArcRelayClient.get("arc:queue:player:$uuid:server") ?: continue
            val rank = ArcRelayClient.zrank("arc:queue:$serverId", uuid) ?: continue
            val total = ArcQueue.getQueueSize(serverId)
            player.sendMessage("§eQueue for §f$serverId§e: position §f${rank + 1}§7/$total")
        }
    }
}
