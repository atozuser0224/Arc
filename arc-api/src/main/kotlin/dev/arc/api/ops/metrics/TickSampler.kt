package dev.arc.api.ops.metrics

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.lang.management.ManagementFactory

/**
 * Single source of truth for live timing metrics, sampled on the main thread
 * once per tick. Everything else (doctor, lag-spike monitor, status server)
 * reads from here so the cost of measuring is paid exactly once.
 *
 * MSPT here is a *wall-clock interval* between consecutive main-thread ticks
 * (System.nanoTime delta). It includes everything the server did that tick, so
 * it is a faithful "how long did this tick take" signal — but note it can be
 * inflated by external OS scheduling, not only by the server. For the canonical
 * server-internal number we also expose [paperAverageMspt] from the Paper API.
 */
object TickSampler {

    private const val RING = 200 // ~10s at 20 TPS

    private val msptRing = DoubleArray(RING)
    private val gcRing = LongArray(RING)
    @Volatile private var index = 0
    @Volatile private var filled = 0

    @Volatile private var lastTickNanos = 0L
    @Volatile private var lastGcCount = 0L
    @Volatile private var lastGcTimeMs = 0L

    @Volatile var lastMspt: Double = 0.0; private set
    @Volatile var lastTickGcCount: Long = 0; private set
    @Volatile var tickCounter: Long = 0; private set

    /**
     * Wall-clock nanoTime of the most recent main-thread tick. An off-thread
     * watchdog can compare this against [System.nanoTime] to detect a stalled
     * main thread (the value stops advancing while the server is frozen).
     */
    fun heartbeatNanos(): Long = lastTickNanos

    private var task: BukkitTask? = null

    fun start(plugin: Plugin) {
        if (task != null) return
        lastTickNanos = System.nanoTime()
        val (c, t) = gcTotals()
        lastGcCount = c
        lastGcTimeMs = t
        task = object : BukkitRunnable() {
            override fun run() = sample()
        }.runTaskTimer(plugin, 1L, 1L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun sample() {
        val now = System.nanoTime()
        val mspt = (now - lastTickNanos) / 1_000_000.0
        lastTickNanos = now

        val (gcCount, gcTime) = gcTotals()
        val gcDelta = gcCount - lastGcCount
        lastGcCount = gcCount
        lastGcTimeMs = gcTime

        lastMspt = mspt
        lastTickGcCount = gcDelta
        tickCounter++

        msptRing[index] = mspt
        gcRing[index] = gcDelta
        index = (index + 1) % RING
        if (filled < RING) filled++
    }

    private fun gcTotals(): Pair<Long, Long> {
        var count = 0L
        var time = 0L
        for (bean in ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.collectionCount >= 0) count += bean.collectionCount
            if (bean.collectionTime >= 0) time += bean.collectionTime
        }
        return count to time
    }

    /** Rolling average MSPT over the sampler ring (wall-clock based). */
    fun avgMspt(): Double {
        if (filled == 0) return 0.0
        var sum = 0.0
        for (i in 0 until filled) sum += msptRing[i]
        return sum / filled
    }

    fun maxMspt(): Double {
        if (filled == 0) return 0.0
        var m = 0.0
        for (i in 0 until filled) if (msptRing[i] > m) m = msptRing[i]
        return m
    }

    /** GC collections observed across the ring window. */
    fun gcCountInWindow(): Long {
        var sum = 0L
        for (i in 0 until filled) sum += gcRing[i]
        return sum
    }

    /** Paper's own averaged MSPT (server-internal tick time). */
    fun paperAverageMspt(): Double =
        runCatching { Bukkit.getServer().averageTickTime }.getOrDefault(0.0)

    /** Paper TPS [1m, 5m, 15m]. */
    fun tps(): DoubleArray =
        runCatching { Bukkit.getTPS() }.getOrDefault(doubleArrayOf(20.0, 20.0, 20.0))
}
