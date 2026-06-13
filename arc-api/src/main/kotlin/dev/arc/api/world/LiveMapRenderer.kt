@file:JvmName("LiveMaps")

package dev.arc.api.world

import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.saveddata.maps.MapDecorationType
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.map.CraftMapView
import org.bukkit.entity.Player
import org.bukkit.map.MapView
import org.bukkit.plugin.Plugin
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Server-side programmatic map renderer. Draws pixels directly to a [MapView]
 * and pushes updates to players without requiring any client-side rendering.
 *
 * ```kotlin
 * val canvas = plugin.liveMap()
 *
 * // Draw a red rectangle
 * canvas.fill(Color.BLACK)
 * canvas.rect(10, 10, 50, 50, Color.RED)
 * canvas.text(15, 15, "Score: 42", Color.WHITE)
 * canvas.flush(player)
 *
 * // Get the item to give to the player
 * val mapItem = canvas.toItemStack()
 * player.inventory.addItem(mapItem)
 * ```
 */
class LiveMapRenderer(val plugin: Plugin) {

    val mapView: MapView = Bukkit.createMap(Bukkit.getWorlds().first())
    private val pixels = ByteArray(MAP_SIZE * MAP_SIZE)

    init {
        // Remove default renderers
        mapView.renderers.toList().forEach { mapView.removeRenderer(it) }
    }

    // ── Drawing API ───────────────────────────────────────────────────────────

    /** Fill the entire canvas with [color]. */
    fun fill(color: Color) {
        val mapped = mapColor(color)
        pixels.fill(mapped)
    }

    /** Set a single pixel at ([x], [y]) to [color]. Coordinates are 0–127. */
    fun setPixel(x: Int, y: Int, color: Color) {
        if (x !in 0 until MAP_SIZE || y !in 0 until MAP_SIZE) return
        pixels[y * MAP_SIZE + x] = mapColor(color)
    }

    /** Draw a filled rectangle from ([x1], [y1]) to ([x2], [y2]) in [color]. */
    fun rect(x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
        val mc = mapColor(color)
        for (y in y1.coerceIn(0, MAP_SIZE - 1)..y2.coerceIn(0, MAP_SIZE - 1)) {
            for (x in x1.coerceIn(0, MAP_SIZE - 1)..x2.coerceIn(0, MAP_SIZE - 1)) {
                pixels[y * MAP_SIZE + x] = mc
            }
        }
    }

    /** Draw a filled circle centred at ([cx], [cy]) with [radius] in [color]. */
    fun circle(cx: Int, cy: Int, radius: Int, color: Color) {
        val mc = mapColor(color)
        val r2 = radius * radius
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= r2) {
                    val px = (cx + dx).coerceIn(0, MAP_SIZE - 1)
                    val py = (cy + dy).coerceIn(0, MAP_SIZE - 1)
                    pixels[py * MAP_SIZE + px] = mc
                }
            }
        }
    }

    /** Draw a horizontal line from ([x1], [y]) to ([x2], [y]) in [color]. */
    fun hLine(x1: Int, x2: Int, y: Int, color: Color) {
        val mc = mapColor(color)
        for (x in x1.coerceIn(0, MAP_SIZE - 1)..x2.coerceIn(0, MAP_SIZE - 1)) {
            val yc = y.coerceIn(0, MAP_SIZE - 1)
            pixels[yc * MAP_SIZE + x] = mc
        }
    }

    /** Copy a [BufferedImage] onto the canvas at ([offsetX], [offsetY]). */
    fun drawImage(img: BufferedImage, offsetX: Int = 0, offsetY: Int = 0) {
        for (y in 0 until img.height.coerceAtMost(MAP_SIZE - offsetY)) {
            for (x in 0 until img.width.coerceAtMost(MAP_SIZE - offsetX)) {
                val px = offsetX + x; val py = offsetY + y
                if (px in 0 until MAP_SIZE && py in 0 until MAP_SIZE) {
                    val argb = img.getRGB(x, y)
                    val a = (argb ushr 24) and 0xFF
                    if (a > 128) pixels[py * MAP_SIZE + px] = mapColor(Color(argb, true))
                }
            }
        }
    }

    // ── Flush ─────────────────────────────────────────────────────────────────

    /**
     * Send the current canvas state to [players].
     * Call after any drawing operations to push the update.
     */
    fun flush(vararg players: Player) {
        val mapId = (mapView as CraftMapView).handle.id
        val patch = MapItemSavedData.MapPatch(0, 0, MAP_SIZE, MAP_SIZE, pixels.clone())
        val packet = ClientboundMapItemDataPacket(
            net.minecraft.world.level.saveddata.maps.MapId(mapId),
            0.toByte(), false, emptyList(), patch
        )
        players.forEach { (it as CraftPlayer).handle.connection.send(packet) }
    }

    // ── Item ──────────────────────────────────────────────────────────────────

    /** Return a map [ItemStack] backed by this renderer. Give it to players so they can hold it. */
    fun toItemStack(): org.bukkit.inventory.ItemStack =
        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(
            net.minecraft.world.item.MapItem.create(mapView.id.toLong())
        )

    // ── Color mapping ─────────────────────────────────────────────────────────

    private fun mapColor(color: Color): Byte {
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in 4 until 256) {
            val mc = net.minecraft.world.level.material.MapColor.byId(i / 4) ?: continue
            val shadeIdx = i % 4
            val shade = SHADE_MULTIPLIERS[shadeIdx]
            val mr = (mc.col ushr 16 and 0xFF) * shade / 255
            val mg = (mc.col ushr 8 and 0xFF) * shade / 255
            val mb = (mc.col and 0xFF) * shade / 255
            val dr = color.red - mr; val dg = color.green - mg; val db = color.blue - mb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) { bestDist = dist; best = i }
        }
        return best.toByte()
    }

    companion object {
        const val MAP_SIZE = 128
        private val SHADE_MULTIPLIERS = intArrayOf(180, 220, 255, 135)
    }
}

/** Create a new [LiveMapRenderer] for this plugin. */
fun Plugin.liveMap(): LiveMapRenderer = LiveMapRenderer(this)
