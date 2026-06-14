package dev.arc.api.network

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Preserves queue position across player disconnects.
 *
 * On quit: writes arc:queue:reconnect:<uuid> with TTL = keepOnDisconnectSeconds.
 * ArcQueueDrainer skips offline players that have this key, so their sorted-set
 * entry survives until either they reconnect or the TTL expires.
 *
 * On join: deletes the reconnect key and informs the player of their position
 * if they are still in a queue.
 */
class ArcQueueEventListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        if (!ArcRelayClient.connected) return
        val uuid = event.player.uniqueId.toString()
        // Only set the grace key if the player is actually in a queue
        ArcRelayClient.get("arc:queue:player:$uuid:server") ?: return
        ArcRelayClient.setex(
            "arc:queue:reconnect:$uuid",
            ArcNetworkConfig.queue.keepOnDisconnectSeconds,
            "1",
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (!ArcRelayClient.connected) return
        val player = event.player
        val uuid = player.uniqueId.toString()
        // Clear the grace key — they're back
        ArcRelayClient.del("arc:queue:reconnect:$uuid")
        // Inform them of their position if still queued
        val serverId = ArcRelayClient.get("arc:queue:player:$uuid:server") ?: return
        val rank = ArcRelayClient.zrank("arc:queue:$serverId", uuid) ?: return
        val total = ArcQueue.getQueueSize(serverId)
        player.sendMessage("§eWelcome back! You are still queued for §f$serverId §e(position §f${rank + 1}§7/$total§e).")
    }
}
