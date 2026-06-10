@file:JvmName("BossBars")

package dev.arc.api.bossbar

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Thin builders over Adventure boss bars (which Paper exposes directly on any audience).
 *
 * ```kotlin
 * val bar = player.showBossBar("<gold>Loading...".mini(), progress = 0f)
 * bar.progress(0.5f)          // update live
 * player.hideBossBar(bar)     // Adventure member, already available
 * ```
 */

/** Create an Adventure [BossBar]. [progress] is clamped to `0f..1f`. */
public fun bossBar(
    title: Component,
    progress: Float = 1.0f,
    color: BossBar.Color = BossBar.Color.PURPLE,
    overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
): BossBar = BossBar.bossBar(title, progress.coerceIn(0.0f, 1.0f), color, overlay)

/**
 * Build a boss bar and immediately show it to this player, returning it so the caller can update progress or
 * hide it later (via the Adventure `hideBossBar`).
 */
public fun Player.showBossBar(
    title: Component,
    progress: Float = 1.0f,
    color: BossBar.Color = BossBar.Color.PURPLE,
    overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
): BossBar {
    val bar = bossBar(title, progress, color, overlay)
    showBossBar(bar)
    return bar
}
