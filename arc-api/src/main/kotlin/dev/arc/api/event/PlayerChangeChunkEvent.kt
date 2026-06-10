package dev.arc.api.event

import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Fired (by [ArcEvents]) when a player moves from one chunk into another - the hook plugins hand-roll from
 * `PlayerMoveEvent` for region/claim/streaming logic, provided once and cheaply.
 */
public class PlayerChangeChunkEvent(
    player: Player,
    public val fromChunkX: Int,
    public val fromChunkZ: Int,
    public val toChunkX: Int,
    public val toChunkZ: Int,
) : PlayerEvent(player) {

    override fun getHandlers(): HandlerList = HANDLERS

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList = HANDLERS
    }
}
