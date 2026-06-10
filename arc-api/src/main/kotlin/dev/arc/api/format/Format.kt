@file:JvmName("Format")

package dev.arc.api.format

import java.text.DecimalFormat

private val COMPACT_FORMAT = DecimalFormat("#.##")

/** Format a large number compactly: 1200 → "1.2K", 3_500_000 → "3.5M". */
public fun Long.compactFormat(): String = when {
    this >= 1_000_000_000L -> "${COMPACT_FORMAT.format(this / 1_000_000_000.0)}B"
    this >= 1_000_000L     -> "${COMPACT_FORMAT.format(this / 1_000_000.0)}M"
    this >= 1_000L         -> "${COMPACT_FORMAT.format(this / 1_000.0)}K"
    else                   -> toString()
}

public fun Int.compactFormat(): String = toLong().compactFormat()
public fun Double.compactFormat(): String = toLong().compactFormat()

/** Format milliseconds as a human-readable duration: "2d 3h 14m 5s". */
public fun Long.asDuration(): String {
    var ms = this
    val days = ms / 86_400_000L;     ms %= 86_400_000L
    val hours = ms / 3_600_000L;     ms %= 3_600_000L
    val mins = ms / 60_000L;         ms %= 60_000L
    val secs = ms / 1_000L
    return buildString {
        if (days > 0)  append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (mins > 0)  append("${mins}m ")
        append("${secs}s")
    }.trim()
}

/** Format ticks as "mm:ss" (20 ticks = 1 s). */
public fun Int.ticksToTime(): String {
    val totalSec = this / 20
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}

/** Pad a string to [width] chars with [padChar] on both sides (center-align). */
public fun String.centerPad(width: Int, padChar: Char = ' '): String {
    if (length >= width) return this
    val total = width - length
    val left = total / 2
    val right = total - left
    return padChar.toString().repeat(left) + this + padChar.toString().repeat(right)
}

/** Progress bar: "████████░░░░" style. */
public fun progressBar(
    progress: Double,
    width: Int = 20,
    filled: Char = '█',
    empty: Char = '░',
): String {
    val filledCount = (progress.coerceIn(0.0, 1.0) * width).toInt()
    return filled.toString().repeat(filledCount) + empty.toString().repeat(width - filledCount)
}
