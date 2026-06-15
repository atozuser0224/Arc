@file:JvmName("OfflinePlayers")

package dev.arc.api.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID

/**
 * Async helpers for [OfflinePlayer] data — avoids blocking the main thread.
 *
 * `Bukkit.getOfflinePlayer(UUID)` performs I/O (reads the player file from disk) when
 * the player is not cached. Calling it on the main thread can cause measurable lag on
 * servers with many unique players.
 *
 * ```kotlin
 * // In a suspend context (e.g. listenSuspend, coroutine launched with plugin.launch):
 * val offline = uuid.offlinePlayer()
 * val name = offline.name ?: "unknown"
 *
 * // Or with a callback (works from non-suspend code):
 * plugin.launch {
 *     val offline = uuid.offlinePlayer()
 *     // back on main thread here if using plugin.launch
 * }
 * ```
 */

/**
 * Resolves the [OfflinePlayer] for this UUID off the main thread, then returns it.
 * Must be called from a coroutine. The result is fetched on [Dispatchers.IO] and
 * the caller's coroutine context is restored afterwards.
 */
public suspend fun UUID.offlinePlayer(): OfflinePlayer =
    withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(this@offlinePlayer) }

/**
 * Returns the [OfflinePlayer] for this UUID. If the player is currently online, returns
 * their online player instance immediately (no I/O). Otherwise resolves off-thread.
 */
public suspend fun UUID.resolvePlayer(): OfflinePlayer =
    Bukkit.getPlayer(this)
        ?: withContext(Dispatchers.IO) { Bukkit.getOfflinePlayer(this@resolvePlayer) }

/** Convenience overload for a name-based lookup. Avoid in hot paths — prefer UUID. */
public suspend fun String.offlinePlayerByName(): OfflinePlayer =
    Bukkit.getPlayer(this)
        ?: withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            Bukkit.getOfflinePlayer(this@offlinePlayerByName)
        }
