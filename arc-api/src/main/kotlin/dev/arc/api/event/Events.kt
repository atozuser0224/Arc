@file:JvmName("Events")

package dev.arc.api.event

import dev.arc.api.coroutine.launch
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/**
 * Register an inline event listener for [T] bound to this plugin's lifecycle.
 * The listener is unregistered automatically when the plugin disables.
 *
 * ```kotlin
 * plugin.listen<PlayerJoinEvent> { event ->
 *     event.player.sendMessage("Welcome!")
 * }
 * ```
 */
public inline fun <reified T : Event> Plugin.listen(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    crossinline handler: (T) -> Unit,
): Listener {
    val listener = object : Listener {}
    Bukkit.getPluginManager().registerEvent(
        T::class.java,
        listener,
        priority,
        { _, event -> if (event is T) handler(event) },
        this,
        ignoreCancelled,
    )
    return listener
}

/**
 * Like [listen] but the handler is a suspend function — a coroutine is launched on the plugin's
 * main-thread scope for each matching event.
 *
 * ```kotlin
 * plugin.listenSuspend<PlayerJoinEvent> { event ->
 *     val data = fetchFromDb(event.player.uniqueId) // suspend OK
 *     event.player.sendMessage("coins: ${data.coins}")
 * }
 * ```
 */
public inline fun <reified T : Event> Plugin.listenSuspend(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    crossinline handler: suspend (T) -> Unit,
): Listener {
    val plugin = this
    val listener = object : Listener {}
    Bukkit.getPluginManager().registerEvent(
        T::class.java,
        listener,
        priority,
        { _, event -> if (event is T) plugin.launch { handler(event) } },
        this,
        ignoreCancelled,
    )
    return listener
}

/** Unregister a listener returned by [listen] or [listenSuspend]. */
public fun Listener.unregister(): Unit = HandlerList.unregisterAll(this)
