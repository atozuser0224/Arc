package dev.arc.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired (by [ArcEvents]) when a sampled tick time exceeds the configured spike threshold. Useful for
 * logging/diagnostics - capture what was happening at the moment of a stall.
 */
public class ServerLagSpikeEvent(
    /** The measured milliseconds-per-tick that triggered this. */
    public val mspt: Double,
    /** The threshold that was crossed. */
    public val threshold: Double,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
