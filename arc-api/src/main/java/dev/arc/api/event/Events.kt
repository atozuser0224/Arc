package dev.arc.api.event

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin

/**
 * Register a typed event handler with a single lambda — no `Listener` class,
 * no `@EventHandler` annotation, no boilerplate.
 *
 * ```
 * plugin.on<PlayerJoinEvent> { it.joinMessage(null) }
 * plugin.on<BlockBreakEvent>(priority = EventPriority.HIGH, ignoreCancelled = true) { e ->
 *     e.isCancelled = e.block.type == Material.BEDROCK
 * }
 * ```
 *
 * @return the synthetic [Listener] so the caller can later unregister it.
 */
inline fun <reified T : Event> Plugin.on(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    crossinline handler: (T) -> Unit,
): Listener {
    val listener = object : Listener {}
    val executor = EventExecutor { _, event ->
        if (event is T) handler(event)
    }
    server.pluginManager.registerEvent(T::class.java, listener, priority, executor, this, ignoreCancelled)
    return listener
}
