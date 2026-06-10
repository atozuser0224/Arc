package dev.arc.api.ops.crash

import org.bukkit.Bukkit
import java.io.File

data class CrashAnalysis(
    val source: String,
    val occurredAt: String?,
    val exceptionType: String?,
    val suspectPlugins: List<String>,
    val suspectWorlds: List<String>,
    val suspectEntities: List<String>,
    val tickPhase: String?,
    val headLines: List<String>,
) {
    val isEmpty: Boolean get() = exceptionType == null && suspectPlugins.isEmpty() && headLines.isEmpty()

    fun render(): String = buildString {
        appendLine("===== Leaf Crash Analyze =====")
        appendLine("source     : $source")
        appendLine("occurred   : ${occurredAt ?: "unknown"}")
        appendLine("exception  : ${exceptionType ?: "none found"}")
        appendLine("tick phase : ${tickPhase ?: "unknown"}")
        appendLine("suspect plugins  : ${suspectPlugins.ifEmpty { listOf("none identified") }.joinToString(", ")}")
        appendLine("suspect worlds   : ${suspectWorlds.ifEmpty { listOf("none") }.joinToString(", ")}")
        appendLine("suspect entities : ${suspectEntities.ifEmpty { listOf("none") }.joinToString(", ")}")
        if (headLines.isNotEmpty()) {
            appendLine("--- excerpt ---")
            headLines.forEach { appendLine("  $it") }
        }
        append("note: attribution is heuristic (stack-trace package + keyword scan), verify before acting")
    }
}

/**
 * Post-mortem analyzer. On boot (and on `/leaf crash analyze`) it inspects the
 * most recent crash report and `logs/latest.log`, then surfaces likely culprits
 * by scanning the stack trace for plugin packages and the surrounding text for
 * world / entity / tick-phase markers.
 *
 * This is deliberately heuristic: a plugin appearing in a stack frame is a
 * *suspect*, not a verdict — the real fault can be a server bug the plugin
 * merely triggered. Output is worded accordingly.
 */
object CrashAnalyzer {

    private val knownPhases = listOf(
        "ticking entity", "ticking block entity", "ticking world", "ticking player",
        "tile entity being ticked", "entity being ticked", "chunk loading", "chunk saving",
        "generating chunk", "server tick", "scheduled task",
    )

    fun analyzeLatest(): CrashAnalysis? {
        val crashDir = File(Bukkit.getWorldContainer(), "crash-reports")
        val latestCrash = crashDir.takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }

        if (latestCrash != null && isRecent(latestCrash)) {
            return analyzeFile(latestCrash, fromCrashReport = true)
        }

        val latestLog = File(Bukkit.getWorldContainer(), "logs/latest.log")
        if (latestLog.isFile && containsFatal(latestLog)) {
            return analyzeFile(latestLog, fromCrashReport = false)
        }
        return null
    }

    fun analyzeFile(file: File, fromCrashReport: Boolean): CrashAnalysis {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val pluginNames = Bukkit.getPluginManager().plugins.map { it.name }
        val worldNames = Bukkit.getWorlds().map { it.name }

        val suspectPlugins = LinkedHashSet<String>()
        val suspectWorlds = LinkedHashSet<String>()
        val suspectEntities = LinkedHashSet<String>()
        var exceptionType: String? = null
        var phase: String? = null
        var occurredAt: String? = null
        val head = ArrayList<String>()

        for ((i, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (occurredAt == null && (line.startsWith("Time:") || line.contains("ERROR]:") || line.contains("FATAL"))) {
                occurredAt = line.take(120)
            }
            if (exceptionType == null) {
                val m = EXC.find(line)
                if (m != null) exceptionType = m.value
            }
            if (phase == null) {
                val p = knownPhases.firstOrNull { line.contains(it, ignoreCase = true) }
                if (p != null) phase = p
            }
            for (p in pluginNames) if (line.contains(p)) suspectPlugins += p
            for (w in worldNames) if (line.contains("world=$w") || line.contains("[$w]") || line.contains("dimension: $w")) suspectWorlds += w
            val ent = ENTITY.find(line)?.groupValues?.getOrNull(1)
            if (ent != null) suspectEntities += ent
            if (head.size < 25 && (line.startsWith("Caused by") || line.startsWith("at ") || EXC.containsMatchIn(line) || line.startsWith("Description:"))) {
                head += line.take(160)
            }
            if (i > 4000) break // bound the scan
        }

        return CrashAnalysis(
            source = file.path,
            occurredAt = occurredAt,
            exceptionType = exceptionType,
            suspectPlugins = suspectPlugins.toList(),
            suspectWorlds = suspectWorlds.toList(),
            suspectEntities = suspectEntities.toList(),
            tickPhase = phase,
            headLines = head,
        )
    }

    private fun isRecent(file: File): Boolean =
        System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60 * 1000L

    private fun containsFatal(file: File): Boolean = runCatching {
        file.bufferedReader().useLines { seq ->
            seq.take(8000).any { it.contains("Exception in server tick loop") || it.contains("This crash report has been saved") || it.contains("/FATAL]") }
        }
    }.getOrDefault(false)

    private val EXC = Regex("""[A-Za-z0-9_.]+(?:Exception|Error)(?:: .{0,80})?""")
    private val ENTITY = Regex("""(?:EntityType|entity type|minecraft:)[ =:]*([A-Za-z_]+)""")
}
