package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.Plugin

/**
 * Broadcasts player chat to all other servers on the network.
 * Format: [server] PlayerName: message
 * Config: arc-network.yml chat.enabled (default false — noisy without proper channel setup)
 */
object ArcNetworkChat {

    @Volatile var enabled: Boolean = false

    fun processIncoming(plugin: Plugin, messages: List<String>) {
        if (messages.isEmpty()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (json in messages) {
                val server = jsonStr(json, "server") ?: continue
                val player = jsonStr(json, "player") ?: continue
                val message = jsonStr(json, "message") ?: continue
                val channel = jsonStr(json, "channel") ?: "global"
                val formatted = "§8[§b$server§8] §7$player§8: §f$message"
                when (channel) {
                    "global" -> Bukkit.broadcastMessage(formatted)
                    else -> Bukkit.broadcastMessage(formatted)
                }
            }
        })
    }

    private fun buildPayload(player: String, message: String, server: String, channel: String): String {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"player":"${player.esc()}","message":"${message.esc()}","server":"${server.esc()}","channel":"${channel.esc()}","timestamp":${System.currentTimeMillis()}}"""
    }

    private fun jsonStr(json: String, key: String): String? =
        """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(json)?.groupValues?.get(1)

    /** Listener registered on each server. */
    class ChatListener : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onChat(event: AsyncPlayerChatEvent) {
            if (!ArcRelayClient.connected) return
            if (!enabled) return
            val payload = buildPayload(event.player.name, event.message,
                ArcNetworkConfig.serverId, "global")
            ArcServerRegistry.onlineServerIds()
                .filter { it != ArcNetworkConfig.serverId }
                .forEach { ArcRelayClient.lpush("arc:inbox:chat:$it", payload) }
        }
    }
}
