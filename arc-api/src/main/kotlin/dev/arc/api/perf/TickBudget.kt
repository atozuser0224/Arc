@file:JvmName("TickBudgets")

package dev.arc.api.perf

import dev.arc.api.coroutine.launchEveryTicks
import kotlinx.coroutines.Job
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A per-tick work spreader that **caps how much time bulk work may steal from each tick**, the core defence
 * against lag spikes.
 *
 * Operations that touch thousands of blocks/entities (world fills, mass teleports, regeneration) blow the
 * 50 ms budget if done in one tick. Submit the work as many small units instead: each tick this drains the
 * queue only until [budgetMillis] is spent, deferring the rest to following ticks. The job is bound to the
 * plugin's coroutine scope, so it runs on the main thread and stops automatically on disable.
 *
 * ```kotlin
 * val budget = plugin.tickBudget(budgetMillis = 4)
 * for (block in hugeRegion.blocks()) budget.submit { block.type = Material.AIR }
 * ```
 */
public class TickBudget internal constructor(private val plugin: Plugin) {

    /** Maximum milliseconds of queued work to run per tick. */
    public var budgetMillis: Long = 5L

    private val queue = ConcurrentLinkedQueue<Runnable>()
    private var job: Job? = null

    /** Number of units still waiting to run. */
    public val pending: Int
        get() = queue.size

    /** Enqueue a unit of work to run (on the main thread) within a future tick's budget. */
    public fun submit(task: Runnable) {
        queue.add(task)
    }

    /** Begin draining the queue each tick. Idempotent. */
    public fun start() {
        if (job != null) return
        job = plugin.launchEveryTicks(1L) { drain() }
    }

    /** Stop draining (queued work is retained and resumes on the next [start]). */
    public fun stop() {
        job?.cancel()
        job = null
    }

    private fun drain() {
        val deadline = System.nanoTime() + budgetMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            (queue.poll() ?: break).run()
        }
    }
}

/** Create and start a [TickBudget] bound to this plugin. */
public fun Plugin.tickBudget(budgetMillis: Long = 5L): TickBudget =
    TickBudget(this).also {
        it.budgetMillis = budgetMillis
        it.start()
    }
