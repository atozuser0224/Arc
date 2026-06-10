@file:JvmName("Countdowns")

package dev.arc.api.countdown

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Tick-accurate countdown that fires [onTick] each second and [onFinish] at zero.
 *
 * ```kotlin
 * Countdown.seconds(plugin, 10)
 *     .onTick { remaining -> players.forEach { it.sendActionBar("§e${remaining}s") } }
 *     .onFinish { startGame() }
 *     .start()
 * ```
 */
public class Countdown private constructor(
    private val plugin: Plugin,
    private val seconds: Int,
) {
    private var onTick: ((remaining: Int) -> Unit)? = null
    private var onFinish: (() -> Unit)? = null
    private var task: BukkitRunnable? = null

    public fun onTick(action: (remaining: Int) -> Unit): Countdown { onTick = action; return this }
    public fun onFinish(action: () -> Unit): Countdown { onFinish = action; return this }

    public fun start(): Countdown {
        var remaining = seconds
        onTick?.invoke(remaining)
        task = object : BukkitRunnable() {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    onTick?.invoke(0)
                    onFinish?.invoke()
                    cancel()
                } else {
                    onTick?.invoke(remaining)
                }
            }
        }
        task!!.runTaskTimer(plugin, 20L, 20L)
        return this
    }

    public fun cancel() { runCatching { task?.cancel() } }

    public val isRunning: Boolean get() = task?.isCancelled == false

    public companion object {
        public fun seconds(plugin: Plugin, seconds: Int): Countdown = Countdown(plugin, seconds)
    }
}
