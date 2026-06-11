@file:JvmName("Displays")

package dev.arc.api.display

import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Display.Brightness
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation

/** Spawn a [TextDisplay] at [location] and configure it via [block]. */
public fun World.spawnText(location: Location, text: Component, block: TextDisplay.() -> Unit = {}): TextDisplay =
    (spawnEntity(location, EntityType.TEXT_DISPLAY) as TextDisplay).also {
        it.text(text)
        it.billboard = Billboard.CENTER
        it.isSeeThrough = false
        block(it)
    }

/** Spawn a [BlockDisplay] at [location] and configure it via [block]. */
public fun World.spawnBlockDisplay(
    location: Location,
    material: org.bukkit.Material,
    block: BlockDisplay.() -> Unit = {},
): BlockDisplay = (spawnEntity(location, EntityType.BLOCK_DISPLAY) as BlockDisplay).also {
    it.block = material.createBlockData()
    block(it)
}

/** Spawn an [ItemDisplay] at [location] and configure it via [block]. */
public fun World.spawnItemDisplay(
    location: Location,
    item: ItemStack,
    block: ItemDisplay.() -> Unit = {},
): ItemDisplay = (spawnEntity(location, EntityType.ITEM_DISPLAY) as ItemDisplay).also {
    it.setItemStack(item)
    it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.GROUND
    block(it)
}

/** Set a [TextDisplay]'s background color (ARGB). */
public fun TextDisplay.backgroundColor(argb: Int) {
    backgroundColor = Color.fromARGB(argb)
}

/** Apply [Brightness] (block=15, sky=15 = fully lit). */
public fun Display.brightness(block: Int = 15, sky: Int = 15) {
    brightness = Brightness(block, sky)
}

/** Scale this display entity uniformly. */
public fun Display.scale(factor: Float) {
    val t = transformation
    transformation = Transformation(
        t.translation, t.leftRotation,
        org.joml.Vector3f(factor, factor, factor),
        t.rightRotation,
    )
}

/** Translate (offset) the display relative to its entity origin. */
public fun Display.translate(x: Float, y: Float, z: Float) {
    val t = transformation
    transformation = Transformation(
        org.joml.Vector3f(x, y, z), t.leftRotation, t.scale, t.rightRotation,
    )
}
