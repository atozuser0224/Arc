@file:JvmName("Holograms")

package dev.arc.api.hologram

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

/**
 * Holograms via real (but cheap, AI-less) [TextDisplay] entities - the modern, API-first replacement for
 * packet-armor-stand holograms. They persist, render crisply, and need no per-player packet bookkeeping.
 *
 * ```kotlin
 * val holo = loc.spawnHologram("<gold>Welcome!".mini())
 * holo.content = "<green>Updated".mini()
 * ```
 */

/** Spawn a billboarded text hologram at this location. */
public fun Location.spawnHologram(text: Component): TextDisplay {
    val world = world ?: error("Location has no world")
    val display = world.spawn(this, TextDisplay::class.java)
    display.text(text)
    display.billboard = Display.Billboard.CENTER
    return display
}

/** Read/replace the displayed text. */
public var TextDisplay.content: Component
    get() = text()
    set(value) {
        text(value)
    }
