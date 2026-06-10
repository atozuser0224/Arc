package dev.arc.api.ops

import dev.arc.api.command.command
import dev.arc.api.ops.async.ArcAsync
import dev.arc.api.ops.chunk.ChunkDiagnostics
import dev.arc.api.ops.configcheck.ConfigValidator
import dev.arc.api.ops.configcheck.Severity
import dev.arc.api.ops.doctor.ServerDoctor
import dev.arc.api.ops.plugincost.PluginCostTracker
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Registers the `/leaf` operator command. Permission: `leaf.admin`.
 *
 * ```
 * /leaf doctor [--world <w>] [--paste]
 * /leaf lagspike [list|last]
 * /leaf plugin-cost top | <plugin>
 * /leaf config check | explain <path>
 * /leaf chunks report|tickets|backlog [world]
 * /leaf entity stats
 * /leaf status
 * /leaf reload
 * ```
 */
object LeafOpsCommand {

    fun register(plugin: Plugin) {
        command("leaf", plugin.name) {
            description = "Leaf operations & diagnostics"
            permission = "leaf.admin"
            usage = "/leaf <doctor|lagspike|plugin-cost|config|chunks|entity|status|reload>"

            executes { sender, args -> dispatch(plugin, sender, args); true }

            completes { _, args ->
                when (args.size) {
                    1 -> listOf("doctor", "lagspike", "plugin-cost", "config", "chunks", "entity", "status", "reload")
                    2 -> when (args[0].lowercase()) {
                        "lagspike" -> listOf("list", "last")
                        "plugin-cost" -> listOf("top")
                        "config" -> listOf("check", "explain")
                        "chunks" -> listOf("report", "tickets", "backlog")
                        "entity" -> listOf("stats")
                        else -> emptyList()
                    }
                    3 -> if (args[0].equals("config", true) && args[1].equals("explain", true)) ConfigValidator.knownPaths() else emptyList()
                    else -> emptyList()
                }
            }
        }
    }

    private fun dispatch(plugin: Plugin, sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "doctor" -> doctor(plugin, sender, args)
            "lagspike" -> lagspike(sender, args)
            "plugin-cost", "plugincost" -> pluginCost(sender, args)
            "config" -> config(sender, args)
            "chunks" -> chunks(sender, args)
            "entity" -> entity(sender)
            "status" -> status(sender)
            "reload" -> {
                runCatching { LeafOps.reload() }.fold(
                    onSuccess = { sender.sendMessage("[Leaf] config reloaded") },
                    onFailure = { sender.sendMessage("[Leaf] reload failed: ${it.message}") },
                )
            }
            else -> sender.sendMessage("/leaf <doctor|lagspike|plugin-cost|config|chunks|entity|status|reload>")
        }
    }

    private fun flag(args: Array<out String>, name: String): Boolean = args.any { it.equals("--$name", true) }
    private fun option(args: Array<out String>, name: String): String? {
        val i = args.indexOfFirst { it.equals("--$name", true) }
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    private fun doctor(plugin: Plugin, sender: CommandSender, args: Array<out String>) {
        val world = option(args, "world")
        val report = ServerDoctor.report(world, LeafOps.lagSpikeMonitor)
        if (flag(args, "paste")) {
            sender.sendMessage("[Leaf] uploading report…")
            ArcAsync.runBlockingIO {
                PasteUploader.upload(report, File(LeafOps.dataDir, "pastes"))
            }.thenSync { url -> sender.sendMessage("[Leaf] report: $url") }
        } else {
            report.lineSequence().forEach { sender.sendMessage(it) }
        }
    }

    private fun lagspike(sender: CommandSender, args: Array<out String>) {
        val monitor = LeafOps.lagSpikeMonitor
        if (monitor == null) { sender.sendMessage("[Leaf] lag-spike monitor not active"); return }
        val reports = monitor.recentReports()
        when (args.getOrNull(1)?.lowercase()) {
            "last" -> reports.firstOrNull()?.render()?.lineSequence()?.forEach { sender.sendMessage(it) }
                ?: sender.sendMessage("[Leaf] no lag spikes captured yet")
            else -> {
                sender.sendMessage("[Leaf] ${reports.size} recent lag spike(s):")
                reports.take(10).forEachIndexed { i, r ->
                    sender.sendMessage("  #$i ${"%.0f".format(r.msptAtTrigger)}ms world=${r.worstWorld} gc=${r.gcDuringCapture}")
                }
            }
        }
    }

    private fun pluginCost(sender: CommandSender, args: Array<out String>) {
        val arg = args.getOrNull(1)
        if (arg != null && !arg.equals("top", true)) {
            val cost = PluginCostTracker.forPlugin(arg)
            if (cost == null) { sender.sendMessage("[Leaf] no data for plugin '$arg'"); return }
            sender.sendMessage("[Leaf] ${cost.plugin}: avg=${"%.3f".format(cost.avgMs)}ms max=${"%.1f".format(cost.maxMs)}ms " +
                "samples=${cost.samples} pendingTasks=${cost.pendingTasks} listeners=${cost.activeListeners} " +
                "(${if (cost.measured) "measured" else "heuristic"})")
            return
        }
        sender.sendMessage("[Leaf] plugin cost (top):")
        PluginCostTracker.top(10).forEach {
            sender.sendMessage("  ${it.plugin}: ${if (it.measured) "${"%.3f".format(it.avgMs)}ms avg" else "~heuristic"} " +
                "tasks=${it.pendingTasks} listeners=${it.activeListeners}")
        }
        sender.sendMessage("  (time shown only for instrumented paths; counts always measured)")
    }

    private fun config(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "explain" -> {
                val path = args.getOrNull(2)
                if (path == null) { sender.sendMessage("usage: /leaf config explain <path>"); return }
                val doc = ConfigValidator.explain(path)
                if (doc == null) { sender.sendMessage("[Leaf] no docs for '$path'. Known: ${ConfigValidator.knownPaths().joinToString()}"); return }
                sender.sendMessage("[Leaf] ${doc.path}")
                sender.sendMessage("  meaning   : ${doc.meaning}")
                sender.sendMessage("  perf      : ${doc.performanceImpact}")
                sender.sendMessage("  recommend : ${doc.recommended}")
                sender.sendMessage("  restart   : ${if (doc.requiresRestart) "required" else "live"}")
            }
            else -> {
                val findings = ConfigValidator.check()
                if (findings.isEmpty()) { sender.sendMessage("[Leaf] config check: no issues found"); return }
                sender.sendMessage("[Leaf] config check — ${findings.size} finding(s):")
                findings.sortedByDescending { it.severity }.forEach {
                    val tag = when (it.severity) { Severity.DANGER -> "[!]"; Severity.WARN -> "[~]"; Severity.INFO -> "[i]" }
                    sender.sendMessage("  $tag ${it.path} = ${it.current}")
                    sender.sendMessage("      ${it.message}")
                    sender.sendMessage("      -> ${it.recommended}")
                }
            }
        }
    }

    private fun chunks(sender: CommandSender, args: Array<out String>) {
        val world = args.getOrNull(2)
        val out = when (args.getOrNull(1)?.lowercase()) {
            "tickets" -> ChunkDiagnostics.tickets(world)
            "backlog" -> ChunkDiagnostics.backlog()
            else -> ChunkDiagnostics.report(world)
        }
        out.lineSequence().forEach { sender.sendMessage(it) }
    }

    private fun entity(sender: CommandSender) {
        val opt = LeafOps.aiOptimizer
        if (opt == null) {
            sender.sendMessage("[Leaf] entity AI optimizer is OFF (enable entity-optimization in leaf-ops.yml)")
            return
        }
        val s = opt.stats
        sender.sendMessage("[Leaf] entity AI optimizer:")
        sender.sendMessage("  awake=${s.awake} throttled=${s.throttled} protected(targeting)=${s.protectedTargeting}")
        val total = s.awake + s.throttled
        if (total > 0) sender.sendMessage("  throttle ratio: ${"%.0f".format(s.throttled.toDouble() / total * 100)}%")
    }

    private fun status(sender: CommandSender) {
        val cfg = LeafOps.config
        sender.sendMessage("[Leaf] status server: ${if (cfg.statusEnabled) "ON http://${cfg.statusBindAddress}:${cfg.statusPort}" else "OFF"}")
        if (cfg.statusEnabled) sender.sendMessage("  endpoints: /leaf/status /leaf/worlds /leaf/performance /metrics")
    }
}
