package dev.arc.api.hologram

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.TextDisplay

/**
 * A multi-line hologram: a managed vertical stack of [TextDisplay] entities, top-to-bottom from the origin.
 * Update or remove the whole stack at once.
 *
 * ```kotlin
 * val holo = loc.spawnHologramLines(listOf("<gold>Title".mini(), "<gray>line 2".mini()))
 * holo.update(listOf("<green>Refreshed".mini()))
 * holo.remove()
 * ```
 */
public class Hologram(private val origin: Location, lines: List<Component>) {

    private val displays = mutableListOf<TextDisplay>()

    /** Vertical gap between lines, in blocks. */
    public var lineGap: Double = 0.27

    init {
        render(lines)
    }

    /** Replace all lines. */
    public fun update(lines: List<Component>) {
        render(lines)
    }

    /** Despawn the whole hologram. */
    public fun remove() {
        clear()
    }

    private fun render(lines: List<Component>) {
        clear()
        var location = origin.clone()
        for (line in lines) {
            displays.add(location.spawnHologram(line))
            location = location.clone().subtract(0.0, lineGap, 0.0)
        }
    }

    private fun clear() {
        displays.forEach { it.remove() }
        displays.clear()
    }
}

/** Spawn a multi-line [Hologram] at this location. */
public fun Location.spawnHologramLines(lines: List<Component>): Hologram = Hologram(this, lines)
