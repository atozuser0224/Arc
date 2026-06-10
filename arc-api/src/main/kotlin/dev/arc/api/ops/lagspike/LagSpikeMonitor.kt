package dev.arc.api.ops.lagspike

import dev.arc.api.ops.OpsConfig
import dev.arc.api.ops.metrics.SnapshotService
import dev.arc.api.ops.metrics.TickSampler
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.logging.Logger

/** A single captured lag spike. */
data class LagSpikeReport(
    val triggeredAtMillis: Long,
    val msptAtTrigger: Double,
    val gcDuringCapture: Long,
    val durationTicks: Int,
    val msptSamples: List<Double>,
    val worstWorld: String?,
    val worldEntityCounts: Map<String, Int>,
    val topEntityTypes: List<Pair<String, Int>>,
    val onlinePlayers: Int,
) {
    fun render(): String = buildString {
        val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(triggeredAtMillis))
        appendLine("=== Lag Spike @ $ts ===")
        appendLine("trigger MSPT : ${"%.1f".format(msptAtTrigger)} ms")
        appendLine("peak MSPT    : ${"%.1f".format(msptSamples.maxOrNull() ?: msptAtTrigger)} ms")
        appendLine("GC during    : $gcDuringCapture collection(s)  ${if (gcDuringCapture > 0) "<-- likely GC pause" else ""}")
        appendLine("players      : $onlinePlayers")
        appendLine("worst world  : ${worstWorld ?: "n/a"}")
        appendLine("world entities:")
        worldEntityCounts.entries.sortedByDescending { it.value }.forEach {
            appendLine("  ${it.key}: ${it.value}")
        }
        appendLine("top entity types:")
        topEntityTypes.forEach { appendLine("  ${it.first}: ${it.second}") }
        appendLine("mspt trace (${msptSamples.size} ticks):")
        appendLine("  " + msptSamples.joinToString(" ") { "%.0f".format(it) })
        append("note: phase-level (entity/tile/chunk/plugin) breakdown requires the")
        append(" server-internal capture hook; see docs.")
    }
}

/**
 * Watches per-tick MSPT and, when it crosses the configured threshold, records
 * a short trace + a server snapshot for post-mortem analysis.
 *
 * Limitation: without a server-internal tick-phase hook we cannot attribute the
 * spike to a specific phase (entity tick vs tile-entity tick vs plugin task).
 * What we *can* capture from the API safely: the MSPT trace across the capture
 * window, GC activity, per-world entity counts, and the dominating entity types.
 * That is enough to point an operator at the right world/entity class. A deeper
 * phase breakdown is a documented NMS extension point.
 */
class LagSpikeMonitor(
    private val plugin: Plugin,
    private val config: OpsConfig,
    private val reportDir: File,
    private val log: Logger,
) {
    private val recent = ArrayDeque<LagSpikeReport>()
    private var task: BukkitTask? = null

    @Volatile private var capturing = false
    private val captureSamples = ArrayList<Double>()
    private var captureTicksLeft = 0
    private var captureGcStart = 0L
    private var captureTrigger = 0.0
    private var captureStartMillis = 0L

    fun recentReports(): List<LagSpikeReport> = synchronized(recent) { recent.toList() }

    fun start() {
        if (task != null) return
        task = object : BukkitRunnable() {
            override fun run() = tick()
        }.runTaskTimer(plugin, 1L, 1L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        if (!config.lagSpikeEnabled) return
        val mspt = TickSampler.lastMspt

        if (capturing) {
            captureSamples.add(mspt)
            if (--captureTicksLeft <= 0) finishCapture()
            return
        }

        if (mspt >= config.lagSpikeMsptThreshold) beginCapture(mspt)
    }

    private fun beginCapture(trigger: Double) {
        capturing = true
        captureSamples.clear()
        captureSamples.add(trigger)
        captureTicksLeft = config.lagSpikeCaptureDurationTicks.coerceAtLeast(1)
        captureGcStart = TickSampler.gcCountInWindow()
        captureTrigger = trigger
        captureStartMillis = System.currentTimeMillis()
    }

    private fun finishCapture() {
        capturing = false
        val snap = SnapshotService.latest
        val worldEntities = snap.worlds.associate { it.name to it.entities }
        val worst = snap.worlds.maxByOrNull { it.entities }?.name
        val typeTotals = HashMap<String, Int>()
        for (w in snap.worlds) for ((t, c) in w.entityCountsByType) typeTotals[t] = (typeTotals[t] ?: 0) + c

        val report = LagSpikeReport(
            triggeredAtMillis = captureStartMillis,
            msptAtTrigger = captureTrigger,
            gcDuringCapture = (TickSampler.gcCountInWindow() - captureGcStart).coerceAtLeast(0),
            durationTicks = captureSamples.size,
            msptSamples = captureSamples.toList(),
            worstWorld = worst,
            worldEntityCounts = worldEntities,
            topEntityTypes = typeTotals.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
            onlinePlayers = snap.onlinePlayers,
        )

        synchronized(recent) {
            recent.addFirst(report)
            while (recent.size > config.lagSpikeMaxReports) recent.removeLast()
        }

        log.warning("[Arc] Lag spike captured: ${"%.1f".format(report.msptAtTrigger)}ms, worst world=${report.worstWorld}")

        if (config.lagSpikeSaveReport) saveReport(report)
    }

    private fun saveReport(report: LagSpikeReport) {
        runCatching {
            reportDir.mkdirs()
            val name = "lagspike-${report.triggeredAtMillis}.txt"
            File(reportDir, name).writeText(report.render())
            // best-effort retention
            val files = reportDir.listFiles { f -> f.name.startsWith("lagspike-") }?.sortedBy { it.name } ?: return@runCatching
            val excess = files.size - config.lagSpikeMaxReports
            if (excess > 0) files.take(excess).forEach { it.delete() }
        }.onFailure { log.warning("[Arc] Failed to save lag spike report: ${it.message}") }
    }
}
