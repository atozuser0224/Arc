@file:JvmName("Chat")

package dev.arc.api.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Broadcast a MiniMessage-formatted message to all players. */
public fun broadcast(message: String) {
    val component = MiniMessage.miniMessage().deserialize(message)
    Bukkit.getServer().broadcast(component)
}

/** Broadcast a [Component] to all players and the console. */
public fun broadcast(component: Component) {
    Bukkit.getServer().broadcast(component)
}

/** Send a MiniMessage-formatted message to a player. */
public fun Player.msg(message: String) {
    sendMessage(MiniMessage.miniMessage().deserialize(message))
}

/** Send a [Component] to this player and all players within [radius] blocks. */
public fun Player.msgNearby(component: Component, radius: Double) {
    val sq = radius * radius
    world.players
        .filter { it.location.distanceSquared(location) <= sq }
        .forEach { it.sendMessage(component) }
}

/** Strip formatting tags and return plain text. */
public fun String.stripMiniMessage(): String =
    MiniMessage.miniMessage().stripTags(this)

/** True if [other] can see this player's chat (online, not ignoring). */
public fun Player.canChatWith(other: Player): Boolean =
    other.isOnline && other.canSee(this)
