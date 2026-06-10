package dev.arc.api.ops

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * File-backed config for the Arc Operations Suite (`arc-ops.yml`).
 *
 * Design rule: **safety first**. Anything that changes Paper plugin-visible
 * behaviour (entity AI throttling, view-distance changes) defaults to OFF.
 * Pure diagnostics (doctor, chunk report, config check) default to ON because
 * they only read state.
 *
 * The file is auto-generated with documented defaults on first boot.
 */
class OpsConfig internal constructor(private val file: File) {

    // ---- lag-spike-capture -------------------------------------------------
    @Volatile var lagSpikeEnabled: Boolean = true
    @Volatile var lagSpikeMsptThreshold: Double = 100.0
    @Volatile var lagSpikeCaptureDurationTicks: Int = 40
    @Volatile var lagSpikeSaveReport: Boolean = true
    @Volatile var lagSpikeMaxReports: Int = 50

    // ---- plugin-cost -------------------------------------------------------
    @Volatile var pluginCostEnabled: Boolean = true

    // ---- entity-optimization (AGGRESSIVE -> default OFF) -------------------
    @Volatile var entityOptEnabled: Boolean = false
    @Volatile var distanceAiEnabled: Boolean = false
    @Volatile var aiNearRange: Double = 32.0
    @Volatile var aiFarRange: Double = 64.0
    @Volatile var aiFarInterval: Int = 5
    @Volatile var aiCheckIntervalTicks: Int = 20
    /** Worlds the AI optimizer is allowed to touch. Empty = all worlds. */
    @Volatile var entityOptWorlds: List<String> = emptyList()

    // ---- status / prometheus ----------------------------------------------
    @Volatile var statusEnabled: Boolean = false
    @Volatile var statusBindAddress: String = "127.0.0.1"
    @Volatile var statusPort: Int = 9595
    @Volatile var statusSnapshotIntervalTicks: Int = 20

    // ---- entity density guard (AGGRESSIVE -> default OFF) ------------------
    @Volatile var densityEnabled: Boolean = false
    @Volatile var densityMaxMobsPerChunk: Int = 24
    @Volatile var densityActivationTps: Double = 18.0
    @Volatile var densityCheckIntervalTicks: Int = 100
    @Volatile var densityWorlds: List<String> = emptyList()

    // ---- memory guard ------------------------------------------------------
    @Volatile var memoryGuardEnabled: Boolean = true
    @Volatile var memoryWarnFraction: Double = 0.85
    @Volatile var memoryCriticalFraction: Double = 0.95
    @Volatile var memoryCheckIntervalTicks: Int = 100
    @Volatile var memoryActionCooldownSeconds: Int = 30
    @Volatile var memoryAllowForcedGc: Boolean = false

    // ---- stall watchdog ----------------------------------------------------
    @Volatile var stallWatchdogEnabled: Boolean = true
    @Volatile var stallThresholdMs: Long = 5000
    @Volatile var stallFullThreadDump: Boolean = false

    // ---- crash diagnostics -------------------------------------------------
    @Volatile var crashAnalyzeOnBoot: Boolean = true

    fun reload() {
        if (!file.exists()) {
            save()
            return
        }
        val yml = YamlConfiguration.loadConfiguration(file)

        lagSpikeEnabled = yml.getBoolean("lag-spike-capture.enabled", lagSpikeEnabled)
        lagSpikeMsptThreshold = yml.getDouble("lag-spike-capture.mspt-threshold", lagSpikeMsptThreshold)
        lagSpikeCaptureDurationTicks = yml.getInt("lag-spike-capture.capture-duration-ticks", lagSpikeCaptureDurationTicks)
        lagSpikeSaveReport = yml.getBoolean("lag-spike-capture.save-report", lagSpikeSaveReport)
        lagSpikeMaxReports = yml.getInt("lag-spike-capture.max-reports", lagSpikeMaxReports)

        pluginCostEnabled = yml.getBoolean("plugin-cost.enabled", pluginCostEnabled)

        entityOptEnabled = yml.getBoolean("entity-optimization.enabled", entityOptEnabled)
        distanceAiEnabled = yml.getBoolean("entity-optimization.distance-based-ai.enabled", distanceAiEnabled)
        aiNearRange = yml.getDouble("entity-optimization.distance-based-ai.near-range", aiNearRange)
        aiFarRange = yml.getDouble("entity-optimization.distance-based-ai.far-range", aiFarRange)
        aiFarInterval = yml.getInt("entity-optimization.distance-based-ai.far-ai-interval", aiFarInterval)
        aiCheckIntervalTicks = yml.getInt("entity-optimization.distance-based-ai.check-interval-ticks", aiCheckIntervalTicks)
        entityOptWorlds = yml.getStringList("entity-optimization.worlds")

        statusEnabled = yml.getBoolean("status.enabled", statusEnabled)
        statusBindAddress = yml.getString("status.bind-address", statusBindAddress) ?: statusBindAddress
        statusPort = yml.getInt("status.port", statusPort)
        statusSnapshotIntervalTicks = yml.getInt("status.snapshot-interval-ticks", statusSnapshotIntervalTicks)

        densityEnabled = yml.getBoolean("entity-density-guard.enabled", densityEnabled)
        densityMaxMobsPerChunk = yml.getInt("entity-density-guard.max-mobs-per-chunk", densityMaxMobsPerChunk)
        densityActivationTps = yml.getDouble("entity-density-guard.activation-tps", densityActivationTps)
        densityCheckIntervalTicks = yml.getInt("entity-density-guard.check-interval-ticks", densityCheckIntervalTicks)
        densityWorlds = yml.getStringList("entity-density-guard.worlds")

        memoryGuardEnabled = yml.getBoolean("memory-guard.enabled", memoryGuardEnabled)
        memoryWarnFraction = yml.getDouble("memory-guard.warn-fraction", memoryWarnFraction)
        memoryCriticalFraction = yml.getDouble("memory-guard.critical-fraction", memoryCriticalFraction)
        memoryCheckIntervalTicks = yml.getInt("memory-guard.check-interval-ticks", memoryCheckIntervalTicks)
        memoryActionCooldownSeconds = yml.getInt("memory-guard.action-cooldown-seconds", memoryActionCooldownSeconds)
        memoryAllowForcedGc = yml.getBoolean("memory-guard.allow-forced-gc", memoryAllowForcedGc)

        stallWatchdogEnabled = yml.getBoolean("stall-watchdog.enabled", stallWatchdogEnabled)
        stallThresholdMs = yml.getLong("stall-watchdog.threshold-ms", stallThresholdMs)
        stallFullThreadDump = yml.getBoolean("stall-watchdog.full-thread-dump", stallFullThreadDump)

        crashAnalyzeOnBoot = yml.getBoolean("crash.analyze-on-boot", crashAnalyzeOnBoot)
    }

    fun save() {
        val yml = YamlConfiguration()
        yml.options().setHeader(
            listOf(
                "Arc Operations Suite configuration.",
                "Diagnostics default ON (read-only). Aggressive optimizations default OFF.",
                "Reload at runtime with: /arc reload",
            ),
        )

        yml.set("lag-spike-capture.enabled", lagSpikeEnabled)
        yml.set("lag-spike-capture.mspt-threshold", lagSpikeMsptThreshold)
        yml.set("lag-spike-capture.capture-duration-ticks", lagSpikeCaptureDurationTicks)
        yml.set("lag-spike-capture.save-report", lagSpikeSaveReport)
        yml.set("lag-spike-capture.max-reports", lagSpikeMaxReports)

        yml.set("plugin-cost.enabled", pluginCostEnabled)

        yml.set("entity-optimization.enabled", entityOptEnabled)
        yml.set("entity-optimization.distance-based-ai.enabled", distanceAiEnabled)
        yml.set("entity-optimization.distance-based-ai.near-range", aiNearRange)
        yml.set("entity-optimization.distance-based-ai.far-range", aiFarRange)
        yml.set("entity-optimization.distance-based-ai.far-ai-interval", aiFarInterval)
        yml.set("entity-optimization.distance-based-ai.check-interval-ticks", aiCheckIntervalTicks)
        yml.set("entity-optimization.worlds", entityOptWorlds)

        yml.set("status.enabled", statusEnabled)
        yml.set("status.bind-address", statusBindAddress)
        yml.set("status.port", statusPort)
        yml.set("status.snapshot-interval-ticks", statusSnapshotIntervalTicks)

        yml.set("entity-density-guard.enabled", densityEnabled)
        yml.set("entity-density-guard.max-mobs-per-chunk", densityMaxMobsPerChunk)
        yml.set("entity-density-guard.activation-tps", densityActivationTps)
        yml.set("entity-density-guard.check-interval-ticks", densityCheckIntervalTicks)
        yml.set("entity-density-guard.worlds", densityWorlds)

        yml.set("memory-guard.enabled", memoryGuardEnabled)
        yml.set("memory-guard.warn-fraction", memoryWarnFraction)
        yml.set("memory-guard.critical-fraction", memoryCriticalFraction)
        yml.set("memory-guard.check-interval-ticks", memoryCheckIntervalTicks)
        yml.set("memory-guard.action-cooldown-seconds", memoryActionCooldownSeconds)
        yml.set("memory-guard.allow-forced-gc", memoryAllowForcedGc)

        yml.set("stall-watchdog.enabled", stallWatchdogEnabled)
        yml.set("stall-watchdog.threshold-ms", stallThresholdMs)
        yml.set("stall-watchdog.full-thread-dump", stallFullThreadDump)

        yml.set("crash.analyze-on-boot", crashAnalyzeOnBoot)

        file.parentFile?.mkdirs()
        yml.save(file)
    }
}
