@file:JvmName("Titles")

package dev.arc.api.title

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Title / action-bar helpers over Adventure (which Paper sends as the proper client packets) - per-player,
 * no NMS. Times are in ticks for familiarity.
 */

/** Show a title + subtitle with tick-based timings. */
public fun Player.title(
    title: Component,
    subtitle: Component = Component.empty(),
    fadeInTicks: Long = 10,
    stayTicks: Long = 70,
    fadeOutTicks: Long = 20,
) {
    showTitle(
        Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50),
                Duration.ofMillis(stayTicks * 50),
                Duration.ofMillis(fadeOutTicks * 50),
            ),
        ),
    )
}

/** Send an action-bar message. */
public fun Player.actionBar(message: Component) {
    sendActionBar(message)
}
