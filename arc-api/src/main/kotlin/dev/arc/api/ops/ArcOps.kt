package dev.arc.api.ops

import dev.arc.api.ops.async.ArcAsync
import dev.arc.api.ops.crash.CrashAnalyzer
import dev.arc.api.ops.entity.EntityAiOptimizer
import dev.arc.api.ops.entity.EntityDensityGuard
import dev.arc.api.ops.lagspike.LagSpikeMonitor
import dev.arc.api.ops.memory.MemoryGuard
import dev.arc.api.ops.metrics.SnapshotService
import dev.arc.api.ops.metrics.TickSampler
import dev.arc.api.ops.plugincost.PluginCostTracker
import dev.arc.api.ops.pregen.ChunkPregenerator
import dev.arc.api.ops.scheduler.ArcScheduler
import dev.arc.api.ops.status.StatusHttpServer
import dev.arc.api.ops.watchdog.StallWatchdog
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Entry point for the Arc Operations Suite. Call [install] once at start-up
 * with a [Plugin] that owns the repeating sampler tasks.
 *
 * Lifecycle: starts the always-on read-only subsystems (tick sampler, snapshot
 * service, lag-spike monitor) immediately, and starts the opt-in subsystems
 * (entity AI optimizer, status HTTP server) only if enabled in config. A
 * [reload] re-reads config and starts/stops the opt-in pieces accordingly.
 */
object ArcOps {

    @Volatile var installed = false; private set

    lateinit var config: OpsConfig; private set
    lateinit var dataDir: File; private set

    private lateinit var plugin: Plugin
    private var monitor: LagSpikeMonitor? = null
    private var optimizer: EntityAiOptimizer? = null
    private var statusServer: StatusHttpServer? = null
    private var densityGuard: EntityDensityGuard? = null
    private var memoryGuard: MemoryGuard? = null
    private var stallWatchdog: StallWatchdog? = null

    /** Always available once installed; one server-wide pregeneration job at a time. */
    val pregenerator: ChunkPregenerator by lazy { ChunkPregenerator(plugin) }

    val lagSpikeMonitor: LagSpikeMonitor? get() = monitor
    val aiOptimizer: EntityAiOptimizer? get() = optimizer
    val densityOptimizer: EntityDensityGuard? get() = densityGuard
    val memory: MemoryGuard? get() = memoryGuard

    @Synchronized
    fun install(plugin: Plugin, dataDir: File = File("arc-ops")) {
        if (installed) return
        this.plugin = plugin
        this.dataDir = dataDir
        dataDir.mkdirs()

        config = OpsConfig(File(dataDir, "arc-ops.yml")).also { it.reload() }

        ArcAsync.init(plugin)
        ArcScheduler.init(plugin)
        PluginCostTracker.setEnabled(config.pluginCostEnabled)

        // Always-on, read-only measurement.
        TickSampler.start(plugin)
        SnapshotService.start(plugin, config.statusSnapshotIntervalTicks)

        monitor = LagSpikeMonitor(plugin, config, File(dataDir, "lag-spikes"), plugin.logger).also { it.start() }

        // Opt-in subsystems.
        applyOptInSubsystems()

        // Boot-time crash post-mortem.
        if (config.crashAnalyzeOnBoot) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                runCatching { CrashAnalyzer.analyzeLatest() }.getOrNull()?.takeIf { !it.isEmpty }?.let {
                    plugin.logger.warning("[Arc] Previous-boot crash detected. /arc crash analyze for details.")
                    plugin.logger.warning(it.render())
                }
            }, 100L)
        }

        // The `/arc` ops subcommands are registered by ArcControlCommand, which
        // delegates to ArcOpsCommand.dispatch — so nothing to register here.
        installed = true
        plugin.logger.info("[Arc] Operations Suite installed (lag-spike=${config.lagSpikeEnabled}, entity-opt=${config.entityOptEnabled}, status=${config.statusEnabled})")
    }

    @Synchronized
    fun reload() {
        if (!installed) return
        config.reload()
        PluginCostTracker.setEnabled(config.pluginCostEnabled)
        applyOptInSubsystems()
    }

    private fun applyOptInSubsystems() {
        // Entity AI optimizer
        if (config.entityOptEnabled && config.distanceAiEnabled) {
            if (optimizer == null) optimizer = EntityAiOptimizer(plugin, config).also { it.start() }
        } else {
            optimizer?.stop()
            optimizer = null
        }
        // Entity density guard
        if (config.densityEnabled) {
            if (densityGuard == null) densityGuard = EntityDensityGuard(plugin, config).also { it.start() }
        } else {
            densityGuard?.stop()
            densityGuard = null
        }
        // Memory guard
        if (config.memoryGuardEnabled) {
            if (memoryGuard == null) memoryGuard = MemoryGuard(plugin, config).also { it.start() }
        } else {
            memoryGuard?.stop()
            memoryGuard = null
        }
        // Stall watchdog — recreated so threshold/dump changes apply on reload.
        stallWatchdog?.stop()
        stallWatchdog = if (config.stallWatchdogEnabled) {
            StallWatchdog(
                config.stallThresholdMs, config.stallFullThreadDump,
                File(dataDir, "stalls"), plugin.logger,
            ).also { it.start() }
        } else {
            null
        }
        // Status HTTP server
        if (config.statusEnabled) {
            if (statusServer == null) {
                statusServer = runCatching {
                    StatusHttpServer(config.statusBindAddress, config.statusPort, plugin.logger).also { it.start() }
                }.getOrElse {
                    plugin.logger.warning("[Arc] Status server failed to start: ${it.message}")
                    null
                }
            }
        } else {
            statusServer?.stop()
            statusServer = null
        }
    }

    @Synchronized
    fun shutdown() {
        if (!installed) return
        monitor?.stop()
        optimizer?.stop()
        densityGuard?.stop()
        memoryGuard?.stop()
        stallWatchdog?.stop()
        statusServer?.stop()
        dev.arc.api.ops.profiler.MainThreadProfiler.stop()
        pregenerator.cancel()
        SnapshotService.stop()
        TickSampler.stop()
        ArcAsync.shutdown()
        installed = false
    }
}
