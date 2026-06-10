package dev.arc.api.ops.doctor

import dev.arc.api.ops.lagspike.LagSpikeMonitor
import dev.arc.api.ops.metrics.SnapshotService
import dev.arc.api.ops.metrics.TickSampler
import org.bukkit.Bukkit
import java.lang.management.ManagementFactory

/**
 * Collects a one-shot health report. Pure read-only; must run on the main
 * thread (it touches world/entity state through the latest snapshot, which is
 * built on the main thread anyway, plus direct server queries).
 */
object ServerDoctor {

    fun report(worldFilter: String? = null, monitor: LagSpikeMonitor? = null): String {
        // Ensure freshest data when called interactively.
        runCatching { SnapshotService.refreshNow() }
        val snap = SnapshotService.latest
        val rt = Runtime.getRuntime()
        val os = ManagementFactory.getOperatingSystemMXBean()
        val runtimeMx = ManagementFactory.getRuntimeMXBean()

        val sb = StringBuilder()
        fun line(s: String) = sb.appendLine(s)

        line("===== Leaf Doctor =====")

        // ---- runtime / JVM ----
        line("[Java]")
        line("  version   : ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        line("  vm        : ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
        val flags = runtimeMx.inputArguments.filter { it.startsWith("-X") || it.startsWith("-XX") }
        line("  jvm flags : ${if (flags.isEmpty()) "(none captured)" else flags.joinToString(" ")}")

        // ---- hardware ----
        line("[Hardware]")
        line("  cpus      : ${rt.availableProcessors()}")
        val sysLoad = runCatching {
            val bean = os as? com.sun.management.OperatingSystemMXBean
            if (bean != null) "process=${pct(bean.processCpuLoad)} system=${pct(bean.cpuLoad)}" else "n/a"
        }.getOrDefault("n/a")
        line("  cpu load  : $sysLoad")

        // ---- memory / gc ----
        line("[Memory]")
        line("  heap used : ${snap.usedMemoryMb} MB / ${snap.maxMemoryMb} MB (${pctOf(snap.usedMemoryMb, snap.maxMemoryMb)})")
        line("  gc (10s)  : ${TickSampler.gcCountInWindow()} collection(s)")
        for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
            line("    ${gc.name}: count=${gc.collectionCount} time=${gc.collectionTime}ms")
        }

        // ---- disk ----
        line("[Disk]")
        val root = Bukkit.getWorldContainer()
        runCatching {
            val total = root.totalSpace / (1024 * 1024 * 1024)
            val free = root.usableSpace / (1024 * 1024 * 1024)
            line("  world dir : ${root.absolutePath}")
            line("  space     : ${total - free} GB used / $total GB total (${free} GB free)")
            line("  note      : I/O latency not sampled here (needs OS-level probe)")
        }.onFailure { line("  (disk probe failed: ${it.message})") }

        // ---- tick health ----
        line("[Tick]")
        val tps = TickSampler.tps()
        line("  TPS       : 1m=${"%.2f".format(tps.getOrElse(0){20.0})} 5m=${"%.2f".format(tps.getOrElse(1){20.0})} 15m=${"%.2f".format(tps.getOrElse(2){20.0})}")
        line("  MSPT      : paper-avg=${"%.1f".format(snap.paperMspt)} sampler-avg=${"%.1f".format(snap.avgMspt)} peak=${"%.1f".format(TickSampler.maxMspt())}")

        // ---- plugins ----
        line("[Plugins]")
        val plugins = Bukkit.getPluginManager().plugins
        line("  count     : ${plugins.size} (${plugins.count { it.isEnabled }} enabled)")

        // ---- worlds ----
        line("[Worlds]")
        val worlds = if (worldFilter != null) snap.worlds.filter { it.name.equals(worldFilter, true) } else snap.worlds
        if (worlds.isEmpty() && worldFilter != null) line("  (no world named '$worldFilter')")
        for (w in worlds) {
            line("  ${w.name}: players=${w.players} chunks=${w.loadedChunks} (forced=${w.forcedChunks}) entities=${w.entities} tiles=${w.tileEntities}")
            val top = w.entityCountsByType.entries.sortedByDescending { it.value }.take(5)
            if (top.isNotEmpty()) line("    top entities: " + top.joinToString(", ") { "${it.key}=${it.value}" })
        }

        // ---- recent lag spikes ----
        line("[Lag Spikes]")
        val spikes = monitor?.recentReports().orEmpty()
        if (spikes.isEmpty()) line("  none recorded")
        else spikes.take(5).forEach {
            line("  ${"%.0f".format(it.msptAtTrigger)}ms @ world=${it.worstWorld} gc=${it.gcDuringCapture}")
        }

        sb.append("=======================")
        return sb.toString()
    }

    private fun pct(v: Double): String = if (v < 0) "n/a" else "%.0f%%".format(v * 100)
    private fun pctOf(a: Long, b: Long): String = if (b <= 0) "n/a" else "%.0f%%".format(a.toDouble() / b * 100)
}
