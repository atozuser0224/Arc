package dev.arc.api.ops

import dev.arc.api.ops.async.ArcAsync
import dev.arc.api.ops.chunk.ChunkDiagnostics
import dev.arc.api.ops.configcheck.ConfigValidator
import dev.arc.api.ops.configcheck.Severity
import dev.arc.api.ops.doctor.ServerDoctor
import dev.arc.api.ops.plugincost.PluginCostTracker
import dev.arc.api.ops.profiler.MainThreadProfiler
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File

/**
 * Operations & diagnostics subcommands of the `/arc` admin command
 * (registered by [dev.arc.api.control.ArcControlCommand]). Permission: `arc.admin`.
 *
 * ```
 * /arc doctor [--world <w>] [--paste]
 * /arc lagspike [list|last]
 * /arc plugin-cost top | <plugin>
 * /arc config check | explain <path>
 * /arc chunks report|tickets|backlog [world]
 * /arc entity stats
 * /arc profiler start|stop|report
 * /arc pregen start <world> <radius> | status | cancel
 * /arc memory | ping | status
 * ```
 *
 * `/arc reload` (which also reloads ops config) is owned by ArcControlCommand.
 */
object ArcOpsCommand {

    /** Top-level ops subcommands, exposed so the `/arc` command can merge them in. */
    val roots: List<String> = listOf(
        "doctor", "lagspike", "plugin-cost", "config", "chunks", "entity",
        "profiler", "pregen", "memory", "ping", "status",
    )

    private val rootSet: Set<String> = (roots + listOf("lagspikes", "plugincost")).toHashSet()

    /** Tab-completion for ops subcommands (args[0] is the subcommand). */
    fun complete(args: Array<out String>): List<String> = when (args.size) {
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

    /**
     * Handles an ops subcommand. Returns `false` if [args] is not an ops
     * subcommand (so the caller can fall through to its own usage message).
     */
    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(0)?.lowercase() ?: return false
        if (sub !in rootSet) return false
        if (!ArcOps.installed) {
            sender.sendMessage("[Arc] operations suite is not installed")
            return true
        }
        when (sub) {
            "doctor" -> doctor(sender, args)
            "lagspike", "lagspikes" -> lagspike(sender, args)
            "plugin-cost", "plugincost" -> pluginCost(sender, args)
            "config" -> config(sender, args)
            "chunks" -> chunks(sender, args)
            "entity" -> entity(sender)
            "profiler" -> profiler(sender, args)
            "pregen" -> pregen(sender, args)
            "memory" -> memory(sender)
            "ping" -> ping(sender)
            "status" -> status(sender)
        }
        return true
    }

    private fun flag(args: Array<out String>, name: String): Boolean = args.any { it.equals("--$name", true) }
    private fun option(args: Array<out String>, name: String): String? {
        val i = args.indexOfFirst { it.equals("--$name", true) }
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    private fun doctor(sender: CommandSender, args: Array<out String>) {
        val world = option(args, "world")
        val report = ServerDoctor.report(world, ArcOps.lagSpikeMonitor)
        if (flag(args, "paste")) {
            sender.sendMessage("[Arc] uploading report…")
            ArcAsync.runBlockingIO {
                PasteUploader.upload(report, File(ArcOps.dataDir, "pastes"))
            }.thenSync { url -> sender.sendMessage("[Arc] report: $url") }
        } else {
            report.lineSequence().forEach { sender.sendMessage(it) }
        }
    }

    private fun lagspike(sender: CommandSender, args: Array<out String>) {
        val monitor = ArcOps.lagSpikeMonitor
        if (monitor == null) { sender.sendMessage("[Arc] lag-spike monitor not active"); return }
        val reports = monitor.recentReports()
        when (args.getOrNull(1)?.lowercase()) {
            "last" -> reports.firstOrNull()?.render()?.lineSequence()?.forEach { sender.sendMessage(it) }
                ?: sender.sendMessage("[Arc] no lag spikes captured yet")
            else -> {
                sender.sendMessage("[Arc] ${reports.size} recent lag spike(s):")
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
            if (cost == null) { sender.sendMessage("[Arc] no data for plugin '$arg'"); return }
            sender.sendMessage("[Arc] ${cost.plugin}: avg=${"%.3f".format(cost.avgMs)}ms max=${"%.1f".format(cost.maxMs)}ms " +
                "samples=${cost.samples} pendingTasks=${cost.pendingTasks} listeners=${cost.activeListeners} " +
                "(${if (cost.measured) "measured" else "heuristic"})")
            return
        }
        sender.sendMessage("[Arc] plugin cost (top):")
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
                if (path == null) { sender.sendMessage("usage: /arc config explain <path>"); return }
                val doc = ConfigValidator.explain(path)
                if (doc == null) { sender.sendMessage("[Arc] no docs for '$path'. Known: ${ConfigValidator.knownPaths().joinToString()}"); return }
                sender.sendMessage("[Arc] ${doc.path}")
                sender.sendMessage("  meaning   : ${doc.meaning}")
                sender.sendMessage("  perf      : ${doc.performanceImpact}")
                sender.sendMessage("  recommend : ${doc.recommended}")
                sender.sendMessage("  restart   : ${if (doc.requiresRestart) "required" else "live"}")
            }
            else -> {
                val findings = ConfigValidator.check()
                if (findings.isEmpty()) { sender.sendMessage("[Arc] config check: no issues found"); return }
                sender.sendMessage("[Arc] config check — ${findings.size} finding(s):")
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
        val opt = ArcOps.aiOptimizer
        if (opt != null) {
            val s = opt.stats
            sender.sendMessage("[Arc] entity AI optimizer:")
            sender.sendMessage("  awake=${s.awake} throttled=${s.throttled} protected(targeting)=${s.protectedTargeting}")
            val total = s.awake + s.throttled
            if (total > 0) sender.sendMessage("  throttle ratio: ${"%.0f".format(s.throttled.toDouble() / total * 100)}%")
        } else {
            sender.sendMessage("[Arc] AI optimizer OFF (enable entity-optimization in arc-ops.yml)")
        }
        val guard = ArcOps.densityOptimizer
        if (guard != null) {
            val g = guard.stats
            sender.sendMessage("[Arc] density guard: active=${g.lastActive} culled(lastRun)=${g.culledThisRun} culled(total)=${g.culledTotal}")
        } else {
            sender.sendMessage("[Arc] density guard OFF (enable entity-density-guard in arc-ops.yml)")
        }
    }

    private fun profiler(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "start" -> {
                val interval = args.getOrNull(2)?.toLongOrNull() ?: 10L
                if (MainThreadProfiler.start(interval)) sender.sendMessage("[Arc] profiler started (interval=${interval}ms). Stop with /arc profiler stop")
                else sender.sendMessage("[Arc] profiler already running")
            }
            "stop" -> { MainThreadProfiler.stop(); sender.sendMessage("[Arc] profiler stopped. /arc profiler report") }
            else -> MainThreadProfiler.report(25).lineSequence().forEach { sender.sendMessage(it) }
        }
    }

    private fun pregen(sender: CommandSender, args: Array<out String>) {
        val pre = ArcOps.pregenerator
        when (args.getOrNull(1)?.lowercase()) {
            "cancel" -> sender.sendMessage(if (pre.cancel()) "[Arc] pregen cancelled" else "[Arc] no pregen running")
            "status" -> {
                val p = pre.progress()
                if (p == null) sender.sendMessage("[Arc] no pregen running")
                else sender.sendMessage("[Arc] pregen ${p.third}: ${p.first}/${p.second} chunks (${"%.1f".format(p.first.toDouble() / p.second * 100)}%)")
            }
            "start" -> {
                val world = args.getOrNull(2)?.let { Bukkit.getWorld(it) }
                val radius = args.getOrNull(3)?.toIntOrNull()
                if (world == null || radius == null) { sender.sendMessage("usage: /arc pregen start <world> <radiusChunks> [cx cz]"); return }
                val cx = args.getOrNull(4)?.toIntOrNull() ?: world.spawnLocation.blockX
                val cz = args.getOrNull(5)?.toIntOrNull() ?: world.spawnLocation.blockZ
                val started = pre.start(
                    world, radius, cx, cz,
                    onProgress = { done, total -> sender.sendMessage("[Arc] pregen ${world.name}: $done/$total") },
                    onComplete = { sender.sendMessage("[Arc] pregen ${world.name} complete") },
                )
                sender.sendMessage(if (started) "[Arc] pregen started: ${world.name} radius=$radius (${(2 * radius + 1) * (2 * radius + 1)} chunks)"
                else "[Arc] a pregen job is already running")
            }
            else -> sender.sendMessage("/arc pregen <start <world> <radius> [cx cz]|status|cancel>")
        }
    }

    private fun memory(sender: CommandSender) {
        val g = ArcOps.memory
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)
        sender.sendMessage("[Arc] heap: $used / $max MB (${"%.0f".format(used.toDouble() / max * 100)}%)")
        if (g != null) sender.sendMessage("  guard state: ${g.lastState} (warn>=${"%.0f".format(ArcOps.config.memoryWarnFraction * 100)}% crit>=${"%.0f".format(ArcOps.config.memoryCriticalFraction * 100)}%)")
        else sender.sendMessage("  memory guard: OFF")
    }

    private fun ping(sender: CommandSender) {
        val players = Bukkit.getOnlinePlayers().sortedByDescending { it.ping }
        if (players.isEmpty()) { sender.sendMessage("[Arc] no players online"); return }
        val avg = players.map { it.ping }.average()
        sender.sendMessage("[Arc] ping — avg ${"%.0f".format(avg)}ms, ${players.size} player(s):")
        players.take(10).forEach { sender.sendMessage("  ${it.name}: ${it.ping}ms") }
    }

    private fun status(sender: CommandSender) {
        val cfg = ArcOps.config
        sender.sendMessage("[Arc] status server: ${if (cfg.statusEnabled) "ON http://${cfg.statusBindAddress}:${cfg.statusPort}" else "OFF"}")
        if (cfg.statusEnabled) sender.sendMessage("  endpoints: /arc/status /arc/worlds /arc/performance /metrics")
    }
}
