@file:JvmName("Light")

package dev.arc.api.block

import org.bukkit.block.Block

/**
 * Light-level reads over Bukkit API. (Writing light / driving the light engine directly is NMS - reach it
 * via `world.nms` if you must; these cover the common "how bright is here" queries.)
 */

/** Combined light level (0–15) at this block. */
public val Block.light: Int
    get() = lightLevel.toInt()

/** Sky-light component (0–15). */
public val Block.skyLight: Int
    get() = lightFromSky.toInt()

/** Block-light component (0–15). */
public val Block.blockLight: Int
    get() = lightFromBlocks.toInt()
