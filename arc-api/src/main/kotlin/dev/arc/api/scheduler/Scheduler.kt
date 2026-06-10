@file:JvmName("Scheduler")

package dev.arc.api.scheduler

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/** Run [action] on the main thread next tick. */
public fun Plugin.runNext(action: () -> Unit): BukkitTask =
    server.scheduler.runTask(this, action)

/** Run [action] after [delayTicks] ticks. */
public fun Plugin.runLater(delayTicks: Long, action: () -> Unit): BukkitTask =
    server.scheduler.runTaskLater(this, action, delayTicks)

/** Run [action] every [periodTicks] ticks, starting after [delayTicks]. */
public fun Plugin.runRepeat(delayTicks: Long = 0, periodTicks: Long = 1, action: BukkitRunnable.() -> Unit): BukkitTask {
    val runnable = object : BukkitRunnable() { override fun run() { action() } }
    return runnable.runTaskTimer(this, delayTicks, periodTicks)
}

/** Run [action] asynchronously after [delayTicks] ticks. */
public fun Plugin.runAsync(delayTicks: Long = 0, action: () -> Unit): BukkitTask =
    server.scheduler.runTaskLaterAsynchronously(this, action, delayTicks)

/** Run [action] on main thread after [delayTicks], repeat every [periodTicks]. Cancel when [action] returns false. */
public fun Plugin.runWhile(delayTicks: Long = 0, periodTicks: Long = 1, action: () -> Boolean): BukkitTask {
    var task: BukkitTask? = null
    val runnable = object : BukkitRunnable() {
        override fun run() { if (!action()) cancel() }
    }
    task = runnable.runTaskTimer(this, delayTicks, periodTicks)
    return task
}

/** Cancel a [BukkitTask] safely (ignores cancellation errors). */
public fun BukkitTask.cancelSafe() { runCatching { cancel() } }
