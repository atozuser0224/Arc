@file:JvmName("MobAi")

package dev.arc.api.ai

import com.destroystokyo.paper.entity.ai.GoalType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob

/**
 * One-call mob-AI controls built on Paper's *public* AI surface (the pathfinder, the AI/aware toggles, and
 * the [com.destroystokyo.paper.entity.ai.MobGoals] registry) - no NMS required.
 *
 * Changing how a mob behaves usually means digging into goal selectors via reflection. These extensions make
 * the common moves trivial: send a mob somewhere, freeze its brain, strip its targeting, or wipe its goals.
 * For bespoke behaviour, build a [Goal][com.destroystokyo.paper.entity.ai.Goal] with the `addGoal { }` DSL.
 *
 * ```kotlin
 * zombie.moveTo(player.location, speed = 1.3)   // path there
 * cow.makePassive()                              // never targets/attacks
 * golem.freezeAi()                               // brain off (still rendered)
 * ```
 */

/** Whether the entity's AI runs at all (`setAI`/`hasAI`). */
public var Mob.aiEnabled: Boolean
    get() = hasAI()
    set(value) {
        setAI(value)
    }

/** Freeze the brain: the mob stops ticking AI but stays loaded (Paper "aware" flag). */
public fun Mob.freezeAi() {
    isAware = false
}

/** Resume AI ticking after [freezeAi]. */
public fun Mob.unfreezeAi() {
    isAware = true
}

/** Set (or clear, with `null`) this mob's attack target. */
public fun Mob.attack(target: LivingEntity?) {
    setTarget(target)
}

/** Path this mob to [location] using its pathfinder. Returns `true` if a path was found. */
public fun Mob.moveTo(location: Location, speed: Double = 1.0): Boolean =
    pathfinder.moveTo(location, speed)

/** Path this mob toward another entity. Returns `true` if a path was found. */
public fun Mob.moveTo(target: LivingEntity, speed: Double = 1.0): Boolean =
    pathfinder.moveTo(target, speed)

/** Stop the mob's current pathfinding. */
public fun Mob.stopMoving() {
    pathfinder.stopPathfinding()
}

/** `true` if the mob is currently following a path. */
public val Mob.hasPath: Boolean
    get() = pathfinder.hasPath()

/** Remove **all** of the mob's goals - it keeps no autonomous behaviour until you add some. */
public fun Mob.clearGoals() {
    Bukkit.getMobGoals().removeAllGoals(this)
}

/** Strip targeting goals so the mob never picks a victim (a simple "make it passive"). */
public fun Mob.makePassive() {
    Bukkit.getMobGoals().removeAllGoals(this, GoalType.TARGET)
}

/** Remove every goal of a given [GoalType] (e.g. [GoalType.MOVE] to stop wandering). */
public fun Mob.removeGoals(type: GoalType) {
    Bukkit.getMobGoals().removeAllGoals(this, type)
}
