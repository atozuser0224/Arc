package dev.arc.api.event

import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Fired (by [ArcEvents]) when a player double-taps sneak within ~400 ms - the classic "activate ability /
 * toggle combat" gesture, detected once here instead of in every plugin.
 */
public class PlayerDoubleSneakEvent(player: Player) : PlayerEvent(player) {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
