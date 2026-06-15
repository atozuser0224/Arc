package dev.arc.api.event

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Java-friendly bridge for Arc event registration.
 *
 * The Kotlin [listen] extension is `inline reified` and cannot be called from Java at all.
 * Use this class instead:
 *
 * ```java
 * // Basic usage
 * ArcEvents.listen(plugin, PlayerJoinEvent.class, event -> {
 *     event.getPlayer().sendMessage("Welcome!");
 * });
 *
 * // With priority and cancellation filter
 * ArcEvents.listen(
 *     plugin,
 *     BlockBreakEvent.class,
 *     EventPriority.HIGH,
 *     true,           // ignoreCancelled
 *     event -> event.getBlock().setType(Material.AIR)
 * );
 *
 * // Unregister later
 * Listener listener = ArcEvents.listen(plugin, PlayerMoveEvent.class, e -> { ... });
 * ArcEvents.unregister(listener);
 * ```
 */
public object ArcEvents {

    /**
     * Register a [Consumer]-based event handler for [eventClass] bound to [plugin]'s lifecycle.
     *
     * Kotlin callers should prefer [listen] (inline reified) instead.
     *
     * @param plugin         owning plugin; the handler is automatically removed on disable
     * @param eventClass     Bukkit [Event] subclass to listen for
     * @param priority       Bukkit [EventPriority] (default [EventPriority.NORMAL])
     * @param ignoreCancelled skip events whose [Cancellable.isCancelled] is true (default false)
     * @param handler        called with the event instance each time it fires
     * @return the [Listener] token — pass to [unregister] to detach early
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Event> listen(
        plugin: Plugin,
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        handler: Consumer<T>,
    ): Listener {
        val listener = object : Listener {}
        Bukkit.getPluginManager().registerEvent(
            eventClass,
            listener,
            priority,
            { _, event ->
                @Suppress("UNCHECKED_CAST")
                if (eventClass.isInstance(event)) handler.accept(event as T)
            },
            plugin,
            ignoreCancelled,
        )
        return listener
    }

    /** Unregister a [Listener] returned by [listen]. */
    @JvmStatic
    public fun unregister(listener: Listener): Unit = HandlerList.unregisterAll(listener)
}
