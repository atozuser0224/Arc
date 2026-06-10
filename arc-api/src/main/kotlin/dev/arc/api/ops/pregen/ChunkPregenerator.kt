package dev.arc.api.ops.pregen

import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.atomic.AtomicInteger

/**
 * Throttled, cancellable chunk pre-generator.
 *
 * Generates a square region of chunks around a centre using Paper's async chunk
 * API ([World.getChunkAtAsync]), keeping only a bounded number of requests in
 * flight so it does not drown the chunk system or stall the main thread. A
 * driver task on the main thread refills the in-flight window each tick and
 * reports progress.
 *
 * One job at a time (server-wide) to keep the load predictable. Pre-generating
 * borders ahead of time removes the live chunk-gen spikes players cause when
 * exploring — at the cost of disk + a controlled background load now.
 */
class ChunkPregenerator(private val plugin: Plugin) {

    @Volatile private var job: Job? = null
    val isRunning: Boolean get() = job != null

    private inner class Job(
        val world: World,
        val centerChunkX: Int,
        val centerChunkZ: Int,
        val radius: Int,
        val maxInFlight: Int,
        val onProgress: (done: Int, total: Int) -> Unit,
        val onComplete: () -> Unit,
    ) {
        val total = (2 * radius + 1) * (2 * radius + 1)
        var cursor = 0
        val done = AtomicInteger()
        val inFlight = AtomicInteger()
        var lastReport = 0
        var task: BukkitTask? = null

        fun coordAt(i: Int): Pair<Int, Int> {
            val side = 2 * radius + 1
            val dx = i % side - radius
            val dz = i / side - radius
            return (centerChunkX + dx) to (centerChunkZ + dz)
        }

        fun drive() {
            while (cursor < total && inFlight.get() < maxInFlight) {
                val (cx, cz) = coordAt(cursor++)
                inFlight.incrementAndGet()
                world.getChunkAtAsync(cx, cz, true).whenComplete { _, _ ->
                    inFlight.decrementAndGet()
                    done.incrementAndGet()
                }
            }
            val finished = done.get()
            if (finished - lastReport >= 200 || (cursor >= total && inFlight.get() == 0)) {
                lastReport = finished
                onProgress(finished, total)
            }
            if (cursor >= total && inFlight.get() == 0) {
                task?.cancel()
                job = null
                onComplete()
            }
        }
    }

    fun start(
        world: World,
        radiusChunks: Int,
        centerBlockX: Int = world.spawnLocation.blockX,
        centerBlockZ: Int = world.spawnLocation.blockZ,
        maxInFlight: Int = 16,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {},
    ): Boolean {
        if (job != null) return false
        val j = Job(
            world = world,
            centerChunkX = centerBlockX shr 4,
            centerChunkZ = centerBlockZ shr 4,
            radius = radiusChunks.coerceIn(1, 5000),
            maxInFlight = maxInFlight.coerceIn(1, 64),
            onProgress = onProgress,
            onComplete = onComplete,
        )
        job = j
        j.task = object : BukkitRunnable() {
            override fun run() {
                if (job !== j) { cancel(); return }
                j.drive()
            }
        }.runTaskTimer(plugin, 1L, 1L)
        return true
    }

    fun cancel(): Boolean {
        val j = job ?: return false
        j.task?.cancel()
        job = null
        return true
    }

    fun progress(): Triple<Int, Int, String>? {
        val j = job ?: return null
        return Triple(j.done.get(), j.total, j.world.name)
    }
}
