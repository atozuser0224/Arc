@file:JvmName("Sidebars")

package dev.arc.api.scoreboard

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot

/**
 * One-call sidebar scoreboards with Adventure [Component] lines.
 *
 * Building a sidebar by hand means juggling an objective, the SIDEBAR slot, and the age-old trick of using
 * per-line teams (with unique invisible score entries) so two lines can show identical text. [showSidebar]
 * hides all of that: pass a title and up to 15 component lines, top to bottom.
 *
 * ```kotlin
 * player.showSidebar(
 *     title = "<gold><bold>SERVER".mini(),
 *     lines = listOf("Online: 42".mini(), "Ping: 30ms".mini()),
 * )
 * ```
 */

private const val MAX_LINES = 15

/** Replace the player's scoreboard with a sidebar showing [title] and [lines] (capped at 15). */
@Suppress("DEPRECATION")
public fun Player.showSidebar(title: Component, lines: List<Component>) {
    val manager = Bukkit.getScoreboardManager() ?: return
    val board = manager.newScoreboard
    val objective = board.registerNewObjective("arc-sidebar", Criteria.DUMMY, title)
    objective.displaySlot = DisplaySlot.SIDEBAR

    val shown = lines.take(MAX_LINES)
    shown.forEachIndexed { index, line ->
        // Each line needs a unique score entry; reuse the legacy colour codes as invisible tokens, and a
        // per-line team whose prefix carries the actual (component) text.
        val entry = ChatColor.values()[index].toString()
        val team = board.registerNewTeam("arc-line-$index")
        team.addEntry(entry)
        team.prefix(line)
        objective.getScore(entry).score = shown.size - index
    }

    scoreboard = board
}

/** Reset the player back to the server's main scoreboard, clearing any sidebar. */
public fun Player.clearSidebar() {
    val manager = Bukkit.getScoreboardManager() ?: return
    scoreboard = manager.mainScoreboard
}
