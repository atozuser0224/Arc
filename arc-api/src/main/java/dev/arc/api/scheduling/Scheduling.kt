package dev.arc.api.scheduling

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/**
 * Lambda-first wrappers over [org.bukkit.scheduler.BukkitScheduler].
 *
 * ```
 * plugin.sync { player.sendMessage("on main thread") }
 * plugin.syncTimer(0, 20) { if (done) cancel() else tick() }
 * ```
 */

/** Run [block] on the next server tick (main thread). */
fun Plugin.sync(block: () -> Unit): BukkitTask =
    Bukkit.getScheduler().runTask(this, Runnable(block))

/** Run [block] off the main thread on Bukkit's async pool. */
fun Plugin.async(block: () -> Unit): BukkitTask =
    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable(block))

/** Run [block] on the main thread after [delayTicks] ticks. */
fun Plugin.syncLater(delayTicks: Long, block: () -> Unit): BukkitTask =
    Bukkit.getScheduler().runTaskLater(this, Runnable(block), delayTicks)

/** Run [block] async after [delayTicks] ticks. */
fun Plugin.asyncLater(delayTicks: Long, block: () -> Unit): BukkitTask =
    Bukkit.getScheduler().runTaskLaterAsynchronously(this, Runnable(block), delayTicks)

/**
 * Repeating main-thread task. The receiver inside [block] is the running task,
 * so it can stop itself with [BukkitRunnable.cancel].
 */
fun Plugin.syncTimer(delayTicks: Long, periodTicks: Long, block: BukkitRunnable.() -> Unit): BukkitTask {
    val runnable = object : BukkitRunnable() {
        override fun run() = block()
    }
    return runnable.runTaskTimer(this, delayTicks, periodTicks)
}

/** Repeating async task; receiver is the running task (see [syncTimer]). */
fun Plugin.asyncTimer(delayTicks: Long, periodTicks: Long, block: BukkitRunnable.() -> Unit): BukkitTask {
    val runnable = object : BukkitRunnable() {
        override fun run() = block()
    }
    return runnable.runTaskTimerAsynchronously(this, delayTicks, periodTicks)
}
