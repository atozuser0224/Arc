package dev.arc.api.ops.snapshot

import dev.arc.api.ops.metrics.SnapshotService
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32

/**
 * `/arc snapshot create|list|compare <id> <id>`.
 * Captures server state for later comparison.
 */
object SnapshotCommands {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    private val readableFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    data class StoredSnapshot(
        val id: String,
        val timestamp: Long,
        val plugins: List<Pair<String, String>>, // name, version
        val configHash: Long,
        val worlds: List<WorldSummary>,
    )

    data class WorldSummary(val name: String, val entities: Int, val chunks: Int, val players: Int)

    private fun dir() = File("arc-ops/snapshots").also { it.mkdirs() }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("create", "list", "compare")
        3 -> if (args.getOrNull(1)?.lowercase() == "compare") listSnapshots().map { it.id }
        else emptyList()
        4 -> if (args.getOrNull(1)?.lowercase() == "compare") listSnapshots().map { it.id }
        else emptyList()
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "create" -> create(sender)
            "list" -> list(sender)
            "compare" -> compare(sender, args.getOrNull(2), args.getOrNull(3))
            else -> { sender.sendMessage("/arc snapshot <create|list|compare <id> <id>>"); return true }
        }
        return true
    }

    private fun create(sender: CommandSender) {
        val snap = SnapshotService.latest
        val id = tsFmt.format(Instant.now())
        val plugins = Bukkit.getPluginManager().plugins.map { it.name to (it.description.version ?: "?") }.sortedBy { it.first }
        val worlds = snap.worlds.map { WorldSummary(it.name, it.entities, it.loadedChunks, it.players) }
        val configHash = computeConfigHash()

        val stored = StoredSnapshot(id, System.currentTimeMillis(), plugins, configHash, worlds)
        saveSnapshot(stored)
        sender.sendMessage("[Arc] Snapshot §e$id §acreated: ${plugins.size} plugins, ${worlds.size} worlds, ${worlds.sumOf { it.entities }} entities")
    }

    private fun list(sender: CommandSender) {
        val snaps = listSnapshots()
        if (snaps.isEmpty()) { sender.sendMessage("[Arc] no snapshots. Create one with /arc snapshot create"); return }
        sender.sendMessage("[Arc] Snapshots (${snaps.size}):")
        snaps.forEach { s ->
            val ts = readableFmt.format(Instant.ofEpochMilli(s.timestamp))
            sender.sendMessage("  §e${s.id} §7$ts plugins=${s.plugins.size} entities=${s.worlds.sumOf { it.entities }}")
        }
    }

    private fun compare(sender: CommandSender, a: String?, b: String?) {
        if (a == null || b == null) { sender.sendMessage("/arc snapshot compare <id1> <id2>"); return }
        val snaps = listSnapshots()
        val s1 = snaps.find { it.id == a }
        val s2 = snaps.find { it.id == b }
        if (s1 == null) { sender.sendMessage("[Arc] snapshot not found: $a"); return }
        if (s2 == null) { sender.sendMessage("[Arc] snapshot not found: $b"); return }

        val t1 = readableFmt.format(Instant.ofEpochMilli(s1.timestamp))
        val t2 = readableFmt.format(Instant.ofEpochMilli(s2.timestamp))

        sender.sendMessage("[Arc] Snapshot diff: §e$s1.id §7($t1) vs §e$s2.id §7($t2):")

        // Plugin changes
        val p1 = s1.plugins.toMap()
        val p2 = s2.plugins.toMap()
        val added = p2.keys - p1.keys
        val removed = p1.keys - p2.keys
        val changed = p1.keys.intersect(p2.keys).filter { p1[it] != p2[it] }
        if (added.isNotEmpty()) sender.sendMessage("  §a+ plugins: ${added.joinToString()}")
        if (removed.isNotEmpty()) sender.sendMessage("  §c- plugins: ${removed.joinToString()}")
        if (changed.isNotEmpty()) sender.sendMessage("  §e~ plugins: ${changed.joinToString { "$it: ${p1[it]}→${p2[it]}" }}")

        // World changes
        val w1 = s1.worlds.associateBy { it.name }
        val w2 = s2.worlds.associateBy { it.name }
        val allWorlds = w1.keys + w2.keys
        for (name in allWorlds.sorted()) {
            val before = w1[name]
            val after = w2[name]
            if (before == null) { sender.sendMessage("  §a+ world ${name}: ${after!!.entities} entities"); continue }
            if (after == null) { sender.sendMessage("  §c- world $name"); continue }
            if (before.entities != after.entities || before.chunks != after.chunks) {
                val eDiff = after.entities - before.entities
                val cDiff = after.chunks - before.chunks
                sender.sendMessage("  §e$name: entities ${before.entities}→${after.entities} (${if(eDiff>=0)"+" else ""}$eDiff) chunks ${before.chunks}→${after.chunks} (${if(cDiff>=0)"+" else ""}$cDiff)")
            }
        }

        if (s1.configHash != s2.configHash) sender.sendMessage("  §eConfig changed (hash: ${s1.configHash}→${s2.configHash})")
    }

    private fun saveSnapshot(snap: StoredSnapshot) {
        val f = File(dir(), "${snap.id}.txt")
        val sb = StringBuilder()
        sb.appendLine("id: ${snap.id}")
        sb.appendLine("timestamp: ${snap.timestamp}")
        sb.appendLine("config-hash: ${snap.configHash}")
        sb.appendLine("plugins:")
        snap.plugins.forEach { sb.appendLine("  ${it.first}: ${it.second}") }
        sb.appendLine("worlds:")
        snap.worlds.forEach { sb.appendLine("  ${it.name}: entities=${it.entities} chunks=${it.chunks} players=${it.players}") }
        f.writeText(sb.toString())
    }

    private fun listSnapshots(): List<StoredSnapshot> {
        return dir().listFiles { f -> f.extension == "txt" }?.sortedByDescending { it.name }?.mapNotNull { parseSnapshot(it) } ?: emptyList()
    }

    private fun parseSnapshot(f: File): StoredSnapshot? {
        return runCatching {
            val lines = f.readLines()
            var id = ""
            var ts = 0L
            var configHash = 0L
            val plugins = ArrayList<Pair<String, String>>()
            val worlds = ArrayList<WorldSummary>()
            var section = ""
            for (line in lines) {
                when {
                    line.startsWith("id: ") -> id = line.removePrefix("id: ")
                    line.startsWith("timestamp: ") -> ts = line.removePrefix("timestamp: ").toLong()
                    line.startsWith("config-hash: ") -> configHash = line.removePrefix("config-hash: ").toLong()
                    line.startsWith("plugins:") -> section = "plugins"
                    line.startsWith("worlds:") -> section = "worlds"
                    line.startsWith("  ") -> when (section) {
                        "plugins" -> {
                            val parts = line.trim().split(": ", limit = 2)
                            if (parts.size == 2) plugins += parts[0] to parts[1]
                        }
                        "worlds" -> {
                            val name = line.trim().split(":").first()
                            val entities = Regex("entities=(\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: 0
                            val chunks = Regex("chunks=(\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: 0
                            val players = Regex("players=(\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: 0
                            worlds += WorldSummary(name, entities, chunks, players)
                        }
                    }
                }
            }
            StoredSnapshot(id, ts, plugins, configHash, worlds)
        }.getOrNull()
    }

    private fun computeConfigHash(): Long {
        val crc = CRC32()
        val files = listOf(File("arc-config.yml"), File("arc-ops/arc-ops.yml"),
            File("server.properties"), File("bukkit.yml"), File("spigot.yml"), File("paper/paper.yml"))
        for (f in files) {
            if (f.isFile) crc.update(f.readBytes())
        }
        return crc.value
    }
}
