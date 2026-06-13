@file:JvmName("AIGoals")

package dev.arc.api.entity

import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.target.TargetGoal
import org.bukkit.craftbukkit.entity.CraftMob
import org.bukkit.entity.Mob
import kotlin.reflect.KClass

/**
 * Runtime AI goal manipulation for mobs. Gives full control over what a mob
 * wants to do and what it targets — without spawning a new entity.
 *
 * ```kotlin
 * // Remove all default goals and replace with custom ones
 * zombie.clearGoals()
 * zombie.clearTargetGoals()
 * zombie.addGoal(1, FloatGoal(nmsMob))
 * zombie.addGoal(2, MyCustomChaseGoal(nmsMob, player))
 *
 * // Remove just the MeleeAttackGoal, keep everything else
 * zombie.removeGoalsOfType(MeleeAttackGoal::class)
 *
 * // Inspect current goals
 * zombie.listGoals().forEach { println("priority=${it.priority} ${it.goal::class.simpleName}") }
 * ```
 *
 * Note: Goals use NMS classes. Import from `net.minecraft.world.entity.ai.goal.*`.
 */

data class GoalEntry(val priority: Int, val goal: Goal)

/** The underlying NMS [net.minecraft.world.entity.Mob] for this Bukkit [Mob]. */
val Mob.nms: net.minecraft.world.entity.Mob
    get() = (this as CraftMob).handle

// ── Goal selector ──────────────────────────────────────────────────────────

/** Add an AI movement/behaviour [goal] at [priority] (lower = higher priority). */
fun Mob.addGoal(priority: Int, goal: Goal) =
    nms.goalSelector.addGoal(priority, goal)

/** Add an AI targeting [goal] at [priority]. */
fun Mob.addTargetGoal(priority: Int, goal: TargetGoal) =
    nms.targetSelector.addGoal(priority, goal)

/** Remove a specific [goal] instance from the goal selector. */
fun Mob.removeGoal(goal: Goal) =
    nms.goalSelector.removeGoal(goal)

/** Remove a specific [goal] instance from the target selector. */
fun Mob.removeTargetGoal(goal: TargetGoal) =
    nms.targetSelector.removeGoal(goal)

/** Remove all goals whose type matches [type] from the goal selector. */
fun <T : Goal> Mob.removeGoalsOfType(type: KClass<T>) {
    val sel = nms.goalSelector
    sel.availableGoals
        .filter { type.isInstance(it.goal) }
        .map { it.goal }
        .forEach { sel.removeGoal(it) }
}

/** Remove all goals whose type matches [type] from the target selector. */
fun <T : Goal> Mob.removeTargetGoalsOfType(type: KClass<T>) {
    val sel = nms.targetSelector
    sel.availableGoals
        .filter { type.isInstance(it.goal) }
        .map { it.goal }
        .forEach { sel.removeGoal(it) }
}

/** Clear ALL goals from the goal selector (mob stops all active behaviour). */
fun Mob.clearGoals() {
    val sel = nms.goalSelector
    sel.availableGoals.map { it.goal }.toList().forEach { sel.removeGoal(it) }
}

/** Clear ALL targeting goals (mob stops targeting players/mobs). */
fun Mob.clearTargetGoals() {
    val sel = nms.targetSelector
    sel.availableGoals.map { it.goal }.toList().forEach { sel.removeGoal(it) }
}

// ── Inspection ─────────────────────────────────────────────────────────────

/** List all active goals in the goal selector. */
fun Mob.listGoals(): List<GoalEntry> =
    nms.goalSelector.availableGoals.map { GoalEntry(it.priority, it.goal) }.sortedBy { it.priority }

/** List all active goals in the target selector. */
fun Mob.listTargetGoals(): List<GoalEntry> =
    nms.targetSelector.availableGoals.map { GoalEntry(it.priority, it.goal) }.sortedBy { it.priority }
