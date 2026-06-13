@file:JvmName("Animations")

package dev.arc.api.entity

import org.bukkit.Location
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Tick-based keyframe animator for [VirtualEntity].
 * Define waypoints and the entity smoothly moves between them on a fixed schedule.
 *
 * ```kotlin
 * val entity = VirtualEntity(EntityType.ARMOR_STAND, startLocation)
 * entity.show(player)
 *
 * val anim = entity.animator(plugin)
 *     .keyframe(20) { moveTo(point1) }   // teleport to point1 at tick 20
 *     .keyframe(40) { moveTo(point2) }
 *     .keyframe(60) { moveTo(startLocation) }
 *     .loop()
 *     .start(player)
 * // later:
 * anim.stop()
 * ```
 */
class AnimationScheduler(
    private val entity: VirtualEntity,
    private val plugin: Plugin,
) {
    private val keyframes = mutableListOf<Keyframe>()
    private var loop = false
    private var task: BukkitTask? = null
    private var currentTick = 0L
    private val viewers = mutableSetOf<org.bukkit.entity.Player>()

    /** Add a keyframe at [tick] (relative to animation start). */
    fun keyframe(tick: Long, action: KeyframeScope.() -> Unit): AnimationScheduler {
        keyframes += Keyframe(tick, action)
        return this
    }

    /** Loop the animation from the beginning when it ends. */
    fun loop(): AnimationScheduler { loop = true; return this }

    /** Start the animation for [players]. */
    fun start(vararg players: org.bukkit.entity.Player): AnimationScheduler {
        viewers += players
        val totalTicks = keyframes.maxOfOrNull { it.tick } ?: return this
        val sorted = keyframes.sortedBy { it.tick }

        currentTick = 0
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, {
            val active = sorted.filter { it.tick == currentTick }
            val scope = KeyframeScope(entity, viewers.toTypedArray())
            active.forEach { it.action(scope) }

            currentTick++
            if (currentTick > totalTicks) {
                if (loop) currentTick = 0 else stop()
            }
        }, 0L, 1L)
        return this
    }

    /** Add [players] as new viewers mid-animation. */
    fun addViewers(vararg players: org.bukkit.entity.Player) { viewers += players }

    /** Stop and cancel the animation task. */
    fun stop() {
        task?.cancel()
        task = null
    }

    val isRunning: Boolean get() = task != null

    private data class Keyframe(val tick: Long, val action: KeyframeScope.() -> Unit)
}

/** DSL scope for actions inside a keyframe. */
class KeyframeScope(
    private val entity: VirtualEntity,
    private val viewers: Array<out org.bukkit.entity.Player>,
) {
    /** Teleport the entity to [location]. */
    fun moveTo(location: Location) = entity.teleport(location, *viewers)

    /** Update the entity's custom name. */
    fun setName(name: net.kyori.adventure.text.Component?) {
        entity.customName = name
        entity.updateMetadata(*viewers)
    }

    /** Update visibility. */
    fun setInvisible(invisible: Boolean) {
        entity.isInvisible = invisible
        entity.updateMetadata(*viewers)
    }

    /** Update glowing. */
    fun setGlowing(glow: Boolean) {
        entity.hasGlowing = glow
        entity.updateMetadata(*viewers)
    }

    /** Despawn the entity for all viewers. */
    fun hide() = entity.hide(*viewers)

    /** Spawn the entity for all viewers. */
    fun show() = entity.show(*viewers)
}

/** Create an [AnimationScheduler] for this entity. */
fun VirtualEntity.animator(plugin: Plugin): AnimationScheduler =
    AnimationScheduler(this, plugin)
