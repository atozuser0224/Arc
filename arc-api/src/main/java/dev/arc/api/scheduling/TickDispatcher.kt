package dev.arc.api.scheduling

import dev.arc.api.Arc
import dev.arc.api.control.ArcFeatures
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.plugin.Plugin
import java.util.ArrayDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * Load-spreading scheduler. Bursty work (mass block edits, bulk entity ops, world
 * scans) submitted here is drained a little each tick under a time budget, so a
 * large batch never stalls a single tick into a lag spike.
 *
 * Controllable at runtime via [Arc]:
 * - budget follows [Arc.settings].tickBudgetMillis unless a fixed override is given.
 * - toggling [ArcFeatures.TICK_DISPATCHER] off flushes everything each tick
 *   (spreading disabled) without changing call sites.
 *
 * ```
 * val spread = TickDispatcher.start(plugin) // follows Arc.settings live
 * Arc.settings.tickBudgetMillis = 4
 * for (loc in tenThousandBlocks) {
 *     if (!spread.trySubmit { loc.block.type = Material.AIR }) break
 * }
 * spread.close()
 * ```
 *
 * Submission is thread-safe; tasks run on Paper's global region thread. Tasks
 * that mutate entities or blocks must use their entity/region scheduler instead.
 * The queue is bounded to apply producer backpressure rather than retaining an
 * unlimited number of closures.
 */
class TickDispatcher private constructor(
    private val plugin: Plugin,
    private val budgetOverride: Long?,
    private val maxPendingOverride: Int?,
) : AutoCloseable {
    private val queue = BoundedTaskQueue {
        maxPendingOverride ?: Arc.settings.dispatcherMaxPending
    }
    private val rejectedCounter = AtomicLong()

    @Volatile
    private var scheduledTask: ScheduledTask? = null

    /**
     * Queue [task] to run on a future tick within the time budget.
     *
     * @throws RejectedExecutionException if this dispatcher is closed, its plugin
     * is disabled, or the pending-task limit has been reached.
     */
    fun submit(task: Runnable) {
        if (!trySubmit(task)) {
            throw RejectedExecutionException(
                "TickDispatcher rejected task: closed=$isClosed, pending=$pending, maxPending=$maxPending",
            )
        }
    }

    /** Try to queue [task] without blocking. False means the producer must slow down. */
    fun trySubmit(task: Runnable): Boolean {
        if (!plugin.isEnabled || !queue.offer(task)) {
            rejectedCounter.incrementAndGet()
            return false
        }
        return true
    }

    /** Number of tasks still waiting to run. */
    val pending: Int
        get() = queue.size

    /** Number of submissions rejected because the dispatcher could not accept them. */
    val rejected: Long
        get() = rejectedCounter.get()

    /** Current live queue limit, or the fixed override supplied to [start]. */
    val maxPending: Int
        get() = maxPendingOverride ?: Arc.settings.dispatcherMaxPending

    val isClosed: Boolean
        get() = queue.isClosed

    private fun nextTask(): Runnable? = queue.poll()

    private fun runSafely(task: Runnable) {
        try {
            task.run()
        } catch (t: Throwable) {
            plugin.logger.log(Level.WARNING, "TickDispatcher task failed", t)
        }
    }

    private fun drain() {
        if (isClosed) return

        // Spreading disabled -> flush the whole queue this tick.
        if (!Arc.features.isEnabled(ArcFeatures.TICK_DISPATCHER)) {
            var task = nextTask()
            while (task != null) {
                runSafely(task)
                task = nextTask()
            }
            return
        }

        val budget = budgetOverride ?: Arc.settings.tickBudgetMillis
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget)
        while (true) {
            val task = nextTask() ?: break
            runSafely(task)
            if (System.nanoTime() >= deadline) break
        }
    }

    private fun attach(task: ScheduledTask) {
        scheduledTask = task
        if (isClosed) task.cancel()
    }

    /** Stop future drains and discard all pending tasks. */
    override fun close() {
        scheduledTask?.cancel()
        queue.close()
    }

    companion object {
        /**
         * Start a dispatcher that drains every tick. With no [maxMillisPerTick] it
         * follows [Arc.settings].tickBudgetMillis live; pass a value to pin a fixed
         * budget for this dispatcher.
         */
        fun start(
            plugin: Plugin,
            maxMillisPerTick: Long? = null,
            maxPending: Int? = null,
        ): TickDispatcher {
            require(maxMillisPerTick == null || maxMillisPerTick > 0) {
                "maxMillisPerTick must be positive"
            }
            require(maxPending == null || maxPending > 0) {
                "maxPending must be positive"
            }
            val dispatcher = TickDispatcher(plugin, maxMillisPerTick, maxPending)
            val task = org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                { dispatcher.drain() },
                1L,
                1L,
            )
            dispatcher.attach(task)
            return dispatcher
        }
    }
}

internal class BoundedTaskQueue(
    private val maxSize: () -> Int,
) {
    private val tasks = ArrayDeque<Runnable>()
    private var closed = false

    val size: Int
        get() = synchronized(tasks) { tasks.size }

    val isClosed: Boolean
        get() = synchronized(tasks) { closed }

    fun offer(task: Runnable): Boolean = synchronized(tasks) {
        if (closed || tasks.size >= maxSize().coerceAtLeast(1)) {
            false
        } else {
            tasks.add(task)
            true
        }
    }

    fun poll(): Runnable? = synchronized(tasks) { tasks.poll() }

    fun close(): Int = synchronized(tasks) {
        closed = true
        val discarded = tasks.size
        tasks.clear()
        discarded
    }
}
