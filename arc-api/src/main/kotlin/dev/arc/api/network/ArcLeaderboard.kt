package dev.arc.api.network

import java.util.UUID

/**
 * Network-wide leaderboards backed by Redis sorted sets.
 * Key pattern: arc:leaderboard:<name>
 *
 * Pairs naturally with ArcGlobalEconomy — call ArcLeaderboard.sync("coins", uuid, balance)
 * after every economy operation to keep leaderboards current.
 */
object ArcLeaderboard {

    private fun key(name: String) = "arc:leaderboard:$name"

    fun set(name: String, player: UUID, score: Double) =
        ArcRelayClient.zadd(key(name), score, player.toString())

    fun increment(name: String, player: UUID, delta: Double) {
        val current = ArcRelayClient.zscore(key(name), player.toString()) ?: 0.0
        ArcRelayClient.zadd(key(name), current + delta, player.toString())
    }

    fun remove(name: String, player: UUID) =
        ArcRelayClient.zrem(key(name), player.toString())

    fun getRank(name: String, player: UUID): Long? =
        ArcRelayClient.zrevrank(key(name), player.toString())

    fun getScore(name: String, player: UUID): Double? =
        ArcRelayClient.zscore(key(name), player.toString())

    fun getSize(name: String): Long = ArcRelayClient.zcard(key(name))

    /** Returns top N entries as (uuid string → score). Higher score = better rank. */
    fun getTop(name: String, n: Int = 10): List<Pair<String, Double>> {
        val members = ArcRelayClient.zrevrange(key(name), 0L, (n - 1).toLong())
        return members.mapNotNull { member ->
            val score = ArcRelayClient.zscore(key(name), member) ?: return@mapNotNull null
            member to score
        }
    }

    /** Convenience: sync a player's economy balance to the "coins" leaderboard. */
    fun syncEconomy(player: UUID) {
        val balance = ArcGlobalEconomy.getBalance(player).toDouble()
        set("coins", player, balance)
    }
}
