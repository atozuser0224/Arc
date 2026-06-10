@file:JvmName("EntityEffects")

package dev.arc.api.entity

import org.bukkit.Location
import org.bukkit.entity.LightningStrike

/**
 * Spawn-time world effects over Bukkit API - lightning (real or cosmetic).
 */

/** Strike real (damaging, fire-starting) lightning here. */
public fun Location.strikeLightning(): LightningStrike? = world?.strikeLightning(this)

/** Strike cosmetic lightning (visual + sound only, no damage) here. */
public fun Location.strikeLightningEffect(): LightningStrike? = world?.strikeLightningEffect(this)
