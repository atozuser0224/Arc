package dev.arc.api.player

import dev.arc.api.text.mm
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration

/** Send a MiniMessage string straight to any audience. */
fun CommandSender.send(mini: String) = sendMessage(mini.mm())

/** Send a pre-built component. */
fun CommandSender.send(component: Component) = sendMessage(component)

/** Action-bar a MiniMessage string. */
fun Player.actionBar(mini: String) = sendActionBar(mini.mm())

/**
 * Show a title from MiniMessage strings with sane default timings.
 *
 * ```
 * player.title("<gold>Welcome", "<gray>to the server")
 * ```
 */
fun Player.title(
    title: String,
    subtitle: String = "",
    fadeIn: Duration = Duration.ofMillis(250),
    stay: Duration = Duration.ofSeconds(3),
    fadeOut: Duration = Duration.ofMillis(250),
) = showTitle(
    Title.title(title.mm(), subtitle.mm(), Title.Times.times(fadeIn, stay, fadeOut)),
)
