package dev.arc.api.network

import dev.arc.api.ops.async.ArcAsync
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.Base64

/**
 * Deploys a plugin JAR from this server to one or all other servers via Redis.
 * Stores JAR as Base64 in arc:deploy:data:<token>, sends deploy notification
 * to arc:inbox:deploy:<targetId>.
 *
 * Enable: arc-network.yml -> plugin-deploy.enabled: true
 * This is OFF by default — arbitrary code execution across servers.
 */
object ArcPluginDeploy {

    fun deploy(sender: CommandSender, jarName: String, targets: List<String>) {
        if (!ArcNetworkConfig.pluginDeployEnabled) {
            sender.sendMessage("[Arc] §cPlugin deploy is disabled. Set arc-network.plugin-deploy.enabled: true")
            return
        }
        val jar = File("plugins", jarName)
        if (!jar.exists()) {
            sender.sendMessage("[Arc] §cFile not found: plugins/$jarName")
            return
        }
        if (!jar.name.endsWith(".jar")) {
            sender.sendMessage("[Arc] §cOnly .jar files can be deployed.")
            return
        }
        sender.sendMessage("[Arc] §7Reading ${jar.name} (${jar.length() / 1024}KB)...")
        ArcAsync.runBlockingIO {
            val token = java.util.UUID.randomUUID().toString()
            val encoded = Base64.getEncoder().encodeToString(jar.readBytes())
            // Store data with 10-minute TTL
            ArcRelayClient.setex("arc:deploy:data:$token", 600, encoded)
            val payload = buildPayload(token, jar.name)
            var sent = 0
            for (target in targets) {
                if (target == ArcNetworkConfig.serverId) continue
                ArcRelayClient.lpush("arc:inbox:deploy:$target", payload)
                sent++
            }
            ArcNetworkAudit.log("plugin.deploy", mapOf(
                "file" to jar.name, "targets" to targets.joinToString(), "actor" to sender.name
            ))
            "$sent server(s)"
        }.thenSync { result ->
            sender.sendMessage("[Arc] §aDeployed §e$jarName §ato $result.")
        }.exceptionallySync { e ->
            sender.sendMessage("[Arc] §cDeploy failed: ${e.message}")
        }
    }

    fun processIncoming(plugin: Plugin, messages: List<String>) {
        if (messages.isEmpty()) return
        for (json in messages) {
            val token = jsonStr(json, "token") ?: continue
            val filename = jsonStr(json, "filename") ?: continue
            ArcAsync.runBlockingIO {
                val encoded = ArcRelayClient.get("arc:deploy:data:$token") ?: return@runBlockingIO null
                ArcRelayClient.del("arc:deploy:data:$token")
                val bytes = Base64.getDecoder().decode(encoded)
                val target = File("plugins", filename)
                target.writeBytes(bytes)
                filename to bytes.size
            }.thenSync { result ->
                if (result == null) return@thenSync
                val (name, size) = result
                Bukkit.getLogger().info("[Arc-Deploy] Received $name (${size / 1024}KB) — loading...")
                val existing = Bukkit.getPluginManager().getPlugin(name.removeSuffix(".jar"))
                val targetFile = File("plugins", name)
                if (existing != null) {
                    Bukkit.getPluginManager().disablePlugin(existing)
                }
                val loaded = runCatching { Bukkit.getPluginManager().loadPlugin(targetFile) }.getOrNull()
                if (loaded != null) {
                    Bukkit.getPluginManager().enablePlugin(loaded)
                    Bukkit.getLogger().info("[Arc-Deploy] Installed $name v${loaded.description.version}")
                } else {
                    Bukkit.getLogger().info("[Arc-Deploy] $name placed — restart to load.")
                }
            }
        }
    }

    private fun buildPayload(token: String, filename: String): String {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"token":"${token.esc()}","filename":"${filename.esc()}","timestamp":${System.currentTimeMillis()}}"""
    }

    private fun jsonStr(json: String, key: String): String? =
        """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(json)?.groupValues?.get(1)
}
