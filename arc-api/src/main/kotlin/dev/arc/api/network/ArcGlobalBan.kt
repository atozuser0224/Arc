package dev.arc.api.network

import java.time.Instant
import java.util.UUID

data class ArcBanEntry(
    val uuid: String,
    val playerName: String,
    val reason: String,
    val actor: String,
    val timestamp: Long,
    val expiry: Long?,
    val type: String = "BAN",
)

/**
 * Cross-server ban/mute/ip-ban system backed by Redis.
 * Ban events are broadcast to all online servers via arc:inbox:ban:<id>.
 */
object ArcGlobalBan {

    private fun banKey(uuid: UUID) = "arc:ban:player:$uuid"
    private fun muteKey(uuid: UUID) = "arc:mute:player:$uuid"
    private fun ipBanKey(ip: String) = "arc:ban:ip:${ip.replace(":", "_")}"

    // ---- Ban ----

    fun ban(uuid: UUID, playerName: String, reason: String, actor: String, durationSeconds: Long? = null): ArcBanEntry {
        val expiry = durationSeconds?.let { Instant.now().epochSecond + it }
        val entry = ArcBanEntry(uuid.toString(), playerName, reason, actor, Instant.now().epochSecond, expiry, "BAN")
        store(banKey(uuid), entry, durationSeconds)
        ArcNetworkAudit.log("ban.player", mapOf("uuid" to uuid.toString(), "player" to playerName,
            "reason" to reason, "actor" to actor, "expiry" to (expiry?.toString() ?: "permanent")))
        broadcastBan(entry)
        return entry
    }

    fun unban(uuid: UUID) {
        ArcRelayClient.del(banKey(uuid))
        ArcNetworkAudit.log("unban.player", mapOf("uuid" to uuid.toString()))
    }

    fun isBanned(uuid: UUID): Boolean {
        val entry = getBan(uuid) ?: return false
        if (entry.expiry != null && entry.expiry < Instant.now().epochSecond) {
            ArcRelayClient.del(banKey(uuid)); return false
        }
        return true
    }

    fun getBan(uuid: UUID): ArcBanEntry? = ArcRelayClient.get(banKey(uuid))?.let { deserialize(it) }

    // ---- Mute ----

    fun mute(uuid: UUID, playerName: String, reason: String, actor: String, durationSeconds: Long? = null): ArcBanEntry {
        val expiry = durationSeconds?.let { Instant.now().epochSecond + it }
        val entry = ArcBanEntry(uuid.toString(), playerName, reason, actor, Instant.now().epochSecond, expiry, "MUTE")
        store(muteKey(uuid), entry, durationSeconds)
        ArcNetworkAudit.log("mute.player", mapOf("uuid" to uuid.toString(), "player" to playerName,
            "reason" to reason, "actor" to actor))
        return entry
    }

    fun unmute(uuid: UUID) = ArcRelayClient.del(muteKey(uuid))

    fun isMuted(uuid: UUID): Boolean {
        val entry = getMute(uuid) ?: return false
        if (entry.expiry != null && entry.expiry < Instant.now().epochSecond) {
            ArcRelayClient.del(muteKey(uuid)); return false
        }
        return true
    }

    fun getMute(uuid: UUID): ArcBanEntry? = ArcRelayClient.get(muteKey(uuid))?.let { deserialize(it) }

    // ---- IP Ban ----

    fun ipBan(ip: String, reason: String, actor: String, durationSeconds: Long? = null) {
        val expiry = durationSeconds?.let { Instant.now().epochSecond + it }
        val entry = ArcBanEntry(ip, ip, reason, actor, Instant.now().epochSecond, expiry, "IP_BAN")
        store(ipBanKey(ip), entry, durationSeconds)
        ArcNetworkAudit.log("ban.ip", mapOf("ip" to ip, "reason" to reason, "actor" to actor))
    }

    fun unbanIp(ip: String) = ArcRelayClient.del(ipBanKey(ip))

    fun isIpBanned(ip: String): Boolean {
        val raw = ArcRelayClient.get(ipBanKey(ip)) ?: return false
        val entry = deserialize(raw) ?: return false
        if (entry.expiry != null && entry.expiry < Instant.now().epochSecond) {
            ArcRelayClient.del(ipBanKey(ip)); return false
        }
        return true
    }

    // ---- Internal ----

    private fun store(key: String, entry: ArcBanEntry, durationSeconds: Long?) {
        val json = serialize(entry)
        if (durationSeconds != null) {
            ArcRelayClient.setex(key, durationSeconds.toInt().coerceAtLeast(1), json)
        } else {
            ArcRelayClient.set(key, json)
        }
    }

    private fun broadcastBan(entry: ArcBanEntry) {
        val payload = serialize(entry)
        ArcServerRegistry.onlineServerIds()
            .filter { it != ArcNetworkConfig.serverId }
            .forEach { ArcRelayClient.lpush("arc:inbox:ban:$it", payload) }
    }

    private fun serialize(e: ArcBanEntry): String {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val expiryStr = if (e.expiry != null) e.expiry.toString() else "null"
        return """{"uuid":"${e.uuid.esc()}","playerName":"${e.playerName.esc()}","reason":"${e.reason.esc()}","actor":"${e.actor.esc()}","timestamp":${e.timestamp},"expiry":$expiryStr,"type":"${e.type}"}"""
    }

    fun deserialize(json: String): ArcBanEntry? = runCatching {
        fun str(key: String) = """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(json)?.groupValues?.get(1)
        fun lng(key: String) = """"$key"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toLongOrNull()
        ArcBanEntry(
            uuid = str("uuid") ?: return@runCatching null,
            playerName = str("playerName") ?: "",
            reason = str("reason") ?: "",
            actor = str("actor") ?: "SYSTEM",
            timestamp = lng("timestamp") ?: 0L,
            expiry = lng("expiry"),
            type = str("type") ?: "BAN",
        )
    }.getOrNull()
}
