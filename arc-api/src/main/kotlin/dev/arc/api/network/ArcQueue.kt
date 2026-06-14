package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Server queue system backed by Redis sorted sets.
 * Higher priority = lower score = popped first.
 */
object ArcQueue {

    fun addToQueue(player: Player, targetServer: String) {
        val uuid = player.uniqueId.toString()
        val score = calculatePriority(player)
        ArcRelayClient.zadd("arc:queue:$targetServer", score.toDouble(), uuid)
        ArcRelayClient.setex("arc:queue:$targetServer:player:$uuid:name", 3600, player.name)
        ArcRelayClient.setex("arc:queue:player:$uuid:server", 3600, targetServer)
        ArcRelayClient.setex("arc:queue:player:$uuid:score", 3600, score.toString())
        val position = (ArcRelayClient.zrank("arc:queue:$targetServer", uuid) ?: 0L) + 1L
        Bukkit.getPluginManager().callEvent(
            dev.arc.api.event.ArcNetworkQueueJoinEvent(player, targetServer, position)
        )
    }

    fun removeFromQueue(player: UUID, targetServer: String) {
        val uuid = player.toString()
        val onlinePlayer = Bukkit.getPlayer(player)
        ArcRelayClient.del("arc:queue:$targetServer:player:$uuid:name")
        ArcRelayClient.del("arc:queue:player:$uuid:server")
        ArcRelayClient.del("arc:queue:player:$uuid:score")
        ArcRelayClient.del("arc:queue:reconnect:$uuid")
        ArcRelayClient.zrem("arc:queue:$targetServer", uuid)
        if (onlinePlayer != null) {
            Bukkit.getPluginManager().callEvent(
                dev.arc.api.event.ArcNetworkQueueLeaveEvent(onlinePlayer, targetServer)
            )
        }
    }

    fun getPosition(player: Player): String {
        val uuid = player.uniqueId.toString()
        val serverId = ArcRelayClient.get("arc:queue:player:$uuid:server")
            ?: return "§7You are not in any queue. Use /arc queue join <server>"
        val rank = ArcRelayClient.zrank("arc:queue:$serverId", uuid)
        return if (rank == null) {
            "§eYou are queued for §f$serverId"
        } else {
            "§eYou are queued for §f$serverId §e(position ${rank + 1})"
        }
    }

    fun isQueued(player: Player, server: String): Boolean {
        return ArcRelayClient.get("arc:queue:$server:player:${player.uniqueId}:name") != null
    }

    fun getQueueSize(server: String): Int {
        return ArcRelayClient.zcard("arc:queue:$server").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun pauseQueue(server: String) {
        ArcRelayClient.setex("arc:queue:$server:paused", 3600, "1")
    }

    fun resumeQueue(server: String) {
        ArcRelayClient.del("arc:queue:$server:paused")
    }

    fun isPaused(server: String): Boolean {
        return ArcRelayClient.get("arc:queue:$server:paused") == "1"
    }

    private fun calculatePriority(player: Player): Long {
        var score = System.currentTimeMillis()
        if (player.hasPermission(ArcNetworkConfig.queue.vipPermission)) score -= 60_000
        if (player.hasPermission(ArcNetworkConfig.queue.priorityPermission)) score -= 300_000
        return score
    }
}
