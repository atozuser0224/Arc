package dev.arc.api.ops.paste

import dev.arc.api.ops.PasteUploader
import dev.arc.api.ops.doctor.ServerDoctor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File

/**
 * `/arc paste doctor|config|logs|plugin <name>`.
 * Uploads a report to a paste service (mclo.gs by default).
 */
object PasteCommands {

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("doctor", "config", "logs", "plugin")
        3 -> if (args.getOrNull(1)?.lowercase() == "plugin") Bukkit.getPluginManager().plugins.map { it.name }
        else emptyList()
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase() ?: run {
            sender.sendMessage("/arc paste <doctor|config|logs|plugin <name>>")
            return true
        }
        sender.sendMessage("[Arc] generating paste…")

        val content = when (sub) {
            "doctor" -> ServerDoctor.report()
            "config" -> gatherConfig()
            "logs" -> gatherLogs()
            "plugin" -> {
                val name = args.getOrNull(2)
                if (name == null) { sender.sendMessage("[Arc] specify a plugin name"); return true }
                val plugin = Bukkit.getPluginManager().getPlugin(name)
                if (plugin == null) { sender.sendMessage("[Arc] plugin not found: $name"); return true }
                dev.arc.api.ops.plugin.PluginInspector.report(plugin).render() + "\n"
            }
            else -> { sender.sendMessage("[Arc] unknown paste type: $sub"); return true }
        }

        sender.sendMessage("[Arc] uploading…")
        try {
            val url = PasteUploader.upload(content, File("arc-ops/pastes"))
            sender.sendMessage("[Arc] paste: §a$url")
        } catch (e: Exception) {
            sender.sendMessage("[Arc] §cupload failed: ${e.message}")
        }
        return true
    }

    private fun gatherConfig(): String {
        val sb = StringBuilder("=== Arc Config ===\n")
        // arc-config.yml
        val arcCfg = File("arc-config.yml")
        if (arcCfg.isFile) {
            sb.appendLine("--- arc-config.yml ---")
            sb.appendLine(arcCfg.readText().take(8192))
        }
        // arc-ops.yml
        val opsCfg = File("arc-ops/arc-ops.yml")
        if (opsCfg.isFile) {
            sb.appendLine("--- arc-ops.yml ---")
            sb.appendLine(opsCfg.readText().take(8192))
        }
        sb.appendLine("=== end config ===")
        return sb.toString()
    }

    private fun gatherLogs(): String {
        val sb = StringBuilder("=== Arc Logs (last 200 lines) ===\n")
        val logFile = File(Bukkit.getWorldContainer(), "logs/latest.log")
        if (logFile.isFile) {
            val lines = logFile.readLines().takeLast(200)
            // Mask sensitive data
            lines.forEach { line ->
                var masked = line
                masked = masked.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "***")
                masked = masked.replace(Regex("[Pp]assword[:=]\\s*\\S+"), "password=***")
                masked = masked.replace(Regex("[Tt]oken[:=]\\s*\\S+"), "token=***")
                sb.appendLine(masked)
            }
        }
        sb.appendLine("=== end logs ===")
        return sb.toString()
    }
}
