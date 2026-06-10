@file:JvmName("PlayerControl")

package dev.arc.api.player

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Player-control helpers over stable Bukkit API - spectator camera, per-viewer visibility (vanish). The
 * "set camera / hide player" packet tricks, but supported.
 */

/** The entity this (spectator) player's camera is attached to. */
public var Player.spectatorTarget: Entity?
    get() = getSpectatorTarget()
    set(value) {
        setSpectatorTarget(value)
    }

/** Hide this player from [other]'s client. */
public fun Player.hideFrom(plugin: Plugin, other: Player) {
    other.hidePlayer(plugin, this)
}

/** Reveal this player to [other]'s client. */
public fun Player.showTo(plugin: Plugin, other: Player) {
    other.showPlayer(plugin, this)
}

/** Hide this player from every other online player. */
public fun Player.vanish(plugin: Plugin) {
    for (viewer in Bukkit.getOnlinePlayers()) {
        if (viewer != this) viewer.hidePlayer(plugin, this)
    }
}

/** Reveal this player to every other online player. */
public fun Player.unvanish(plugin: Plugin) {
    for (viewer in Bukkit.getOnlinePlayers()) {
        if (viewer != this) viewer.showPlayer(plugin, this)
    }
}
