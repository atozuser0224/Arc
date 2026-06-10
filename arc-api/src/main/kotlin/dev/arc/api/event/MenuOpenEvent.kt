package dev.arc.api.event

import dev.arc.api.gui.Menu
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired before an arc [Menu] opens for a [viewer]; cancel to prevent it. Lets plugins gate/redirect GUI
 * opening (permissions, conditions) without wrapping every `open()` call.
 */
public class MenuOpenEvent(
    public val viewer: HumanEntity,
    public val menu: Menu,
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
