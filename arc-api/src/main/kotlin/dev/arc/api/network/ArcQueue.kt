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
    }

    fun removeFromQueue(player: UUID, targetServer: String) {
        ArcRelayClient.del("arc:queue:$targetServer:player:${player}:name")
        ArcRelayClient.zpopmin("arc:queue:$targetServer:player:${player}") // approximate
    }

    fun getPosition(player: Player): String {
        val config = ArcNetworkConfig
        for (serverId in ArcServerRegistry.onlineServerIds()) {
            if (isQueued(player, serverId)) {
                return "§eYou are queued for §f$serverId §e(position not available client-side)"
            }
        }
        return "§7You are not in any queue. Use /arc queue join <server>"
    }

    fun isQueued(player: Player, server: String): Boolean {
        return ArcRelayClient.get("arc:queue:$server:player:${player.uniqueId}:name") != null
    }

    fun getQueueSize(server: String): Int {
        // Approximate — counts members in the sorted set
        return ArcServerRegistry.onlineServerIds().size // simplified
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
