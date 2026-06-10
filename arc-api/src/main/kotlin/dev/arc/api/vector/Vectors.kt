@file:JvmName("Vectors")

package dev.arc.api.vector

import org.bukkit.util.Vector

/**
 * Operator overloads for [Vector] that, like the location helpers, never mutate the receiver - every result
 * is a fresh clone. Lets velocity/direction math read naturally:
 *
 * ```kotlin
 * val knockback = (target.location - source.location).toVector().normalized() * 1.5
 * entity.velocity = knockback + Vector(0.0, 0.4, 0.0)
 * ```
 */

public operator fun Vector.plus(other: Vector): Vector = clone().add(other)

public operator fun Vector.minus(other: Vector): Vector = clone().subtract(other)

public operator fun Vector.times(scalar: Double): Vector = clone().multiply(scalar)

public operator fun Vector.unaryMinus(): Vector = clone().multiply(-1.0)

/** A unit-length copy (zero vector stays zero). */
public fun Vector.normalized(): Vector {
    val length = length()
    return if (length == 0.0) clone() else clone().multiply(1.0 / length)
}

/** A copy scaled to exactly [length] units long. */
public fun Vector.withLength(length: Double): Vector = normalized().multiply(length)
