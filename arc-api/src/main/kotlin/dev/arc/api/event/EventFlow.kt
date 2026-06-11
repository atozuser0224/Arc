@file:JvmName("EventFlow")

package dev.arc.api.event

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin

/**
 * Returns a cold [Flow] that emits every [T] event while collected.
 * The listener is registered on collection start and unregistered on cancellation.
 *
 * ```kotlin
 * plugin.launch {
 *     plugin.eventFlow<PlayerMoveEvent>()
 *         .filter { it.player == target }
 *         .first() // suspend until target moves once
 *     startGame()
 * }
 * ```
 */
public inline fun <reified T : Event> Plugin.eventFlow(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
): Flow<T> = callbackFlow {
    val listener = listen<T>(priority, ignoreCancelled) { trySend(it) }
    awaitClose { HandlerList.unregisterAll(listener) }
}
