package dev.arc.api.event

import dev.arc.api.perf.LoadLevel
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired (by [ArcEvents]) when the server's [LoadLevel] transitions - e.g. NORMAL → HIGH as tick time climbs.
 * Listen to react to load changes (pause expensive tasks, lower quality, alert staff) without polling.
 */
public class ServerLoadLevelChangeEvent(
    public val previousLevel: LoadLevel,
    public val newLevel: LoadLevel,
    public val mspt: Double,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
