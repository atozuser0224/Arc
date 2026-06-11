@file:JvmName("Huds")

package dev.arc.api.hud

import dev.arc.api.coroutine.launch
import dev.arc.api.coroutine.delayTicks
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective

private val mm = MiniMessage.miniMessage()
private fun String.mm(): Component = mm.deserialize(this)

// ---------------------------------------------------------------------------
//  DSL scopes
// ---------------------------------------------------------------------------

class SidebarScope {
    internal val lines = mutableListOf<() -> String>()
    internal var titleProvider: () -> String = { "" }

    fun title(provider: () -> String) { titleProvider = provider }
    fun title(text: String)           { titleProvider = { text } }
    fun line(provider: () -> String)  { lines += provider }
    fun line(text: String)            { lines += { text } }
    fun blank()                       { lines += { "" } }
}

class ActionBarScope {
    internal var provider: () -> String = { "" }
    operator fun invoke(text: String)            { provider = { text } }
    operator fun invoke(provider: () -> String)  { this.provider = provider }
}

class BossBarScope {
    internal var titleProvider: () -> String    = { "" }
    internal var progressProvider: () -> Float  = { 1f }
    internal var color: BossBar.Color           = BossBar.Color.WHITE
    internal var overlay: BossBar.Overlay       = BossBar.Overlay.PROGRESS

    fun title(provider: () -> String)    { titleProvider = provider }
    fun title(text: String)              { titleProvider = { text } }
    fun progress(provider: () -> Float)  { progressProvider = provider }
    fun color(c: BossBar.Color)          { color = c }
    fun overlay(o: BossBar.Overlay)      { overlay = o }
}

class HudBuilder {
    internal var sidebarScope:    SidebarScope?    = null
    internal var actionBarScope:  ActionBarScope?  = null
    internal var bossBarScope:    BossBarScope?    = null
    /** Ticks between each refresh. Default 4. */
    var refreshTicks: Long = 4L

    fun sidebar(block: SidebarScope.() -> Unit)   { sidebarScope   = SidebarScope().apply(block) }
    fun actionbar(text: String)                    { actionBarScope = ActionBarScope().also { it(text) } }
    fun actionbar(provider: () -> String)          { actionBarScope = ActionBarScope().also { it(provider) } }
    fun bossbar(block: BossBarScope.() -> Unit)    { bossBarScope   = BossBarScope().apply(block) }
}

// ---------------------------------------------------------------------------
//  HUD controller
// ---------------------------------------------------------------------------

/**
 * A live HUD tied to one [player]. Call [show] to start the update loop,
 * [hide] to clear all elements without stopping the loop, [destroy] to stop.
 *
 * ```kotlin
 * val hud = plugin.hud(player) {
 *     sidebar {
 *         title { "§6MyServer" }
 *         line  { "§eTPS: §f${tps()}" }
 *         blank()
 *         line  { "§7${player.name}" }
 *     }
 *     actionbar { "§bMana §f${player.data<ManaData>().current.toInt()}" }
 *     bossbar {
 *         title { "§cBoss" }
 *         progress { boss.health / boss.maxHealth }
 *         color(BossBar.Color.RED)
 *     }
 *     refreshTicks = 4L
 * }
 * hud.show()
 * ```
 */
class Hud internal constructor(
    private val plugin: Plugin,
    private val player: Player,
    private val builder: HudBuilder,
) {
    private var job: Job? = null
    private var bossBar: BossBar? = null
    private var objective: Objective? = null

    fun show() {
        if (job?.isActive == true) return
        builder.bossBarScope?.let {
            bossBar = BossBar.bossBar(
                it.titleProvider().mm(), it.progressProvider(), it.color, it.overlay,
            ).also { bb -> player.showBossBar(bb) }
        }
        job = plugin.launch {
            while (isActive && player.isOnline) {
                render()
                plugin.delayTicks(builder.refreshTicks)
            }
        }
    }

    fun hide() {
        clearSidebar()
        player.sendActionBar(Component.empty())
        bossBar?.let { player.hideBossBar(it) }
    }

    fun destroy() {
        job?.cancel()
        job = null
        hide()
        bossBar = null
    }

    private fun render() {
        builder.sidebarScope?.let { renderSidebar(it) }
        builder.actionBarScope?.let { player.sendActionBar(it.provider().mm()) }
        builder.bossBarScope?.let { scope ->
            bossBar?.also {
                it.name(scope.titleProvider().mm())
                it.progress(scope.progressProvider().coerceIn(0f, 1f))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun renderSidebar(scope: SidebarScope) {
        val board = player.scoreboard.takeIf { it != plugin.server.scoreboardManager.mainScoreboard }
            ?: plugin.server.scoreboardManager.newScoreboard

        val obj = board.getObjective("arc_hud")
            ?: board.registerNewObjective("arc_hud", Criteria.DUMMY, scope.titleProvider().mm())
                .also { it.displaySlot = DisplaySlot.SIDEBAR }

        obj.displayName(scope.titleProvider().mm())

        val lines = scope.lines.map { it() }.take(15)
        val existing = board.entries.toMutableSet()

        lines.forEachIndexed { i, text ->
            val score = lines.size - i
            val entry = "${ChatColor.values().getOrElse(i) { ChatColor.WHITE }}$text"
            obj.getScore(entry).score = score
            existing.remove(entry)
        }
        existing.forEach { board.resetScores(it) }

        if (player.scoreboard !== board) player.scoreboard = board
        objective = obj
    }

    private fun clearSidebar() {
        runCatching {
            player.scoreboard.getObjective("arc_hud")?.unregister()
        }
    }
}

// ---------------------------------------------------------------------------

/** Build and return a [Hud] for [player]. Call [Hud.show] to start it. */
public fun Plugin.hud(player: Player, block: HudBuilder.() -> Unit): Hud =
    Hud(this, player, HudBuilder().apply(block))
