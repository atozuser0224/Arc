@file:JvmName("ServerCooldowns")

package dev.arc.api.player

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import kotlin.time.Duration

/**
 * Server-side persistent cooldowns stored in the player's [org.bukkit.persistence.PersistentDataContainer].
 *
 * Unlike Bukkit's [org.bukkit.entity.Player.setCooldown] (client-side item visual only), these cooldowns:
 * - Survive server restarts (PDC persists with the player file)
 * - Work per arbitrary string key, not per material
 * - Are invisible to the client (no hotbar radial animation)
 *
 * ```kotlin
 * // In a command:
 * if (player.hasServerCooldown(plugin, "daily-reward")) {
 *     val left = player.serverCooldownRemainingMs(plugin, "daily-reward")
 *     player.sendMessage("Wait ${left / 1000}s before claiming again.")
 *     return
 * }
 * player.setServerCooldown(plugin, "daily-reward", 24.hours)
 * giveReward(player)
 * ```
 */

private fun key(plugin: Plugin, name: String) = NamespacedKey(plugin, "cd_$name")

/**
 * Put [key] on cooldown for [duration].
 * Overwrites any existing cooldown for the same key.
 */
fun Player.setServerCooldown(plugin: Plugin, key: String, duration: Duration) {
    val expiresAt = System.currentTimeMillis() + duration.inWholeMilliseconds
    persistentDataContainer.set(key(plugin, key), PersistentDataType.LONG, expiresAt)
}

/**
 * Put [key] on cooldown for [durationMs] milliseconds.
 */
fun Player.setServerCooldown(plugin: Plugin, key: String, durationMs: Long) {
    val expiresAt = System.currentTimeMillis() + durationMs
    persistentDataContainer.set(key(plugin, key), PersistentDataType.LONG, expiresAt)
}

/**
 * Whether [key] cooldown is currently active (exists and has not expired).
 */
fun Player.hasServerCooldown(plugin: Plugin, key: String): Boolean =
    serverCooldownRemainingMs(plugin, key) > 0L

/**
 * Remaining cooldown in milliseconds. Returns 0 if absent or already expired.
 * Expired entries are cleaned up automatically on first access.
 */
fun Player.serverCooldownRemainingMs(plugin: Plugin, key: String): Long {
    val nsKey = key(plugin, key)
    val expiresAt = persistentDataContainer.get(nsKey, PersistentDataType.LONG) ?: return 0L
    val remaining = expiresAt - System.currentTimeMillis()
    if (remaining <= 0L) {
        persistentDataContainer.remove(nsKey)
        return 0L
    }
    return remaining
}

/**
 * Remaining cooldown as a [Duration]. [Duration.ZERO] if not active.
 */
fun Player.serverCooldownRemaining(plugin: Plugin, key: String): Duration =
    Duration.parse("${serverCooldownRemainingMs(plugin, key)}ms")

/**
 * Clear a cooldown immediately regardless of whether it has expired.
 */
fun Player.clearServerCooldown(plugin: Plugin, key: String) {
    persistentDataContainer.remove(key(plugin, key))
}

/**
 * Run [block] only if [key] is not on cooldown, setting a new cooldown of [duration] first.
 * Returns true if the block was executed, false if the cooldown blocked it.
 *
 * ```kotlin
 * if (!player.withServerCooldown(plugin, "mine-bonus", 30.seconds) {
 *     player.giveExp(50)
 * }) player.sendMessage("Cooldown active!")
 * ```
 */
inline fun Player.withServerCooldown(
    plugin: Plugin,
    key: String,
    duration: Duration,
    block: Player.() -> Unit,
): Boolean {
    if (hasServerCooldown(plugin, key)) return false
    setServerCooldown(plugin, key, duration)
    block()
    return true
}
