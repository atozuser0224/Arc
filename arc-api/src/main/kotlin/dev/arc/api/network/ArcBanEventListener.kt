package dev.arc.api.network

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.time.Instant

/** Enforces global bans and mutes from ArcGlobalBan on this server. */
class ArcBanEventListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (!ArcRelayClient.connected) return

        val ban = ArcGlobalBan.getBan(event.uniqueId)
        if (ban != null) {
            if (ban.expiry != null && ban.expiry < Instant.now().epochSecond) {
                ArcGlobalBan.unban(event.uniqueId)
            } else {
                val expStr = ban.expiry?.let { Instant.ofEpochSecond(it) } ?: "permanent"
                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cYou are banned from this network.\n§7Reason: §f${ban.reason}\n§7Expires: §f$expStr"
                )
                return
            }
        }

        val ip = event.address.hostAddress
        if (ArcGlobalBan.isIpBanned(ip)) {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                "§cYour IP address is banned from this network."
            )
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!ArcRelayClient.connected) return
        if (ArcGlobalBan.isMuted(event.player.uniqueId)) {
            event.isCancelled = true
            event.player.sendMessage("§cYou are muted on this network.")
        }
    }
}
