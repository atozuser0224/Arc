package dev.arc.server.nms

import dev.arc.api.Arc
import dev.arc.api.player.ClientWorldState
import dev.arc.api.player.ClientWorldStateBackend
import org.bukkit.GameRule
import org.bukkit.entity.Player

/** Reflection-backed packet implementation for per-player time and weather. */
object NmsClientWorldStateBackend : ClientWorldStateBackend {
    private const val GAME_EVENT_PACKET =
        "net.minecraft.network.protocol.game.ClientboundGameEventPacket"
    private const val TIME_PACKET =
        "net.minecraft.network.protocol.game.ClientboundSetTimePacket"

    override fun apply(player: Player, state: ClientWorldState): Boolean {
        var successful = true
        state.dayTime?.let { successful = sendTime(player, it, false) && successful }
        state.rainLevel?.let { successful = sendRain(player, it) && successful }
        state.thunderLevel?.let {
            successful = sendEvent(player, "THUNDER_LEVEL_CHANGE", it) && successful
        }
        return successful
    }

    override fun restore(player: Player): Boolean {
        val world = player.world
        val daylight = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE) ?: true
        val level = Reflect.handleOf(world)
        val rain = (level?.let { Reflect.invoke(it, "getRainLevel", 1f) } as? Number)
            ?.toFloat()
            ?: if (world.hasStorm()) 1f else 0f
        val thunder = (level?.let { Reflect.invoke(it, "getThunderLevel", 1f) } as? Number)
            ?.toFloat()
            ?: if (world.isThundering) 1f else 0f

        return sendTime(player, world.time, daylight) and
            sendRain(player, rain.coerceIn(0f, 1f)) and
            sendEvent(player, "THUNDER_LEVEL_CHANGE", thunder.coerceIn(0f, 1f))
    }

    private fun sendTime(
        player: Player,
        dayTime: Long,
        daylightCycle: Boolean,
    ): Boolean {
        val packetType = Reflect.cls(TIME_PACKET) ?: return false
        val packet = Reflect.construct(
            packetType,
            arrayOf(java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Boolean.TYPE),
            player.world.fullTime,
            dayTime,
            daylightCycle,
        ) ?: return false
        Arc.nms.players.sendPacket(player, packet)
        return true
    }

    private fun sendRain(player: Player, level: Float): Boolean {
        val transition = if (level > 0f) "START_RAINING" else "STOP_RAINING"
        return sendEvent(player, transition, 0f) and
            sendEvent(player, "RAIN_LEVEL_CHANGE", level)
    }

    private fun sendEvent(
        player: Player,
        eventName: String,
        value: Float,
    ): Boolean {
        val packetType = Reflect.cls(GAME_EVENT_PACKET) ?: return false
        val eventField = Reflect.field(packetType, eventName) ?: return false
        val event = Reflect.fieldValue(eventField, null) ?: return false
        val packet = Reflect.constructByArity(packetType, 2, event, value) ?: return false
        Arc.nms.players.sendPacket(player, packet)
        return true
    }
}
