package dev.arc.api.ops.memory

import dev.arc.api.ops.OpsConfig
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/**
 * Watches heap pressure and reacts gracefully before the JVM hits OutOfMemory.
 *
 * Two thresholds (fraction of max heap):
 *  - warn     -> log a warning + notify ops with `arc.admin`
 *  - critical -> additionally flush worlds to disk (`save-all`) so a subsequent
 *                OOM/crash loses as little as possible
 *
 * It does NOT call System.gc() by default (forcing GC under pressure usually
 * makes spikes worse); that is an explicit opt-in. Every action is rate-limited
 * so a sustained high-memory state produces periodic, not continuous, output.
 *
 * Read-only sampling by default — safe to leave on.
 */
class MemoryGuard(
    private val plugin: Plugin,
    private val config: OpsConfig,
) {
    @Volatile var lastUsedFraction: Double = 0.0; private set
    @Volatile var lastState: String = "ok"; private set
    private var lastActionMillis = 0L
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        val period = config.memoryCheckIntervalTicks.coerceAtLeast(20).toLong()
        task = object : BukkitRunnable() {
            override fun run() = pass()
        }.runTaskTimer(plugin, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun pass() {
        if (!config.memoryGuardEnabled) return
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        val max = rt.maxMemory()
        val frac = used.toDouble() / max
        lastUsedFraction = frac

        val now = System.currentTimeMillis()
        val cooldownMs = config.memoryActionCooldownSeconds * 1000L

        when {
            frac >= config.memoryCriticalFraction -> {
                lastState = "critical"
                if (now - lastActionMillis < cooldownMs) return
                lastActionMillis = now
                val usedMb = used / (1024 * 1024)
                val maxMb = max / (1024 * 1024)
                val msg = "[Arc] CRITICAL heap ${pct(frac)} (${usedMb}/${maxMb} MB) — flushing worlds"
                plugin.logger.severe(msg)
                notifyOps(msg)
                runCatching { plugin.server.savePlayers() }
                for (w in plugin.server.worlds) runCatching { w.save() }
                if (config.memoryAllowForcedGc) {
                    plugin.logger.warning("[Arc] memory-guard: requesting GC (opt-in)")
                    System.gc()
                }
            }
            frac >= config.memoryWarnFraction -> {
                lastState = "warn"
                if (now - lastActionMillis < cooldownMs) return
                lastActionMillis = now
                val msg = "[Arc] High heap ${pct(frac)} — watch for GC lag"
                plugin.logger.warning(msg)
                notifyOps(msg)
            }
            else -> lastState = "ok"
        }
    }

    private fun notifyOps(message: String) {
        for (p in plugin.server.onlinePlayers) {
            if (p.hasPermission("arc.admin")) p.sendMessage(message)
        }
    }

    private fun pct(v: Double): String = "%.0f%%".format(v * 100)
}
