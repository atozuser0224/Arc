package dev.arc.api.network

import dev.arc.api.ops.async.ArcAsync
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Sends Discord embed messages via webhook URL stored in ArcGlobalConfig.
 *
 * Configure at runtime (no restart needed):
 *   /arc network globalconfig set discord.webhook.url https://discord.com/api/webhooks/...
 *   /arc network globalconfig set discord.events ban,broadcast,server-down
 *
 * Events: ban, broadcast, server-down, player-transfer, remote-cmd
 */
object ArcDiscordWebhook {

    private val http by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private fun webhookUrl(): String? = ArcGlobalConfig.get("discord.webhook.url")?.takeIf { it.isNotBlank() }

    private fun enabledEvents(): Set<String> =
        ArcGlobalConfig.get("discord.events")?.split(",")?.map { it.trim() }?.toSet()
            ?: setOf("ban", "broadcast", "server-down")

    fun send(event: String, title: String, description: String, color: Int = 0x5865F2) {
        val url = webhookUrl() ?: return
        if (event !in enabledEvents()) return
        ArcAsync.runBlockingIO {
            sendEmbed(url, title, description, color)
        }
    }

    fun sendBan(playerName: String, reason: String, actor: String, permanent: Boolean) =
        send("ban", "🔨 Player Banned",
            "**$playerName** banned by **$actor**\nReason: $reason\nDuration: ${if (permanent) "Permanent" else "Temporary"}",
            color = 0xED4245)

    fun sendBroadcast(message: String, actor: String) =
        send("broadcast", "📢 Network Broadcast",
            "**$actor**: $message", color = 0x57F287)

    fun sendServerDown(serverId: String) =
        send("server-down", "🔴 Server Offline",
            "**$serverId** went offline", color = 0xED4245)

    fun sendServerUp(serverId: String) =
        send("server-down", "🟢 Server Online",
            "**$serverId** came online", color = 0x57F287)

    private fun sendEmbed(url: String, title: String, description: String, color: Int) {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """{"embeds":[{"title":"${title.esc()}","description":"${description.esc()}","color":$color,"footer":{"text":"Arc Network • ${ArcNetworkConfig.serverId}"}}]}"""
        runCatching {
            val req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            http.send(req, HttpResponse.BodyHandlers.discarding())
        }
    }
}
