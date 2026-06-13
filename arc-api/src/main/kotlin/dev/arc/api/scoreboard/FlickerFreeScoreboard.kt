@file:JvmName("FlickerFreeScoreboards")

package dev.arc.api.scoreboard

import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import io.papermc.paper.adventure.PaperAdventure
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Flicker-free per-player sidebar scoreboard via NMS packets.
 *
 * The standard Bukkit sidebar API flickers whenever a score value changes because
 * the client receives a "remove score" + "add score" pair. This implementation
 * sends only a team-prefix update packet when a line changes — zero flicker.
 *
 * Each player gets their own objective and team set, fully independent.
 *
 * ```kotlin
 * val sb = plugin.flickerFreeScoreboard()
 *
 * // Show to a player
 * sb.show(player, title = Component.text("§6§lRAID SERVER"),
 *     lines = listOf(
 *         Component.text("§7Zone: §fOverworld"),
 *         Component.text("§7Kills: §c42"),
 *     ))
 *
 * // Update a single line without recreating the board (no flicker)
 * sb.updateLine(player, index = 1, Component.text("§7Kills: §c43"))
 *
 * // Remove
 * sb.hide(player)
 * ```
 */
class FlickerFreeScoreboard(private val plugin: Plugin) {

    private companion object {
        const val OBJECTIVE_ID = "arc_ff"
        const val MAX_LINES = 15
        // Unique invisible entry per line slot (using § colour codes as dummy names)
        fun lineEntry(index: Int): String = "§$index"
        fun teamId(playerId: UUID, index: Int): String = "arc_ff_${playerId.toString().take(8)}_$index"
    }

    private data class BoardState(val title: Component, val lines: List<Component>)
    private val states = ConcurrentHashMap<UUID, BoardState>()

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Show or replace the sidebar for [player]. */
    fun show(player: Player, title: Component, lines: List<Component>) {
        val existing = states[player.uniqueId]
        if (existing == null) {
            createBoard(player, title, lines.take(MAX_LINES))
        } else {
            updateBoard(player, existing, title, lines.take(MAX_LINES))
        }
        states[player.uniqueId] = BoardState(title, lines.take(MAX_LINES))
    }

    /** Update a single [line] at [index] without touching other lines (no flicker). */
    fun updateLine(player: Player, index: Int, line: Component) {
        val state = states[player.uniqueId] ?: return
        if (index !in state.lines.indices) return
        val newLines = state.lines.toMutableList().also { it[index] = line }
        sendTeamPrefix(player, index, line)
        states[player.uniqueId] = state.copy(lines = newLines)
    }

    /** Update the title without recreating the board. */
    fun updateTitle(player: Player, title: Component) {
        val state = states[player.uniqueId] ?: return
        sendObjectiveUpdate(player, title)
        states[player.uniqueId] = state.copy(title = title)
    }

    /** Remove the sidebar from [player]. */
    fun hide(player: Player) {
        val state = states.remove(player.uniqueId) ?: return
        destroyBoard(player, state.lines.size)
    }

    /** Whether [player] has an active sidebar. */
    fun isShowing(player: Player): Boolean = player.uniqueId in states

    // ── Board lifecycle ────────────────────────────────────────────────────────

    private fun createBoard(player: Player, title: Component, lines: List<Component>) {
        val conn = (player as CraftPlayer).handle.connection
        val nmsTitle = PaperAdventure.asVanilla(title)

        // 1. Create objective
        conn.send(ClientboundSetObjectivePacket(
            OBJECTIVE_ID, 0, nmsTitle,
            ObjectiveCriteria.RenderType.INTEGER, Optional.empty()
        ))

        // 2. Create a team + score entry per line
        lines.forEachIndexed { i, line ->
            createTeamAndScore(player, i, line, lines.size - i)
        }

        // 3. Show in sidebar — Objective reference required by the packet constructor
        val tmpObj = Objective(Scoreboard(), OBJECTIVE_ID, ObjectiveCriteria.DUMMY, nmsTitle,
            ObjectiveCriteria.RenderType.INTEGER, false, null)
        conn.send(net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket(
            DisplaySlot.SIDEBAR, tmpObj
        ))
    }

    private fun updateBoard(player: Player, old: BoardState, newTitle: Component, newLines: List<Component>) {
        if (old.title != newTitle) sendObjectiveUpdate(player, newTitle)

        val maxLen = maxOf(old.lines.size, newLines.size)
        for (i in 0 until maxLen) {
            when {
                i >= old.lines.size -> createTeamAndScore(player, i, newLines[i], newLines.size - i)
                i >= newLines.size -> removeTeamAndScore(player, i)
                old.lines[i] != newLines[i] -> sendTeamPrefix(player, i, newLines[i])
            }
        }
    }

    private fun destroyBoard(player: Player, lineCount: Int) {
        val conn = (player as CraftPlayer).handle.connection
        // Clear display slot before removing objective so the client doesn't keep a ghost sidebar
        conn.send(net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket(
            DisplaySlot.SIDEBAR, null
        ))
        for (i in 0 until lineCount) {
            val entry = lineEntry(i)
            conn.send(ClientboundResetScorePacket(entry, OBJECTIVE_ID))
            conn.send(ClientboundSetPlayerTeamPacket.createRemovePacket(
                net.minecraft.world.scores.PlayerTeam(Scoreboard(), teamId(player.uniqueId, i))
            ))
        }
        conn.send(ClientboundSetObjectivePacket(OBJECTIVE_ID, 1, PaperAdventure.asVanilla(Component.empty()),
            ObjectiveCriteria.RenderType.INTEGER, Optional.empty()))
    }

    // ── Per-line helpers ───────────────────────────────────────────────────────

    private fun createTeamAndScore(player: Player, index: Int, line: Component, score: Int) {
        val conn = (player as CraftPlayer).handle.connection
        val entry = lineEntry(index)
        val teamName = teamId(player.uniqueId, index)

        val team = net.minecraft.world.scores.PlayerTeam(Scoreboard(), teamName)
        team.playerPrefix = PaperAdventure.asVanilla(line)
        conn.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true))
        conn.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, entry,
            ClientboundSetPlayerTeamPacket.Action.ADD))

        conn.send(ClientboundSetScorePacket(entry, OBJECTIVE_ID, score, Optional.empty(), Optional.empty()))
    }

    private fun removeTeamAndScore(player: Player, index: Int) {
        val conn = (player as CraftPlayer).handle.connection
        val entry = lineEntry(index)
        conn.send(ClientboundResetScorePacket(entry, OBJECTIVE_ID))
        conn.send(ClientboundSetPlayerTeamPacket.createRemovePacket(
            net.minecraft.world.scores.PlayerTeam(Scoreboard(), teamId(player.uniqueId, index))
        ))
    }

    private fun sendTeamPrefix(player: Player, index: Int, line: Component) {
        val conn = (player as CraftPlayer).handle.connection
        val team = net.minecraft.world.scores.PlayerTeam(Scoreboard(), teamId(player.uniqueId, index))
        team.playerPrefix = PaperAdventure.asVanilla(line)
        conn.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false))
    }

    private fun sendObjectiveUpdate(player: Player, title: Component) {
        (player as CraftPlayer).handle.connection.send(
            ClientboundSetObjectivePacket(OBJECTIVE_ID, 2, PaperAdventure.asVanilla(title),
                ObjectiveCriteria.RenderType.INTEGER, Optional.empty())
        )
    }
}

/** Create a [FlickerFreeScoreboard] for this plugin. */
fun Plugin.flickerFreeScoreboard(): FlickerFreeScoreboard = FlickerFreeScoreboard(this)
