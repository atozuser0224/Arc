package dev.arc.api.ops.startup

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Measures what takes time during server boot and reports it.
 *
 * `/arc startup-profile [last|detail <plugin>]`
 *
 * Hooks: called by ArcBootstrap.install() and ArcOps.install() to mark phases.
 * Records per-plugin load time + onEnable time by wrapping the plugin manager.
 */
object StartupProfile {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val profileFile = File("arc-ops/startup-profile.txt")

    data class Phase(val name: String, val startMs: Long, val endMs: Long, val durationMs: Long)
    data class PluginLoad(val name: String, val version: String, val loadMs: Long, val enableMs: Long, val totalMs: Long)

    private var startTime = 0L
    private val phases = LinkedHashMap<String, Phase>()
    private val pluginLoads = LinkedHashMap<String, PluginLoad>()
    private var currentPhase: String? = null
    private var phaseStart = 0L

    fun init() {
        startTime = System.currentTimeMillis()
        phases.clear()
        pluginLoads.clear()
    }

    fun beginPhase(name: String) {
        currentPhase = name
        phaseStart = System.currentTimeMillis()
    }

    fun endPhase(name: String) {
        val now = System.currentTimeMillis()
        phases[name] = Phase(name, phaseStart, now, now - phaseStart)
        currentPhase = null
    }

    fun recordPlugin(plugin: Plugin, loadMs: Long, enableMs: Long) {
        pluginLoads[plugin.name] = PluginLoad(
            name = plugin.name,
            version = plugin.description.version ?: "?",
            loadMs = loadMs,
            enableMs = enableMs,
            totalMs = loadMs + enableMs,
        )
    }

    fun report(): String = buildString {
        val total = System.currentTimeMillis() - startTime
        appendLine("===== Arc Startup Profile =====")
        appendLine("total boot time: ${total}ms (${"%.1f".format(total / 1000.0)}s)")
        appendLine("")

        if (phases.isNotEmpty()) {
            appendLine("[Boot Phases]")
            phases.values.sortedBy { it.startMs }.forEach { p ->
                val pct = if (total > 0) "%.0f%%".format(p.durationMs.toDouble() / total * 100) else ""
                appendLine("  ${p.name}: ${p.durationMs}ms $pct")
            }
            appendLine("")
        }

        if (pluginLoads.isNotEmpty()) {
            appendLine("[Plugin Load Times] (sorted by total)")
            pluginLoads.values.sortedByDescending { it.totalMs }.take(15).forEach { p ->
                appendLine("  ${p.name} v${p.version}: load=${p.loadMs}ms enable=${p.enableMs}ms total=${p.totalMs}ms")
            }
            if (pluginLoads.size > 15) appendLine("  ... and ${pluginLoads.size - 15} more")
        }

        appendLine("")
        appendLine("[World Preload]")
        Bukkit.getWorlds().forEach { w ->
            appendLine("  ${w.name}: ${w.loadedChunks.size} chunks loaded")
        }
    }

    fun save() {
        runCatching {
            profileFile.parentFile?.mkdirs()
            profileFile.writeText(report())
        }
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.getOrNull(1)?.lowercase()) {
            "detail" -> {
                val name = args.getOrNull(2)
                if (name == null) { sender.sendMessage("/arc startup-profile detail <plugin>"); return true }
                val p = pluginLoads[name]
                if (p == null) { sender.sendMessage("[Arc] no profile data for $name"); return true }
                sender.sendMessage("[Arc] §e${p.name} v${p.version}:")
                sender.sendMessage("  load  : ${p.loadMs}ms")
                sender.sendMessage("  enable: ${p.enableMs}ms")
                sender.sendMessage("  total : ${p.totalMs}ms")
            }
            else -> {
                sender.sendMessage(report())
            }
        }
        return true
    }
}
