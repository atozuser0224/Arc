package dev.arc.api.ops.logs

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * `/arc logs summary|errors|warnings|plugin <name>|cleanup`.
 * Reads the latest.log in the server directory.
 */
object LogCommands {

    private val tsFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("summary", "errors", "warnings", "plugin", "cleanup")
        3 -> if (args.getOrNull(1)?.lowercase() == "plugin") Bukkit.getPluginManager().plugins.map { it.name }
        else emptyList()
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "summary" -> summary(sender)
            "errors" -> errors(sender)
            "warnings" -> warnings(sender)
            "plugin" -> byPlugin(sender, args.getOrNull(2))
            "cleanup" -> cleanup(sender, args)
            else -> { sender.sendMessage("/arc logs <summary|errors|warnings|plugin <p>|cleanup>"); return true }
        }
        return true
    }

    private fun logFile(): File = File(Bukkit.getWorldContainer(), "logs/latest.log")

    private fun readLines(): List<String> {
        val f = logFile()
        if (!f.isFile) return emptyList()
        return runCatching { f.readLines() }.getOrDefault(emptyList())
    }

    private fun summary(sender: CommandSender) {
        val lines = readLines()
        val errors = lines.count { it.contains("[ERROR]") || it.contains("/ERROR]") }
        val warns = lines.count { it.contains("[WARN]") || it.contains("/WARN]") }
        sender.sendMessage("[Arc] Log summary (${logFile().name}):")
        sender.sendMessage("  total lines: ${lines.size}")
        sender.sendMessage("  errors  : $errors")
        sender.sendMessage("  warnings: $warns")
        sender.sendMessage("  info    : ${lines.size - errors - warns}")

        // Top error messages (compressed)
        val errorLines = lines.filter { it.contains("[ERROR]") || it.contains("/ERROR]") }
        val errorGroups = errorLines.groupBy { extractMessage(it) }.entries
            .sortedByDescending { it.value.size }.take(5)
        if (errorGroups.isNotEmpty()) {
            sender.sendMessage("  top errors:")
            errorGroups.forEach { (msg, occ) ->
                sender.sendMessage("    ${occ.size}x $msg")
            }
        }
    }

    private fun errors(sender: CommandSender) {
        val lines = readLines().filter { it.contains("[ERROR]") || it.contains("/ERROR]") }.takeLast(30)
        if (lines.isEmpty()) { sender.sendMessage("[Arc] no errors in log"); return }
        sender.sendMessage("[Arc] Recent errors:")
        lines.forEach { sender.sendMessage("  §c$it") }
    }

    private fun warnings(sender: CommandSender) {
        val lines = readLines().filter { it.contains("[WARN]") || it.contains("/WARN]") }.takeLast(30)
        if (lines.isEmpty()) { sender.sendMessage("[Arc] no warnings in log"); return }
        sender.sendMessage("[Arc] Recent warnings:")
        lines.forEach { sender.sendMessage("  §e$it") }
    }

    private fun byPlugin(sender: CommandSender, pluginName: String?) {
        if (pluginName == null) { sender.sendMessage("[Arc] specify a plugin name"); return }
        val lines = readLines().filter {
            it.contains(pluginName, true) && (it.contains("[ERROR]") || it.contains("[WARN]") || it.contains("/ERROR]") || it.contains("/WARN]") || it.contains("[INFO]") || it.contains("/INFO]"))
        }.takeLast(30)

        if (lines.isEmpty()) { sender.sendMessage("[Arc] no log entries for $pluginName"); return }
        sender.sendMessage("[Arc] Log ($pluginName):")
        lines.forEach { sender.sendMessage("  §7$it") }
    }

    private fun cleanup(sender: CommandSender, args: Array<out String>) {
        sender.sendMessage("[Arc] log cleanup runs via arc-ops.yml settings:")
        sender.sendMessage("  logs.cleanup.delete-older-than-days: 30")
        sender.sendMessage("  logs.cleanup.compress-older-than-days: 7")
        sender.sendMessage("  logs.cleanup.max-total-size-mb: 2048")
        sender.sendMessage("§7Note: cleanup is not yet automated. Check arc-ops.yml.")
    }

    private fun extractMessage(line: String): String {
        // Remove timestamp and log level, keep the message
        return line.replaceFirst(Regex("^\\[?\\d\\d:\\d\\d:\\d\\d\\]?.*?\\[ERROR\\]\\s*"), "")
            .replaceFirst(Regex("^\\[?\\d\\d:\\d\\d:\\d\\d\\]?.*?\\[/ERROR\\]\\s*"), "")
            .take(80)
    }
}
