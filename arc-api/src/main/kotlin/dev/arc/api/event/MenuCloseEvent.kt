package dev.arc.api.event

import dev.arc.api.gui.Menu
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Fired when a [viewer] closes an arc [Menu] - a hook for cleanup or "are you sure?" follow-ups. */
public class MenuCloseEvent(
    public val viewer: HumanEntity,
    public val menu: Menu,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
