package dev.arc.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Fired on this server when a player is transferred to another server via Arc. */
class ArcNetworkPlayerTransferEvent(
    val player: Player,
    val fromServer: String,
    val toServer: String,
    val triggeredBy: String,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired on this server when a global ban/mute is received (from any server). */
class ArcNetworkPlayerBanEvent(
    val uuid: UUID,
    val playerName: String,
    val reason: String,
    val actor: String,
    val expiry: Long?,
    val type: String,
) : Event() {
    val isPermanent: Boolean get() = expiry == null
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired when a player joins a server queue. */
class ArcNetworkQueueJoinEvent(
    val player: Player,
    val targetServer: String,
    val position: Long,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** Fired when a player leaves a server queue. */
class ArcNetworkQueueLeaveEvent(
    val player: Player,
    val targetServer: String,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
