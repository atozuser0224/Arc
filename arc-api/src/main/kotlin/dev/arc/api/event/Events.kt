@file:JvmName("Events")

package dev.arc.api.event

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
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
