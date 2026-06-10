package dev.arc.api.ops

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * File-backed config for the Leaf Operations Suite (`leaf-ops.yml`).
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

        crashAnalyzeOnBoot = yml.getBoolean("crash.analyze-on-boot", crashAnalyzeOnBoot)
    }

    fun save() {
        val yml = YamlConfiguration()
        yml.options().setHeader(
            listOf(
                "Leaf Operations Suite configuration.",
                "Diagnostics default ON (read-only). Aggressive optimizations default OFF.",
                "Reload at runtime with: /leaf reload",
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

        yml.set("crash.analyze-on-boot", crashAnalyzeOnBoot)

        file.parentFile?.mkdirs()
        yml.save(file)
    }
}
