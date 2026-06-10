@file:JvmName("Tablist")

package dev.arc.api.tablist

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Per-player tab-list (player list) controls via Paper's Adventure API - header/footer, display name and
 * ping - the API-first equivalent of the player-list packets people reach NMS for.
 */

/** Set this player's tab-list header and footer. */
public fun Player.tabHeaderFooter(header: Component, footer: Component) {
    sendPlayerListHeaderAndFooter(header, footer)
}

/** This player's tab-list display name (null = show their real name). */
public var Player.tabName: Component?
    get() = playerListName()
    set(value) {
        playerListName(value)
    }

/** Round-trip latency in milliseconds, as the server measures it. */
public val Player.ping: Int
    get() = getPing()
