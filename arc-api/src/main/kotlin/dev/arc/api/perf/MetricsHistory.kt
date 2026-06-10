@file:JvmName("MetricsHistory")

package dev.arc.api.perf

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Rolling ring-buffer of MSPT and TPS samples, useful for dashboards and anomaly detection.
 *
 * ```kotlin
 * val metrics = MetricsHistory.start(plugin, samples = 600) // last 30 s
 * metrics.avgMspt()   // average MSPT over the window
 * metrics.maxMspt()   // worst tick in the window
 * metrics.tpsHistory  // raw TPS values
 * ```
 */
public class MetricsHistory private constructor(
    private val capacity: Int,
) {
    private val msptRing = DoubleArray(capacity)
    private val tpsRing = DoubleArray(capacity)
    private var head = 0
    private var size = 0

    internal fun record(mspt: Double, tps: Double) {
        msptRing[head] = mspt
        tpsRing[head] = tps
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    public fun avgMspt(): Double = if (size == 0) 0.0 else msptRing.take(size).average()
    public fun maxMspt(): Double = if (size == 0) 0.0 else msptRing.take(size).max()
    public fun minMspt(): Double = if (size == 0) 0.0 else msptRing.take(size).min()
    public fun avgTps(): Double  = if (size == 0) 0.0 else tpsRing.take(size).average()

    /** Snapshot of the MSPT history (oldest → newest). */
    public val msptHistory: DoubleArray
        get() = DoubleArray(size) { i -> msptRing[(head - size + i + capacity) % capacity] }

    /** Snapshot of the TPS history (oldest → newest). */
    public val tpsHistory: DoubleArray
        get() = DoubleArray(size) { i -> tpsRing[(head - size + i + capacity) % capacity] }

    public companion object {
        /** Start recording every tick. [samples] = number of ticks to retain. */
        public fun start(plugin: Plugin, samples: Int = 1200): MetricsHistory {
            val history = MetricsHistory(samples)
            object : BukkitRunnable() {
                override fun run() {
                    history.record(ServerLoad.mspt, ServerLoad.tps)
                }
            }.runTaskTimer(plugin, 1L, 1L)
            return history
        }
    }
}
