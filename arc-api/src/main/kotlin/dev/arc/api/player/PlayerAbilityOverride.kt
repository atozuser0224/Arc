@file:JvmName("AbilityOverrides")

package dev.arc.api.player

import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.world.entity.player.Abilities
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * Overrides the ability flags sent to a player's client without changing their
 * actual server-side game mode. Changes are client-only — the server still enforces
 * normal rules, so use this together with real permission hooks.
 *
 * Common uses:
 * - Allow creative-flight in a specific region without switching game mode
 * - Give instabuild to admins while keeping survival experience
 * - Hide the flight bar for spectators in custom game modes
 *
 * ```kotlin
 * // Grant client-side flight
 * player.sendAbilityOverride {
 *     mayFly = true
 *     flying = true
 *     flyingSpeed = 0.1f
 * }
 *
 * // Restore real abilities
 * player.syncAbilities()
 * ```
 */
class AbilityOverrideScope(private val base: Abilities) {
    var invulnerable: Boolean = base.invulnerable
    var isFlying: Boolean = base.flying
    var mayFly: Boolean = base.mayfly
    var instabuild: Boolean = base.instabuild
    var mayBuild: Boolean = base.mayBuild
    var flyingSpeed: Float = base.flyingSpeed
    var walkingSpeed: Float = base.walkingSpeed
}

/**
 * Send a synthetic abilities packet to override what the client thinks.
 * Use [syncAbilities] to restore real state.
 */
fun Player.sendAbilityOverride(block: AbilityOverrideScope.() -> Unit) {
    val nmsPlayer = (this as CraftPlayer).handle
    val scope = AbilityOverrideScope(nmsPlayer.abilities).apply(block)

    // Build a temporary Abilities object for the packet
    val temp = Abilities().also {
        it.invulnerable = scope.invulnerable
        it.flying = scope.isFlying
        it.mayfly = scope.mayFly
        it.instabuild = scope.instabuild
        it.mayBuild = scope.mayBuild
        it.flyingSpeed = scope.flyingSpeed
        it.walkingSpeed = scope.walkingSpeed
    }
    nmsPlayer.connection.send(ClientboundPlayerAbilitiesPacket(temp))
}

/**
 * Re-sync the player's real abilities to their client.
 * Call after [sendAbilityOverride] to restore the actual state.
 */
fun Player.syncAbilities() {
    val nmsPlayer = (this as CraftPlayer).handle
    nmsPlayer.connection.send(ClientboundPlayerAbilitiesPacket(nmsPlayer.abilities))
}

/**
 * Grant client-side flight to a player. Use [syncAbilities] to remove it.
 * Note: server-side anti-cheat still applies — pair with [Player.setAllowFlight] for real flight.
 */
fun Player.sendClientFlight(flying: Boolean = true, speed: Float = 0.05f) =
    sendAbilityOverride {
        mayFly = flying
        isFlying = flying
        flyingSpeed = speed
    }
