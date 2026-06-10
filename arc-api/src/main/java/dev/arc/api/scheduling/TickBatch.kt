package dev.arc.api.scheduling

import dev.arc.api.Arc
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

data class BatchResult(
    val processed: Int,
    val failed: Int,
    val elapsedNanos: Long,
) {
    val elapsedMillis: Long
        get() = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
}

/**
 * Process global-only [items] over multiple global ticks. For block/location
 * mutations use [dispatchRegionBatch].
 */
fun <T> Plugin.dispatchBatch(
    items: Iterable<T>,
    maxMillisPerTick: Long = Arc.settings.tickBudgetMillis,
    failFast: Boolean = false,
    operation: (T) -> Unit,
): CompletableFuture<BatchResult> {
    require(maxMillisPerTick > 0) { "maxMillisPerTick must be > 0" }
    val future = CompletableFuture<BatchResult>()
    val taskRef = AtomicReference<BukkitTask>()
    val iterator = items.iterator()
    val started = System.nanoTime()
    var processed = 0
    var failed = 0

    val runnable = object : BukkitRunnable() {
        override fun run() {
            if (!isEnabled) {
                future.cancel(false)
                cancel()
                return
            }

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxMillisPerTick)
            do {
                if (!iterator.hasNext()) {
                    future.complete(BatchResult(processed, failed, System.nanoTime() - started))
                    cancel()
                    return
                }

                val item = iterator.next()
                try {
                    operation(item)
                } catch (error: Throwable) {
                    failed++
                    if (failFast) {
                        future.completeExceptionally(error)
                        cancel()
                        return
                    }
                } finally {
                    processed++
                }
            } while (System.nanoTime() < deadline)
        }
    }

    runCatching { runnable.runTaskTimer(this, 1L, 1L) }
        .onSuccess(taskRef::set)
        .onFailure(future::completeExceptionally)
    future.whenComplete { _, _ ->
        if (future.isCancelled) taskRef.get()?.cancel()
    }
    return future
}

private data class RegionKey(
    val world: World,
    val chunkX: Int,
    val chunkZ: Int,
)

/**
 * Region-aware batch processing for Folia/Paper. Items are grouped by owning
 * chunk and each group consumes at most [maxMillisPerTick] per region tick.
 */
fun <T> Plugin.dispatchRegionBatch(
    items: Iterable<T>,
    locationOf: (T) -> Location,
    maxMillisPerTick: Long = Arc.settings.tickBudgetMillis,
    maxConcurrentRegions: Int = Arc.settings.regionBatchConcurrency,
    failFast: Boolean = false,
    operation: (T) -> Unit,
): CompletableFuture<BatchResult> {
    require(maxMillisPerTick > 0) { "maxMillisPerTick must be > 0" }
    require(maxConcurrentRegions > 0) { "maxConcurrentRegions must be > 0" }
    val future = CompletableFuture<BatchResult>()
    val started = System.nanoTime()
    val grouped = LinkedHashMap<RegionKey, MutableList<T>>()
    for (item in items) {
        val location = locationOf(item)
        val world = location.world
            ?: return future.also {
                it.completeExceptionally(IllegalArgumentException("Batch item location has no world"))
            }
        val key = RegionKey(world, location.blockX shr 4, location.blockZ shr 4)
        grouped.getOrPut(key, ::ArrayList).add(item)
    }
    if (grouped.isEmpty()) {
        future.complete(BatchResult(0, 0, System.nanoTime() - started))
        return future
    }

    val remainingRegions = AtomicInteger(grouped.size)
    val activeRegions = AtomicInteger()
    val processed = LongAdder()
    val failed = LongAdder()
    val active = ConcurrentHashMap.newKeySet<RegionKey>()
    val pending = ConcurrentLinkedQueue(grouped.entries)
    val launchLock = Any()

    fun finishRegion(key: RegionKey) {
        if (!active.remove(key)) return
        activeRegions.decrementAndGet()
        if (remainingRegions.decrementAndGet() == 0 && !future.isDone) {
            future.complete(
                BatchResult(
                    processed = processed.sum().toInt(),
                    failed = failed.sum().toInt(),
                    elapsedNanos = System.nanoTime() - started,
                ),
            )
        }
    }

    lateinit var launchPending: () -> Unit
    fun launchGroup(key: RegionKey, group: List<T>) {
        active += key
        activeRegions.incrementAndGet()
        val iterator = group.iterator()
        lateinit var runSlice: Runnable
        runSlice = Runnable {
            if (future.isDone || !isEnabled) {
                finishRegion(key)
                launchPending()
                return@Runnable
            }

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxMillisPerTick)
            do {
                if (!iterator.hasNext()) {
                    finishRegion(key)
                    launchPending()
                    return@Runnable
                }
                try {
                    operation(iterator.next())
                } catch (error: Throwable) {
                    failed.increment()
                    if (failFast) {
                        future.completeExceptionally(error)
                        finishRegion(key)
                        launchPending()
                        return@Runnable
                    }
                } finally {
                    processed.increment()
                }
            } while (System.nanoTime() < deadline)

            runCatching {
                Bukkit.getRegionScheduler().execute(
                    this,
                    key.world,
                    key.chunkX,
                    key.chunkZ,
                    runSlice,
                )
            }.onFailure {
                future.completeExceptionally(it)
                finishRegion(key)
                launchPending()
            }
        }

        runCatching {
            Bukkit.getRegionScheduler().execute(
                this,
                key.world,
                key.chunkX,
                key.chunkZ,
                runSlice,
            )
        }.onFailure {
            future.completeExceptionally(it)
            finishRegion(key)
            launchPending()
        }
    }

    launchPending = {
        synchronized(launchLock) {
            while (!future.isDone && activeRegions.get() < maxConcurrentRegions) {
                val entry = pending.poll() ?: break
                launchGroup(entry.key, entry.value)
            }
        }
    }
    launchPending()
    return future
}
