@file:JvmName("TimeUtils")

package dev.arc.api.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Conversions between server ticks, milliseconds and [kotlin.time.Duration], plus a clock formatter.
 *
 * Minecraft schedules in ticks (20 per second at full TPS); plugin logic usually thinks in seconds/minutes
 * or a [Duration]. These keep the two worlds a single call apart and avoid scattering `* 20` / `* 50` magic
 * numbers through the codebase.
 */

/** Nominal wall-clock milliseconds per tick at 20 TPS. */
public const val MILLIS_PER_TICK: Long = 50L

/** Nominal ticks per second at full TPS. */
public const val TICKS_PER_SECOND: Long = 20L

/** This many seconds expressed as a tick count (nominal 20 TPS). */
public fun Int.secondsAsTicks(): Long = this * TICKS_PER_SECOND

/** This many minutes expressed as a tick count (nominal 20 TPS). */
public fun Int.minutesAsTicks(): Long = this * TICKS_PER_SECOND * 60L

/** Convert a tick count to nominal wall-clock milliseconds. */
public fun Long.ticksToMillis(): Long = this * MILLIS_PER_TICK

/** Convert a tick count to a [Duration]. */
public fun Long.ticksToDuration(): Duration = (this * MILLIS_PER_TICK).milliseconds

/** Convert a [Duration] to a (floored) tick count. */
public fun Duration.toTicks(): Long = inWholeMilliseconds / MILLIS_PER_TICK

/** Format a tick count as `mm:ss` (or `h:mm:ss` past an hour). */
public fun formatTicks(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
