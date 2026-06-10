@file:JvmName("Teams")

package dev.arc.api.team

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team

/**
 * Scoreboard-team helpers for the things teams exist for: name-tag visibility, collision, and grouping -
 * the API-first equivalent of team packets. Operates on the server's main scoreboard.
 */

/** Get or create a team by [name] on the main scoreboard. */
public fun team(name: String): Team {
    val board = Bukkit.getScoreboardManager()?.mainScoreboard ?: error("Scoreboard manager unavailable")
    return board.getTeam(name) ?: board.registerNewTeam(name)
}

/** Whether members' name tags are visible. */
public var Team.nameTagVisible: Boolean
    get() = getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.NEVER
    set(value) {
        setOption(Team.Option.NAME_TAG_VISIBILITY, if (value) Team.OptionStatus.ALWAYS else Team.OptionStatus.NEVER)
    }

/** Whether members collide with others. */
public var Team.collisions: Boolean
    get() = getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER
    set(value) {
        setOption(Team.Option.COLLISION_RULE, if (value) Team.OptionStatus.ALWAYS else Team.OptionStatus.NEVER)
    }

/** Add this player to team [name] (created if needed). */
public fun Player.joinTeam(name: String) {
    team(name).addEntry(this.name)
}

/** Hide this player's name tag by placing them on a dedicated hidden-nametag team. */
public fun Player.hideNametag() {
    val hidden = team("arc-hidden")
    hidden.nameTagVisible = false
    hidden.addEntry(name)
}
