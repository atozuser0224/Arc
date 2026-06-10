@file:JvmName("GoalDsl")

package dev.arc.api.ai

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mob
import java.util.EnumSet

/**
 * A DSL for defining custom mob AI as a [Goal] from a few lambdas, instead of implementing the interface and
 * wiring goal selectors by hand.
 *
 * The [mob] is exposed inside the scope so the callbacks can act on it directly. Provide at least
 * [shouldActivate] (when the goal may run) and [onTick] (what it does each tick); [onStart]/[onStop] handle
 * setup/teardown. [GoalType]s tell the selector which behaviour slots this goal occupies so it can preempt
 * vanilla goals correctly.
 *
 * ```kotlin
 * mob.addGoal(plugin.key("chase-player"), priority = 1, types = EnumSet.of(GoalType.MOVE, GoalType.LOOK)) {
 *     shouldActivate = { mob.world.players.isNotEmpty() }
 *     onTick = { mob.world.players.minByOrNull { it.location.distance(mob.location) }?.let { mob.moveTo(it, 1.2) } }
 * }
 * ```
 */
public class GoalScope internal constructor(
    /** The mob this goal is attached to. */
    public val mob: Mob,
) {
    /** Whether the goal is currently eligible to run. */
    public var shouldActivate: () -> Boolean = { false }

    /** Whether a running goal should keep running (defaults to re-checking [shouldActivate]). */
    public var shouldStayActive: () -> Boolean = { shouldActivate() }

    /** Called once when the goal starts. */
    public var onStart: () -> Unit = {}

    /** Called every tick while the goal is active. */
    public var onTick: () -> Unit = {}

    /** Called once when the goal stops. */
    public var onStop: () -> Unit = {}
}

private class ScopedGoal(
    private val key: GoalKey<Mob>,
    private val types: EnumSet<GoalType>,
    private val scope: GoalScope,
) : Goal<Mob> {
    override fun shouldActivate(): Boolean = scope.shouldActivate()
    override fun shouldStayActive(): Boolean = scope.shouldStayActive()
    override fun start() {
        scope.onStart()
    }

    override fun stop() {
        scope.onStop()
    }

    override fun tick() {
        scope.onTick()
    }

    override fun getKey(): GoalKey<Mob> = key
    override fun getTypes(): EnumSet<GoalType> = types
}

/**
 * Build a custom [Goal] from the [builder] DSL and register it on this mob at [priority] (lower runs first),
 * returning it so it can later be removed. [key] uniquely identifies the goal; [types] declares which
 * behaviour slots it occupies.
 */
public fun Mob.addGoal(
    key: NamespacedKey,
    priority: Int,
    types: EnumSet<GoalType> = EnumSet.of(GoalType.UNKNOWN_BEHAVIOR),
    builder: GoalScope.() -> Unit,
): Goal<Mob> {
    val scope = GoalScope(this).apply(builder)
    val goal = ScopedGoal(GoalKey.of(Mob::class.java, key), types, scope)
    Bukkit.getMobGoals().addGoal(this, priority, goal)
    return goal
}
