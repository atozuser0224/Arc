@file:JvmName("EntityVisibility")

package dev.arc.api.entity

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Per-player entity visibility via Paper's `hideEntity`/`showEntity` - the safe, API way to make an entity
 * visible to only some players (personal mobs, per-player holograms, "ghost" effects) without raw spawn/remove
 * packets. Spawn a real entity, hide it from everyone, then reveal it to whom you choose.
 */

/** Hide this entity from [viewer]'s client. */
public fun Entity.hideFrom(plugin: Plugin, viewer: Player) {
    viewer.hideEntity(plugin, this)
}

/** Reveal this entity to [viewer]'s client. */
public fun Entity.showTo(plugin: Plugin, viewer: Player) {
    viewer.showEntity(plugin, this)
}

/** Hide this entity from every online player. */
public fun Entity.hideFromAll(plugin: Plugin) {
    for (viewer in Bukkit.getOnlinePlayers()) viewer.hideEntity(plugin, this)
}

/** Reveal this entity to every online player. */
public fun Entity.showToAll(plugin: Plugin) {
    for (viewer in Bukkit.getOnlinePlayers()) viewer.showEntity(plugin, this)
}
