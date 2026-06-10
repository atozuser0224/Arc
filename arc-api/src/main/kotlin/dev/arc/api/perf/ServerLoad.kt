package dev.arc.api.perf

import org.bukkit.Bukkit
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference

/** Coarse server-load buckets used to drive adaptive optimizations. */
public enum class LoadLevel {
    /** Plenty of headroom - safe to restore quality settings. */
    LOW,

    /** Healthy; no action needed. */
    NORMAL,

    /** Tick time elevated - start shedding load. */
    HIGH,

    /** At/over budget - shed load aggressively. */
    CRITICAL,
}

/**
 * The single source of truth for "how loaded is the server right now", and the input every adaptive
 * optimization here reads.
 *
 * Works standalone on the Bukkit API ([Bukkit.getAverageTickTime] / [Bukkit.getTPS]); if arc-server's
 * reflective [PerfBackend] is on the classpath it is auto-discovered for sharper, lower-latency MSPT.
 * Thresholds are tunable so an operator can decide how eagerly to react.
 */
public object ServerLoad {

    private val backendRef = AtomicReference<PerfBackend?>(null)

    private fun backend(): PerfBackend? = backendRef.get() ?: run {
        val loaded = ServiceLoader.load(PerfBackend::class.java, javaClass.classLoader).firstOrNull() ?: return null
        if (backendRef.compareAndSet(null, loaded)) loaded else backendRef.get()
    }

    /** Override telemetry source explicitly (mostly for arc-server / tests). */
    public fun installBackend(backend: PerfBackend) {
        backendRef.set(backend)
    }

    /** MSPT at/above which load is [LoadLevel.HIGH]. */
    public var highMsptThreshold: Double = 45.0

    /** MSPT at/above which load is [LoadLevel.CRITICAL]. */
    public var criticalMsptThreshold: Double = 50.0

    /** MSPT at/below which load is [LoadLevel.LOW] (recovery). */
    public var lowMsptThreshold: Double = 25.0

    /** Live milliseconds-per-tick. */
    public val mspt: Double
        get() = backend()?.mspt ?: Bukkit.getAverageTickTime()

    /** Live ticks-per-second. */
    public val tps: Double
        get() = backend()?.tps ?: Bukkit.getTPS().firstOrNull() ?: 20.0

    /** The current [LoadLevel], derived from [mspt] and the thresholds (with a NORMAL hysteresis band). */
    public val level: LoadLevel
        get() {
            val current = mspt
            return when {
                current >= criticalMsptThreshold -> LoadLevel.CRITICAL
                current >= highMsptThreshold -> LoadLevel.HIGH
                current <= lowMsptThreshold -> LoadLevel.LOW
                else -> LoadLevel.NORMAL
            }
        }
}
