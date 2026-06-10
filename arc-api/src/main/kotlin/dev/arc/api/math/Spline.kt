@file:JvmName("Splines")

package dev.arc.api.math

import org.bukkit.Location
import org.bukkit.World

/**
 * Catmull-Rom spline through a series of [controlPoints].
 * Use [sample] to get a smooth position at any t ∈ [0, 1].
 */
public class CatmullRomSpline(private val controlPoints: List<Location>) {

    init {
        require(controlPoints.size >= 2) { "Need at least 2 control points" }
    }

    /** World of the first control point. */
    public val world: World? get() = controlPoints.first().world

    /** Sample the spline at [t] ∈ [0, 1]. */
    public fun sample(t: Double): Location {
        val points = controlPoints
        val n = points.size
        val seg = (t * (n - 1)).toInt().coerceIn(0, n - 2)
        val localT = (t * (n - 1) - seg).coerceIn(0.0, 1.0)

        val p0 = points[(seg - 1).coerceAtLeast(0)]
        val p1 = points[seg]
        val p2 = points[(seg + 1).coerceAtMost(n - 1)]
        val p3 = points[(seg + 2).coerceAtMost(n - 1)]

        return catmullRom(p0, p1, p2, p3, localT)
    }

    /** Sample [count] evenly-spaced points along the spline. */
    public fun samplePoints(count: Int): List<Location> =
        (0 until count).map { sample(it.toDouble() / (count - 1).coerceAtLeast(1)) }

    private fun catmullRom(p0: Location, p1: Location, p2: Location, p3: Location, t: Double): Location {
        val t2 = t * t
        val t3 = t2 * t
        fun interp(a: Double, b: Double, c: Double, d: Double): Double =
            0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)

        return Location(
            p1.world,
            interp(p0.x, p1.x, p2.x, p3.x),
            interp(p0.y, p1.y, p2.y, p3.y),
            interp(p0.z, p1.z, p2.z, p3.z),
        )
    }
}

/** Build a [CatmullRomSpline] from this list of locations. */
public fun List<Location>.spline(): CatmullRomSpline = CatmullRomSpline(this)
