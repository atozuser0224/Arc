package dev.arc.api.ops.audit

import org.bukkit.command.CommandSender
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tracks who ran dangerous Arc commands and when.
 *
 * `/arc audit [last <n>|since <hours>|search <player>]`
 *
 * In-memory ring buffer, persisted to `arc-ops/audit.log` on each write.
 * Logs: plugin reload, disable, enable, reload-chain apply, config set/reset,
 * safe-mode toggle, maintenance toggle, restart.
 */
object AuditLog {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val logFile = File("arc-ops/audit.log")
    private val entries = ConcurrentLinkedQueue<Entry>()
    private const val MAX_MEMORY = 500

    data class Entry(
        val timestamp: Long,
        val sender: String,
        val action: String,
        val details: String,
    ) {
        fun format(): String {
            val ts = tsFmt.format(Instant.ofEpochMilli(timestamp))
            return "[$ts] $sender: $action — $details"
        }
    }

    fun log(sender: CommandSender, action: String, details: String = "") {
        val entry = Entry(System.currentTimeMillis(), sender.name, action, details)
        entries.add(entry)
        while (entries.size > MAX_MEMORY) entries.poll()
        appendToFile(entry)
    }

    fun recent(n: Int = 20): List<Entry> = entries.toList().takeLast(n).reversed()

    fun since(hours: Int): List<Entry> {
        val cutoff = System.currentTimeMillis() - hours * 3600_000L
        return entries.filter { it.timestamp >= cutoff }.reversed()
    }

    fun byPlayer(name: String): List<Entry> =
        entries.filter { it.sender.equals(name, true) }.reversed()

    private fun appendToFile(entry: Entry) {
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText(entry.format() + "\n")
        }
    }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("last", "since", "search", "clear")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "since" -> listOf("1", "6", "12", "24", "168")
            "search" -> entries.map { it.sender }.distinct().take(20)
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "last" -> {
                val n = args.getOrNull(2)?.toIntOrNull() ?: 20
                val recs = recent(n)
                if (recs.isEmpty()) { sender.sendMessage("[Arc] audit log is empty"); return true }
                sender.sendMessage("[Arc] Audit (last $n):")
                recs.forEach { sender.sendMessage("  §7${it.format()}") }
            }
            "since" -> {
                val hours = args.getOrNull(2)?.toIntOrNull() ?: 24
                val recs = since(hours)
                sender.sendMessage("[Arc] Audit (${hours}h): ${recs.size} entries")
                recs.take(50).forEach { sender.sendMessage("  §7${it.format()}") }
            }
            "search" -> {
                val name = args.getOrNull(2) ?: run { sender.sendMessage("/arc audit search <player>"); return true }
                val recs = byPlayer(name)
                sender.sendMessage("[Arc] Audit for §e$name: ${recs.size} entries")
                recs.take(30).forEach { sender.sendMessage("  §7${it.format()}") }
            }
            "clear" -> {
                entries.clear()
                logFile.delete()
                sender.sendMessage("[Arc] Audit log cleared")
            }
            else -> { sender.sendMessage("/arc audit <last [n]|since <hours>|search <player>|clear>"); return true }
        }
        return true
    }
}
