package dev.arc.api.ops

import dev.arc.api.command.command
import dev.arc.api.ops.async.ArcAsync
import dev.arc.api.ops.chunk.ChunkDiagnostics
import dev.arc.api.ops.configcheck.ConfigValidator
import dev.arc.api.ops.configcheck.Severity
import dev.arc.api.ops.doctor.ServerDoctor
import dev.arc.api.ops.plugincost.PluginCostTracker
import dev.arc.api.ops.profiler.MainThreadProfiler
import org.bukkit.Bukkit
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
            usage = "/leaf <doctor|lagspike|plugin-cost|config|chunks|entity|profiler|pregen|memory|ping|status|reload>"

            executes { sender, args -> dispatch(plugin, sender, args); true }

            completes { _, args ->
                when (args.size) {
                    1 -> listOf("doctor", "lagspike", "plugin-cost", "config", "chunks", "entity",
                        "profiler", "pregen", "memory", "ping", "status", "reload")
                    2 -> when (args[0].lowercase()) {
                        "lagspike" -> listOf("list", "last")
                        "plugin-cost" -> listOf("top")
                        "config" -> listOf("check", "explain")
                        "chunks" -> listOf("report", "tickets", "backlog")
                        "entity" -> listOf("stats")
                        "profiler" -> listOf("start", "stop", "report")
                        "pregen" -> listOf("start", "status", "cancel")
                        else -> emptyList()
                    }
                    3 -> when {
                        args[0].equals("config", true) && args[1].equals("explain", true) -> ConfigValidator.knownPaths()
                        args[0].equals("pregen", true) && args[1].equals("start", true) -> Bukkit.getWorlds().map { it.name }
                        else -> emptyList()
                    }
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
            "profiler" -> profiler(sender, args)
            "pregen" -> pregen(sender, args)
            "memory" -> memory(sender)
            "ping" -> ping(sender)
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
        if (opt != null) {
            val s = opt.stats
            sender.sendMessage("[Leaf] entity AI optimizer:")
            sender.sendMessage("  awake=${s.awake} throttled=${s.throttled} protected(targeting)=${s.protectedTargeting}")
            val total = s.awake + s.throttled
            if (total > 0) sender.sendMessage("  throttle ratio: ${"%.0f".format(s.throttled.toDouble() / total * 100)}%")
        } else {
            sender.sendMessage("[Leaf] AI optimizer OFF (enable entity-optimization in leaf-ops.yml)")
        }
        val guard = LeafOps.densityOptimizer
        if (guard != null) {
            val g = guard.stats
            sender.sendMessage("[Leaf] density guard: active=${g.lastActive} culled(lastRun)=${g.culledThisRun} culled(total)=${g.culledTotal}")
        } else {
            sender.sendMessage("[Leaf] density guard OFF (enable entity-density-guard in leaf-ops.yml)")
        }
    }

    private fun profiler(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "start" -> {
                val interval = args.getOrNull(2)?.toLongOrNull() ?: 10L
                if (MainThreadProfiler.start(interval)) sender.sendMessage("[Leaf] profiler started (interval=${interval}ms). Stop with /leaf profiler stop")
                else sender.sendMessage("[Leaf] profiler already running")
            }
            "stop" -> { MainThreadProfiler.stop(); sender.sendMessage("[Leaf] profiler stopped. /leaf profiler report") }
            else -> MainThreadProfiler.report(25).lineSequence().forEach { sender.sendMessage(it) }
        }
    }

    private fun pregen(sender: CommandSender, args: Array<out String>) {
        val pre = LeafOps.pregenerator
        when (args.getOrNull(1)?.lowercase()) {
            "cancel" -> sender.sendMessage(if (pre.cancel()) "[Leaf] pregen cancelled" else "[Leaf] no pregen running")
            "status" -> {
                val p = pre.progress()
                if (p == null) sender.sendMessage("[Leaf] no pregen running")
                else sender.sendMessage("[Leaf] pregen ${p.third}: ${p.first}/${p.second} chunks (${"%.1f".format(p.first.toDouble() / p.second * 100)}%)")
            }
            "start" -> {
                val world = args.getOrNull(2)?.let { Bukkit.getWorld(it) }
                val radius = args.getOrNull(3)?.toIntOrNull()
                if (world == null || radius == null) { sender.sendMessage("usage: /leaf pregen start <world> <radiusChunks> [cx cz]"); return }
                val cx = args.getOrNull(4)?.toIntOrNull() ?: world.spawnLocation.blockX
                val cz = args.getOrNull(5)?.toIntOrNull() ?: world.spawnLocation.blockZ
                val started = pre.start(
                    world, radius, cx, cz,
                    onProgress = { done, total -> sender.sendMessage("[Leaf] pregen ${world.name}: $done/$total") },
                    onComplete = { sender.sendMessage("[Leaf] pregen ${world.name} complete") },
                )
                sender.sendMessage(if (started) "[Leaf] pregen started: ${world.name} radius=$radius (${(2 * radius + 1) * (2 * radius + 1)} chunks)"
                else "[Leaf] a pregen job is already running")
            }
            else -> sender.sendMessage("/leaf pregen <start <world> <radius> [cx cz]|status|cancel>")
        }
    }

    private fun memory(sender: CommandSender) {
        val g = LeafOps.memory
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)
        sender.sendMessage("[Leaf] heap: $used / $max MB (${"%.0f".format(used.toDouble() / max * 100)}%)")
        if (g != null) sender.sendMessage("  guard state: ${g.lastState} (warn>=${"%.0f".format(LeafOps.config.memoryWarnFraction * 100)}% crit>=${"%.0f".format(LeafOps.config.memoryCriticalFraction * 100)}%)")
        else sender.sendMessage("  memory guard: OFF")
    }

    private fun ping(sender: CommandSender) {
        val players = Bukkit.getOnlinePlayers().sortedByDescending { it.ping }
        if (players.isEmpty()) { sender.sendMessage("[Leaf] no players online"); return }
        val avg = players.map { it.ping }.average()
        sender.sendMessage("[Leaf] ping — avg ${"%.0f".format(avg)}ms, ${players.size} player(s):")
        players.take(10).forEach { sender.sendMessage("  ${it.name}: ${it.ping}ms") }
    }

    private fun status(sender: CommandSender) {
        val cfg = LeafOps.config
        sender.sendMessage("[Leaf] status server: ${if (cfg.statusEnabled) "ON http://${cfg.statusBindAddress}:${cfg.statusPort}" else "OFF"}")
        if (cfg.statusEnabled) sender.sendMessage("  endpoints: /leaf/status /leaf/worlds /leaf/performance /metrics")
    }
}
