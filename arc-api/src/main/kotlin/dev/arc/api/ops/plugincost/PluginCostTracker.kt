package dev.arc.api.ops.plugincost

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * Attributes main-thread cost to plugins.
 *
 * IMPORTANT — what is measured vs. estimated:
 *  - Pending/active scheduler task counts per plugin: **measured** (Bukkit API).
 *  - Registered event-listener counts per plugin: **measured** (HandlerList).
 *  - Execution time (ms): only for code paths that opt in via [time]/[timeSync].
 *    We do NOT silently wrap every listener/task — doing that safely requires
 *    server-internal event-dispatch hooks (see NMS note in docs). Until a path
 *    is instrumented, its time cost is reported as 0 and the heuristic falls
 *    back to task/listener counts.
 *
 * So `/leaf plugin-cost top` ranks by measured time where available, then by a
 * count-based heuristic — and labels which is which.
 */
object PluginCostTracker {

    private class Stat {
        val totalNanos = LongAdder()
        val samples = LongAdder()
        @Volatile var maxNanos = 0L
    }

    private val stats = ConcurrentHashMap<String, Stat>()
    @Volatile private var enabled = true

    fun setEnabled(value: Boolean) { enabled = value }

    /** Record a measured execution. Cheap; safe from any thread. */
    fun record(plugin: String, nanos: Long) {
        if (!enabled) return
        val s = stats.computeIfAbsent(plugin) { Stat() }
        s.totalNanos.add(nanos)
        s.samples.increment()
        if (nanos > s.maxNanos) s.maxNanos = nanos
    }

    /** Time [block], attributing the elapsed time to [plugin]. Returns the result. */
    inline fun <T> time(plugin: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(plugin, System.nanoTime() - start)
        }
    }

    /** Convenience overload keyed by a [Plugin] instance. */
    inline fun <T> time(plugin: Plugin, block: () -> T): T = time(plugin.name, block)

    /** Average measured ms per sample, per plugin. */
    fun snapshotCost(): Map<String, Double> {
        val out = HashMap<String, Double>()
        for ((name, s) in stats) {
            val n = s.samples.sum()
            if (n > 0) out[name] = s.totalNanos.sum() / n / 1_000_000.0
        }
        return out
    }

    data class PluginCost(
        val plugin: String,
        val avgMs: Double,
        val maxMs: Double,
        val samples: Long,
        val pendingTasks: Int,
        val activeListeners: Int,
        val measured: Boolean,
    ) {
        /** Heuristic ordering score: measured time dominates; else count-based. */
        val score: Double
            get() = if (measured) avgMs * (samples.coerceAtMost(1000)) + maxMs
            else pendingTasks * 2.0 + activeListeners * 0.5
    }

    fun top(limit: Int = 10): List<PluginCost> {
        val pending = pendingTaskCounts()
        val listeners = listenerCounts()
        val plugins = (stats.keys + pending.keys + listeners.keys).toSet()
        return plugins.map { name ->
            val s = stats[name]
            val samples = s?.samples?.sum() ?: 0
            val avg = if (samples > 0) s!!.totalNanos.sum() / samples / 1_000_000.0 else 0.0
            PluginCost(
                plugin = name,
                avgMs = avg,
                maxMs = (s?.maxNanos ?: 0L) / 1_000_000.0,
                samples = samples,
                pendingTasks = pending[name] ?: 0,
                activeListeners = listeners[name] ?: 0,
                measured = samples > 0,
            )
        }.sortedByDescending { it.score }.take(limit)
    }

    fun forPlugin(name: String): PluginCost? = top(Int.MAX_VALUE).firstOrNull { it.plugin.equals(name, true) }

    private fun pendingTaskCounts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        runCatching {
            for (task in Bukkit.getScheduler().pendingTasks) {
                val owner = task.owner?.name ?: continue
                out[owner] = (out[owner] ?: 0) + 1
            }
        }
        return out
    }

    private fun listenerCounts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        runCatching {
            for (plugin in Bukkit.getPluginManager().plugins) {
                val count = HandlerList.getRegisteredListeners(plugin).size
                if (count > 0) out[plugin.name] = count
            }
        }
        return out
    }
}
