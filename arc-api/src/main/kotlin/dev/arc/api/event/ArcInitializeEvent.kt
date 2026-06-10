package dev.arc.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Fired once when [ArcEvents.enable] is called - a hook for plugins to wire up Arc-dependent features. */
public class ArcInitializeEvent : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
