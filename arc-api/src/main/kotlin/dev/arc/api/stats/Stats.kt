@file:JvmName("Stats")

package dev.arc.api.stats

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Statistic
import org.bukkit.entity.Player

/**
 * Statistics & advancements via Bukkit API - read/modify a player's stats and grant/revoke advancements
 * without touching the NMS `ServerStatsCounter` / advancement manager.
 */

/** Read a statistic value. */
public fun Player.stat(statistic: Statistic): Int = getStatistic(statistic)

/** Increment a statistic by [amount]. */
public fun Player.addStat(statistic: Statistic, amount: Int = 1) {
    incrementStatistic(statistic, amount)
}

/** Grant every remaining criterion of the advancement identified by [key] (no-op if unknown). */
public fun Player.grantAdvancement(key: NamespacedKey) {
    val advancement = Bukkit.getAdvancement(key) ?: return
    val progress = getAdvancementProgress(advancement)
    for (criterion in progress.remainingCriteria) {
        progress.awardCriteria(criterion)
    }
}

/** Revoke every awarded criterion of the advancement identified by [key] (no-op if unknown). */
public fun Player.revokeAdvancement(key: NamespacedKey) {
    val advancement = Bukkit.getAdvancement(key) ?: return
    val progress = getAdvancementProgress(advancement)
    for (criterion in progress.awardedCriteria) {
        progress.revokeCriteria(criterion)
    }
}
