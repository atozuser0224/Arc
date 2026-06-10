@file:JvmName("Numbers")

package dev.arc.api.numbers

/**
 * Number formatting for player-facing UI - compact abbreviations, Roman numerals (enchant/level labels), and
 * fixed-decimal strings.
 */

/** Abbreviate large counts: `1500 -> "1.5K"`, `2_300_000 -> "2.3M"`. Values under 1000 are returned as-is. */
public fun Long.abbreviate(): String {
    if (this < 1000L) return toString()
    val units = arrayOf("K", "M", "B", "T")
    var value = this.toDouble()
    var index = -1
    while (value >= 1000.0 && index < units.size - 1) {
        value /= 1000.0
        index++
    }
    return "%.1f%s".format(value, units[index])
}

/** Roman numeral for `1..3999` (the usual range for levels/tiers). */
public fun Int.toRoman(): String {
    require(this in 1..3999) { "Roman numerals support 1..3999, was $this" }
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val builder = StringBuilder()
    var remaining = this
    for (i in values.indices) {
        while (remaining >= values[i]) {
            builder.append(symbols[i])
            remaining -= values[i]
        }
    }
    return builder.toString()
}

/** Fixed-decimal string, e.g. `3.14159.format(2) == "3.14"`. */
public fun Double.format(decimals: Int = 2): String = "%.${decimals}f".format(this)
