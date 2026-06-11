package dev.arc.api.ops.config

import org.bukkit.command.CommandSender
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tracks every config change made via `/arc config set|reset|migrate`.
 *
 * `/arc config-history [list|diff <id>|search <path>]`
 *
 * Each change saved to `arc-ops/config-history/` as timestamped copies.
 */
object ConfigChangeHistory {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    private val readableFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    data class ChangeEntry(
        val id: String,
        val timestamp: Long,
        val who: String,
        val path: String,
        val oldValue: String?,
        val newValue: String?,
    )

    private fun dir() = File("arc-ops/config-history").also { it.mkdirs() }

    /** Record a config change. Called by ConfigCommands after set/reset. */
    fun record(who: String, path: String, oldValue: String?, newValue: String?) {
        val id = tsFmt.format(Instant.now())
        val entry = ChangeEntry(id, System.currentTimeMillis(), who, path, oldValue, newValue)
        File(dir(), "${id}.json").writeText(buildString {
            appendLine("{")
            appendLine("  \"id\": \"${entry.id}\",")
            appendLine("  \"timestamp\": ${entry.timestamp},")
            appendLine("  \"who\": \"${entry.who}\",")
            appendLine("  \"path\": \"${entry.path}\",")
            appendLine("  \"oldValue\": ${entry.oldValue?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"newValue\": ${entry.newValue?.let { "\"$it\"" } ?: "null"}")
            appendLine("}")
        })
        // Backup config
        File("arc-config.yml").copyTo(File(dir(), "${id}_arc-config.yml"), overwrite = true)
    }

    fun listEntries(): List<ChangeEntry> {
        return dir().listFiles { f -> f.extension == "json" }?.sortedByDescending { it.name }
            ?.mapNotNull { parseEntry(it) } ?: emptyList()
    }

    private fun parseEntry(f: File): ChangeEntry? = runCatching {
        val text = f.readText()
        fun jsonField(key: String): String? {
            val pattern = "\"$key\":\\s*\"?([^\",}\\n]+)\"?".toRegex()
            return pattern.find(text)?.groupValues?.get(1)
        }
        ChangeEntry(
            id = jsonField("id") ?: f.nameWithoutExtension,
            timestamp = jsonField("timestamp")?.toLongOrNull() ?: 0L,
            who = jsonField("who") ?: "?",
            path = jsonField("path") ?: "?",
            oldValue = jsonField("oldValue"),
            newValue = jsonField("newValue"),
        )
    }.getOrNull()

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("list", "diff", "search")
        3 -> listOf(/* entry ids populated at runtime */)
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "list" -> {
                val entries = listEntries()
                if (entries.isEmpty()) { sender.sendMessage("[Arc] no config history yet"); return true }
                sender.sendMessage("[Arc] Config change history (${entries.size}):")
                entries.take(30).forEach { e ->
                    val ts = readableFmt.format(Instant.ofEpochMilli(e.timestamp))
                    val change = if (e.oldValue == null) "RESET" else "${e.oldValue} → ${e.newValue}"
                    sender.sendMessage("  §e${e.id} §7$ts by ${e.who}: ${e.path} = $change")
                }
            }
            "diff" -> {
                val id = args.getOrNull(2) ?: run { sender.sendMessage("/arc config-history diff <id>"); return true }
                val backup = File(dir(), "${id}_arc-config.yml")
                val current = File("arc-config.yml")
                if (!backup.isFile) { sender.sendMessage("[Arc] no backup for id: $id"); return true }
                val oldLines = backup.readLines()
                val newLines = current.readLines()
                sender.sendMessage("[Arc] Config diff (vs $id):")
                val added = newLines.filter { it !in oldLines }
                val removed = oldLines.filter { it !in newLines }
                added.take(20).forEach { sender.sendMessage("  §a+ $it") }
                removed.take(20).forEach { sender.sendMessage("  §c- $it") }
            }
            "search" -> {
                val keyword = args.getOrNull(2) ?: run { sender.sendMessage("/arc config-history search <path-keyword>"); return true }
                val entries = listEntries().filter { it.path.contains(keyword, true) }
                sender.sendMessage("[Arc] History for '$keyword': ${entries.size} changes")
                entries.take(20).forEach { e ->
                    val ts = readableFmt.format(Instant.ofEpochMilli(e.timestamp))
                    sender.sendMessage("  §7$ts by ${e.who}: ${e.path}")
                }
            }
            else -> { sender.sendMessage("/arc config-history <list|diff <id>|search <path>>"); return true }
        }
        return true
    }
}
