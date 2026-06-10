@file:JvmName("Gamerules")

package dev.arc.api.world

import org.bukkit.GameRule
import org.bukkit.World

/**
 * Game-rule, time and weather helpers over stable Bukkit API.
 */

/** Set a game rule (e.g. `world.setRule(GameRule.DO_DAYLIGHT_CYCLE, false)`). */
public fun <T : Any> World.setRule(rule: GameRule<T>, value: T) {
    setGameRule(rule, value)
}

/** Read a game rule value, or `null` if unset. */
public fun <T : Any> World.getRule(rule: GameRule<T>): T? = getGameRuleValue(rule)

/** Set the time to morning. */
public fun World.makeDay() {
    time = 1000L
}

/** Set the time to night. */
public fun World.makeNight() {
    time = 13000L
}

/** Clear storm and thunder. */
public fun World.clearWeather() {
    setStorm(false)
    isThundering = false
}

/** Start a thunderstorm. */
public fun World.startStorm() {
    setStorm(true)
    isThundering = true
}
