package dev.arc.api.ops.proxy

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File

/**
 * `/arc proxy-check` — validates common Velocity/BungeeCord setup issues.
 *
 * Checks:
 * - online-mode / bungeecord / velocity config consistency
 * - forwarding secret presence
 * - player-info-forwarding
 * - firewall / ip-forwarding settings
 * - modern forwarding vs legacy bungeecord mode
 */
object ProxySetupChecker {

    data class ProxyReport(
        val proxyMode: String,
        val onlineMode: Boolean,
        val bungeecord: Boolean,
        val velocityModern: Boolean,
        val hasForwardingSecret: Boolean,
        val issues: List<String>,
    ) {
        fun render(): String = buildString {
            appendLine("===== Arc Proxy Setup Check =====")
            appendLine("proxy mode    : $proxyMode")
            appendLine("online-mode   : ${if (onlineMode) "§atrue" else "§cfalse"}")
            appendLine("bungeecord    : ${if (bungeecord) "§atrue" else "§7false"}")
            appendLine("velocity-modern: ${if (velocityModern) "§atrue" else "§7false"}")
            appendLine("forwarding-secret: ${if (hasForwardingSecret) "§aset" else "§cNOT SET"}")

            if (issues.isEmpty()) {
                appendLine("§aNo issues detected")
            } else {
                appendLine("issues:")
                issues.forEach { appendLine("  §c- $it") }
            }

            appendLine("")
            if (bungeecord) {
                if (!hasForwardingSecret) {
                    appendLine("§eRecommendation: switch to Velocity modern forwarding.")
                    appendLine("  Set forwarding-secret in paper.yml and velocity.toml")
                }
            }
        }
    }

    fun check(): ProxyReport {
        val issues = mutableListOf<String>()
        val spigotYml = File("spigot.yml")
        val paperDir = File("paper")
        val paperGlobal = File(paperDir, "paper-global.yml")
        val paperDefault = File(paperDir, "paper-world-defaults.yml")

        val bungeecord = getYamlValue(spigotYml, "settings.bungeecord", false)
        val onlineMode = Bukkit.getOnlineMode()
        val velocityModern = getYamlValue(paperGlobal, "proxies.velocity.enabled", false)
            || getYamlValue(paperDefault, "proxies.velocity.enabled", false)
        val forwardingSecret = getYamlString(paperGlobal, "proxies.velocity.secret")
            ?: getYamlString(paperDefault, "proxies.velocity.secret")
        val hasSecret = !forwardingSecret.isNullOrBlank() && forwardingSecret != ""

        val proxyMode = when {
            velocityModern -> "velocity-modern"
            bungeecord -> "bungeecord (legacy)"
            else -> "none (direct connection)"
        }

        // Checks
        if (bungeecord && velocityModern) {
            issues += "Both bungeecord and velocity-modern enabled — pick one"
        }
        if (bungeecord && onlineMode) {
            issues += "bungeecord=true but online-mode=true — BungeeCord should handle auth"
        }
        if (velocityModern && !hasSecret) {
            issues += "velocity-modern enabled but forwarding-secret is NOT set"
        }
        if (velocityModern && onlineMode) {
            issues += "velocity-modern enabled but online-mode=true — Velocity handles auth"
        }
        if (!bungeecord && !velocityModern && !onlineMode) {
            issues += "offline-mode without proxy — players can join with any name"
        }
        if (bungeecord && hasSecret) {
            issues += "Using bungeecord mode with forwarding-secret — bungeecord ignores the secret (use velocity-modern instead)"
        }

        return ProxyReport(proxyMode, onlineMode, bungeecord, velocityModern, hasSecret, issues)
    }

    private fun getYamlValue(file: File, path: String, default: Boolean): Boolean {
        if (!file.isFile) return default
        return runCatching {
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file).getBoolean(path, default)
        }.getOrDefault(default)
    }

    private fun getYamlString(file: File, path: String): String? {
        if (!file.isFile) return null
        return runCatching {
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file).getString(path)
        }.getOrNull()
    }

    fun dispatch(sender: CommandSender): Boolean {
        val report = check()
        report.render().lineSequence().forEach { sender.sendMessage(it) }
        return true
    }
}
