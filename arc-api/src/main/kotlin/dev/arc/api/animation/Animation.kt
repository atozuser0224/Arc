@file:JvmName("Animations")

package dev.arc.api.animation

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Simple tick-based keyframe animator.
 *
 * ```kotlin
 * Animation.of(plugin, durationTicks = 40)
 *     .onTick { t -> entity.teleport(spline.sample(t)) }
 *     .onFinish { entity.remove() }
 *     .start()
 * ```
 */
public class Animation private constructor(
    private val plugin: Plugin,
    private val durationTicks: Int,
    private val periodTicks: Long = 1L,
) {
    private var onTick: ((t: Double) -> Unit)? = null
    private var onFinish: (() -> Unit)? = null
    private var task: BukkitRunnable? = null

    /** [t] ∈ [0.0, 1.0] normalized progress. */
    public fun onTick(action: (t: Double) -> Unit): Animation { onTick = action; return this }
    public fun onFinish(action: () -> Unit): Animation { onFinish = action; return this }

    public fun start(): Animation {
        var tick = 0
        task = object : BukkitRunnable() {
            override fun run() {
                val t = tick.toDouble() / durationTicks.coerceAtLeast(1)
                onTick?.invoke(t.coerceIn(0.0, 1.0))
                tick += periodTicks.toInt()
                if (tick > durationTicks) {
                    onTick?.invoke(1.0)
                    onFinish?.invoke()
                    cancel()
                }
            }
        }
        task!!.runTaskTimer(plugin, 0L, periodTicks)
        return this
    }

    public fun cancel() {
        runCatching { task?.cancel() }
    }

    public companion object {
        public fun of(plugin: Plugin, durationTicks: Int, periodTicks: Long = 1L): Animation =
            Animation(plugin, durationTicks, periodTicks)
    }
}
