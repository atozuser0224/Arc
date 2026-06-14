package dev.arc.api.network

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * Polls per-server inbox lists for broadcast messages and remote commands.
 * Also monitors Redis connectivity and triggers auto-reconnect when dropped.
 *
 * Uses LPUSH (sender) + RPOP batch (receiver) on arc:inbox:<type>:<serverId>
 * instead of JedisPubSub — which would require subclassing an abstract class at
 * runtime, impossible without a compile-time Jedis dependency or bytecode generation.
 */
object ArcNetworkInbox {

    private var taskId: Int = -1
    @Volatile private var lastReconnectAttempt = 0L
    private const val RECONNECT_COOLDOWN_MS = 30_000L

    // Per-minute rate-limit for remote commands: requester → (windowStart, count)
    private val cmdRateMap = ConcurrentHashMap<String, Pair<Long, Int>>()

    fun start(plugin: Plugin) {
        stop()
        // Poll every 2 seconds on an async thread — Redis I/O should not block the main thread
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            checkReconnect()
            if (!ArcRelayClient.connected) return@Runnable
            drainBroadcast(plugin)
            drainBans(plugin)
            if (ArcNetworkConfig.remoteCommand.enabled) drainRemoteCommands(plugin)
        }, 40L, 40L).taskId
    }

    fun stop() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1 }
    }

    /** Push a remote command to a target server's inbox. */
    fun sendRemoteCommand(targetServerId: String, command: String, requester: String) {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"command":"${command.esc()}","requester":"${requester.esc()}","timestamp":${System.currentTimeMillis()}}"""
        ArcRelayClient.lpush("arc:inbox:cmd:$targetServerId", json)
    }

    // ---- private ----

    private fun checkReconnect() {
        if (ArcRelayClient.connected) return
        val now = System.currentTimeMillis()
        if (now - lastReconnectAttempt < RECONNECT_COOLDOWN_MS) return
        lastReconnectAttempt = now
        Bukkit.getLogger().info("[Arc-Network] Redis disconnected — attempting reconnect…")
        ArcRelayClient.reconnect()
        if (ArcRelayClient.connected) {
            Bukkit.getLogger().info("[Arc-Network] Redis reconnected successfully.")
        }
    }

    private fun drainBroadcast(plugin: Plugin) {
        val messages = ArcRelayClient.rpopBatch("arc:inbox:broadcast:${ArcNetworkConfig.serverId}")
        if (messages.isEmpty()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (json in messages) {
                val msg = jsonStr(json, "message") ?: continue
                val sender = jsonStr(json, "sender") ?: "Network"
                Bukkit.broadcastMessage("[§b$sender§r] $msg")
            }
        })
    }

    private fun drainRemoteCommands(plugin: Plugin) {
        val cmds = ArcRelayClient.rpopBatch("arc:inbox:cmd:${ArcNetworkConfig.serverId}")
        if (cmds.isEmpty()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (json in cmds) {
                val command = jsonStr(json, "command") ?: continue
                val requester = jsonStr(json, "requester") ?: "unknown"
                val result = executeRemoteCommand(command, requester)
                ArcNetworkAudit.log("remote-command.exec", mapOf(
                    "command" to command, "requester" to requester,
                    "server" to ArcNetworkConfig.serverId, "result" to result,
                ))
            }
        })
    }

    private fun executeRemoteCommand(command: String, requester: String): String {
        val cfg = ArcNetworkConfig.remoteCommand
        val verb = command.trim().split(" ").firstOrNull()?.lowercase() ?: return "EMPTY"

        if (cfg.blocklist.any { it.lowercase() == verb }) return "BLOCKED:$verb"
        if (cfg.allowlist.isNotEmpty() && cfg.allowlist.none { it.lowercase() == verb }) return "NOT_ALLOWED:$verb"

        // Per-minute rate limit per requester
        val now = System.currentTimeMillis()
        val (windowStart, count) = cmdRateMap[requester] ?: (now to 0)
        val newCount = if (now - windowStart > 60_000L) 1 else count + 1
        cmdRateMap[requester] = now to newCount
        if (newCount > cfg.rateLimit) return "RATE_LIMITED"

        return try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            "OK"
        } catch (e: Exception) {
            "ERROR:${e.message}"
        }
    }

    private fun drainBans(plugin: Plugin) {
        val entries = ArcRelayClient.rpopBatch("arc:inbox:ban:${ArcNetworkConfig.serverId}")
        if (entries.isEmpty()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (json in entries) {
                val entry = ArcGlobalBan.deserialize(json) ?: continue
                val uuid = runCatching { java.util.UUID.fromString(entry.uuid) }.getOrNull() ?: continue
                // Fire network event so plugins can react
                val event = dev.arc.api.event.ArcNetworkPlayerBanEvent(
                    uuid, entry.playerName, entry.reason, entry.actor, entry.expiry, entry.type
                )
                Bukkit.getPluginManager().callEvent(event)
                // Kick if banned (not mute, not ip-ban — those need different handling)
                if (entry.type == "BAN") {
                    Bukkit.getPlayer(uuid)?.kickPlayer("§cYou have been banned from this network.\n§7Reason: §f${entry.reason}")
                }
            }
        })
    }

    private fun jsonStr(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex().find(json)?.groupValues?.get(1)
}
