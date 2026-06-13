@file:JvmName("FakeWorldStates")

package dev.arc.api.player

import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * Inject fake client-side world state (time, weather, fog) to individual players
 * without changing the real world state or affecting other players.
 *
 * Great for story-mode sequences, cinematic cut-scenes, or per-player environmental
 * effects in mini-games.
 *
 * ```kotlin
 * // Show midnight sky only to this player
 * player.sendFakeTime(18000)
 *
 * // Trigger rain + thunder for this player only
 * player.sendFakeWeather(raining = true, thunder = 0.8f)
 *
 * // Sync back to real world state
 * player.syncWorldState()
 * ```
 */

// ── Time ──────────────────────────────────────────────────────────────────────

/**
 * Send [player] a fake day-time value (0–23999).
 * Does not change the real world time or affect other players.
 * Use [syncWorldState] to restore.
 */
fun Player.sendFakeTime(dayTime: Long) {
    val nms = (this as CraftPlayer).handle
    nms.connection.send(ClientboundSetTimePacket(nms.serverLevel().gameTime, dayTime, false))
}

// ── Weather ───────────────────────────────────────────────────────────────────

/**
 * Toggle client-side rain for this player.
 * [intensity] controls rainfall density (0.0–1.0).
 */
fun Player.sendFakeRain(raining: Boolean, intensity: Float = 1.0f) {
    val conn = (this as CraftPlayer).handle.connection
    if (raining) {
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0f))
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, intensity.coerceIn(0f, 1f)))
    } else {
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0f))
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f))
    }
}

/**
 * Set client-side thunder level for this player (0.0 = silent, 1.0 = maximum thunder).
 * Does not imply rain — call [sendFakeRain] separately if needed.
 */
fun Player.sendFakeThunder(level: Float) {
    (this as CraftPlayer).handle.connection.send(
        ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.coerceIn(0f, 1f))
    )
}

/**
 * Convenience: full storm effect for one player only.
 */
fun Player.sendFakeStorm(rainIntensity: Float = 1.0f, thunderLevel: Float = 0.8f) {
    sendFakeRain(true, rainIntensity)
    sendFakeThunder(thunderLevel)
}

/** Clear all client-side weather for this player. */
fun Player.sendFakeClearWeather() {
    val conn = (this as CraftPlayer).handle.connection
    conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0f))
    conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f))
    conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0f))
}

// ── Sync ──────────────────────────────────────────────────────────────────────

/**
 * Re-sync the real world time and weather to this player's client, undoing any
 * fake state sent via [sendFakeTime], [sendFakeRain], or [sendFakeThunder].
 */
fun Player.syncWorldState() {
    val nms = (this as CraftPlayer).handle
    val level = nms.serverLevel()
    val conn = nms.connection

    // Sync time
    conn.send(ClientboundSetTimePacket(level.gameTime, level.dayTime, level.levelData.gameRules.getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)))

    // Sync weather
    if (level.isRaining) {
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0f))
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel))
    } else {
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0f))
        conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f))
    }
    conn.send(ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel))
}
