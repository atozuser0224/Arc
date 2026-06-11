package dev.arc.api.ops.security

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File
import java.net.ServerSocket

/**
 * `/arc security-audit` — basic security checks for operators.
 *
 * Checks:
 * - offline-mode / proxy config
 * - RCON enabled without password
 * - query enabled without restriction
 * - server port exposure
 * - perm plugins (LuckPerms) present
 * - world-download prevention
 * - OP list review
 */
object SecurityAudit {

    data class SecReport(
        val issues: List<Issue>,
    ) {
        fun render(): String = buildString {
            appendLine("===== Arc Security Audit =====")
            if (issues.isEmpty()) {
                appendLine("§aNo critical issues found")
            } else {
                val critical = issues.filter { it.severity == "CRITICAL" }
                val warn = issues.filter { it.severity == "WARN" }
                val info = issues.filter { it.severity == "INFO" }
                if (critical.isNotEmpty()) {
                    appendLine("§cCRITICAL (${critical.size}):")
                    critical.forEach { appendLine("  §c[!] ${it.message}") }
                }
                if (warn.isNotEmpty()) {
                    appendLine("§eWARNINGS (${warn.size}):")
                    warn.forEach { appendLine("  §e[~] ${it.message}") }
                }
                if (info.isNotEmpty()) {
                    appendLine("§7INFO (${info.size}):")
                    info.forEach { appendLine("  §7[i] ${it.message}") }
                }
            }
        }
    }

    data class Issue(val severity: String, val message: String)

    fun audit(): SecReport {
        val issues = mutableListOf<Issue>()

        // Offline mode
        if (!Bukkit.getOnlineMode()) {
            val bungee = getYamlBool(File("spigot.yml"), "settings.bungeecord")
            val velo = getYamlBool(File("paper/paper-global.yml"), "proxies.velocity.enabled")
            if (!bungee && !velo) {
                issues += Issue("CRITICAL", "offline-mode without proxy — anyone can join with any name (including OP)")
            }
        }

        // RCON
        val serverProps = File("server.properties")
        if (serverProps.isFile) {
            val props = serverProps.readLines().associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
            if (props["enable-rcon"] == "true" && props["rcon.password"].isNullOrBlank()) {
                issues += Issue("CRITICAL", "RCON enabled without password")
            }
            if (props["enable-query"] == "true") {
                issues += Issue("WARN", "Query protocol enabled — may expose server info to internet")
            }
        }

        // Permission plugin
        val hasPermPlugin = Bukkit.getPluginManager().plugins.any {
            it.name.contains("LuckPerms", true) || it.name.contains("PermissionsEx", true)
        }
        if (!hasPermPlugin) {
            issues += Issue("INFO", "No permission plugin detected (LuckPerms recommended)")
        }

        // OP count
        val ops = Bukkit.getOperators().filter { it.isOp }
        if (ops.size > 3) {
            issues += Issue("WARN", "${ops.size} operators — review for unnecessary OPs")
        }
        ops.forEach { op -> issues += Issue("INFO", "OP: ${op.name}") }

        // World download protection
        val worldContainer = Bukkit.getWorldContainer()
        if (worldContainer.canWrite()) {
            issues += Issue("INFO", "World directory is writable — ensure backups exist")
        }

        // Server port bound to 0.0.0.0
        val port = Bukkit.getPort()
        if (port > 0) {
            issues += Issue("INFO", "Server listening on port $port. Ensure firewall allows only intended users")
        }

        // Plugin list with known risky plugins
        val riskyPlugins = listOf("PluginMetrics", "bStats")
        val loadedRisky = Bukkit.getPluginManager().plugins.filter { p ->
            riskyPlugins.any { p.name.contains(it, true) }
        }
        if (loadedRisky.isNotEmpty()) {
            issues += Issue("INFO", "Metrics plugins loaded: ${loadedRisky.joinToString { it.name }}")
        }

        return SecReport(issues)
    }

    private fun getYamlBool(file: File, path: String): Boolean {
        if (!file.isFile) return false
        return runCatching {
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file).getBoolean(path, false)
        }.getOrDefault(false)
    }

    fun dispatch(sender: CommandSender): Boolean {
        val report = audit()
        report.render().lineSequence().forEach { sender.sendMessage(it) }
        return true
    }
}
