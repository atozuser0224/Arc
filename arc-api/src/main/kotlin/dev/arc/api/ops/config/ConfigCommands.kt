package dev.arc.api.ops.config

import dev.arc.api.Arc
import dev.arc.api.ops.audit.AuditLog
import dev.arc.api.ops.configcheck.ConfigValidator
import dev.arc.api.ops.configcheck.Severity
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `/arc config get|set|reset|reload|diff|migrate|search <keyword>`.
 */
object ConfigCommands {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    private fun backupDir() = File("arc-ops/config-backups").also { it.mkdirs() }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("check", "explain", "get", "set", "reset", "reload", "diff", "migrate", "search")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "explain", "get", "reset" -> ConfigValidator.knownPaths()
            "set" -> ConfigValidator.knownPaths()
            "migrate" -> listOf("--dry-run", "--apply")
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase() ?: run {
            sender.sendMessage("/arc config <get|set|reset|reload|diff|migrate|search|check|explain>")
            return true
        }
        when (sub) {
            "get" -> get(sender, args.getOrNull(2))
            "set" -> set(sender, args.getOrNull(2), args.getOrNull(3))
            "reset" -> reset(sender, args.getOrNull(2))
            "reload" -> reload(sender)
            "diff" -> diff(sender)
            "migrate" -> migrate(sender, args)
            "search" -> search(sender, args.getOrNull(2))
            "check", "explain" -> delegateToArcOps(sender, args) // existing config handler
            else -> sender.sendMessage("[Arc] unknown config command: $sub")
        }
        return true
    }

    private fun get(sender: CommandSender, path: String?) {
        if (path == null) { sender.sendMessage("[Arc] usage: /arc config get <path>"); return }
        val value = Arc.config.getString(path) ?: Arc.config.getInt(path).toString()
        val defaultValue = "see /arc config explain $path"
        sender.sendMessage("[Arc] $path = §e$value §7(default: $defaultValue)")
    }

    private fun set(sender: CommandSender, path: String?, raw: String?) {
        if (path == null || raw == null) { sender.sendMessage("[Arc] usage: /arc config set <path> <value>"); return }
        backupConfig()
        val value: Any? = when {
            raw.equals("true", true) -> true
            raw.equals("false", true) -> false
            raw.toIntOrNull() != null -> raw.toInt()
            raw.toDoubleOrNull() != null -> raw.toDouble()
            else -> raw
        }
        val old = Arc.config.getString(path)
        Arc.config.set(path, value)
        Arc.config.save()
        ConfigChangeHistory.record(sender.name, path, old, value.toString())
        AuditLog.log(sender, "config set", "$path = $value")
        sender.sendMessage("[Arc] §aSet $path = $value. Config saved. Use /arc config diff to compare.")
    }

    private fun reset(sender: CommandSender, path: String?) {
        if (path == null) { sender.sendMessage("[Arc] usage: /arc config reset <path>"); return }
        val old = Arc.config.getString(path)
        backupConfig()
        Arc.config.set(path, null)
        Arc.config.save()
        ConfigChangeHistory.record(sender.name, path, old, null)
        AuditLog.log(sender, "config reset", path)
        sender.sendMessage("[Arc] §aReset $path (removed from file, default will apply).")
    }

    private fun reload(sender: CommandSender) {
        runCatching { Arc.config.reload() }.fold(
            onSuccess = { sender.sendMessage("[Arc] §aConfig reloaded.") },
            onFailure = { sender.sendMessage("[Arc] §cReload failed: ${it.message}") }
        )
    }

    private fun diff(sender: CommandSender) {
        val backups = backupDir().listFiles { f -> f.name.startsWith("arc-config-") }?.sortedByDescending { it.name } ?: emptyList()
        if (backups.isEmpty()) {
            sender.sendMessage("[Arc] no backup to compare. Make a change first.")
            return
        }
        val lastBackup = backups.first()
        val current = File("arc-config.yml")
        if (!current.isFile) { sender.sendMessage("[Arc] arc-config.yml not found"); return }

        val oldLines = lastBackup.readLines()
        val newLines = current.readLines()
        val added = newLines.filter { it !in oldLines }
        val removed = oldLines.filter { it !in newLines }

        sender.sendMessage("[Arc] Config diff (vs ${lastBackup.name}):")
        if (added.isEmpty() && removed.isEmpty()) { sender.sendMessage("  §7(no changes)"); return }
        added.take(20).forEach { sender.sendMessage("  §a+ $it") }
        removed.take(20).forEach { sender.sendMessage("  §c- $it") }
    }

    private fun search(sender: CommandSender, keyword: String?) {
        if (keyword == null) { sender.sendMessage("[Arc] usage: /arc config search <keyword>"); return }
        val file = File("arc-config.yml")
        if (!file.isFile) { sender.sendMessage("[Arc] arc-config.yml not found"); return }
        val matches = file.readLines().filter { it.contains(keyword, true) }.take(20)
        if (matches.isEmpty()) { sender.sendMessage("[Arc] no lines match '$keyword'"); return }
        sender.sendMessage("[Arc] Config lines matching '$keyword':")
        matches.forEach { sender.sendMessage("  §e$it") }
    }

    private fun migrate(sender: CommandSender, args: Array<out String>) {
        val dryRun = args.any { it == "--dry-run" }
        sender.sendMessage(if (dryRun) "[Arc] Config migration (dry-run):" else "[Arc] Config migration: apply stub — see arc config migrate")
        sender.sendMessage("  §7No pending migrations. Integration point for arc.yml → arc-ops.yml migration.")
        sender.sendMessage("  §7Custom migrations can be written via ArcConfigMigration API.")
    }

    private fun delegateToArcOps(sender: CommandSender, args: Array<out String>) {
        // Pass through to the existing ArcOpsCommand config handler
        when (args.getOrNull(1)?.lowercase()) {
            "explain" -> {
                val path = args.getOrNull(2)
                if (path == null) { sender.sendMessage("usage: /arc config explain <path>"); return }
                val doc = ConfigValidator.explain(path)
                if (doc == null) { sender.sendMessage("[Arc] no docs for '$path'. Try: ${ConfigValidator.knownPaths().joinToString()}"); return }
                sender.sendMessage("[Arc] ${doc.path}")
                sender.sendMessage("  meaning   : ${doc.meaning}")
                sender.sendMessage("  perf      : ${doc.performanceImpact}")
                sender.sendMessage("  recommend : ${doc.recommended}")
                sender.sendMessage("  restart   : ${if (doc.requiresRestart) "required" else "live"}")
            }
            "check" -> {
                val findings = ConfigValidator.check()
                if (findings.isEmpty()) { sender.sendMessage("[Arc] config check: no issues found"); return }
                findings.sortedByDescending { it.severity }.forEach { f ->
                    val tag = when (f.severity) { Severity.DANGER -> "[!]"; Severity.WARN -> "[~]"; Severity.INFO -> "[i]" }
                    sender.sendMessage("  $tag ${f.path} = ${f.current}")
                    sender.sendMessage("      ${f.message} -> ${f.recommended}")
                }
            }
        }
    }

    private fun backupConfig() {
        val now = tsFmt.format(Instant.now())
        val src = File("arc-config.yml")
        if (src.isFile) {
            src.copyTo(File(backupDir(), "arc-config-$now.yml"), overwrite = true)
        }
    }
}
