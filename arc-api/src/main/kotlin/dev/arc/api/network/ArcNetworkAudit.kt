package dev.arc.api.network

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Network audit log — records all cross-server operations.
 *
 * Fast path: in-memory ring buffer (MAX_BUFFER entries) for the current session.
 * Persistence: each entry is also written to Redis ZSet arc:audit:net scored by
 * timestamp, enabling cross-server audit history that survives restarts.
 * Pruning: entries older than AuditConfig.retentionDays are removed on startup
 * and when pruneOld() is called explicitly.
 */
object ArcNetworkAudit {

    private const val REDIS_KEY = "arc:audit:net"
    private const val MAX_BUFFER = 500

    data class Entry(
        val traceId: String,
        val actorName: String,
        val action: String,
        val metadata: Map<String, String>,
        val timestamp: Instant,
        val result: String = "SUCCESS",
    )

    private val buffer = ConcurrentLinkedQueue<Entry>()

    fun log(action: String, metadata: Map<String, String> = emptyMap(), actor: String = "SYSTEM") {
        val entry = Entry(
            traceId = java.util.UUID.randomUUID().toString().take(8),
            actorName = actor,
            action = action,
            metadata = metadata,
            timestamp = Instant.now(),
        )
        buffer.add(entry)
        while (buffer.size > MAX_BUFFER) buffer.poll()
        if (ArcRelayClient.connected) {
            runCatching {
                ArcRelayClient.zadd(REDIS_KEY, entry.timestamp.toEpochMilli().toDouble(), serialize(entry))
            }
        }
    }

    /** Read recent entries — Redis first (cross-server history), falls back to buffer. */
    fun recent(n: Int = 20): List<Entry> {
        if (ArcRelayClient.connected) {
            val fromRedis = ArcRelayClient.zrevrange(REDIS_KEY, 0L, (n - 1L)).mapNotNull { deserialize(it) }
            if (fromRedis.isNotEmpty()) return fromRedis
        }
        return buffer.toList().takeLast(n).reversed()
    }

    fun byPlayer(name: String): List<Entry> =
        buffer.filter { it.metadata["playerName"]?.equals(name, true) == true || it.actorName.equals(name, true) }

    fun byAction(action: String): List<Entry> =
        buffer.filter { it.action.equals(action, true) }

    /** Remove Redis entries older than retentionDays. Safe to call on startup or periodically. */
    fun pruneOld() {
        if (!ArcRelayClient.connected) return
        val retentionDays = ArcNetworkConfig.audit.retentionDays
        val cutoff = (System.currentTimeMillis() - retentionDays * 86_400_000L).toDouble()
        ArcRelayClient.zremrangebyscore(REDIS_KEY, 0.0, cutoff)
    }

    fun clear() = buffer.clear()

    // ---- serialization ----

    private fun serialize(entry: Entry): String {
        fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
        val meta = entry.metadata.entries.joinToString(",") { (k, v) -> "\"${k.esc()}\":\"${v.esc()}\"" }
        return """{"id":"${entry.traceId}","actor":"${entry.actorName.esc()}","action":"${entry.action.esc()}","meta":{$meta},"ts":${entry.timestamp.toEpochMilli()},"result":"${entry.result.esc()}"}"""
    }

    private fun deserialize(json: String): Entry? = runCatching {
        val meta = "\"meta\":\\{([^}]*)\\}".toRegex().find(json)?.groupValues?.get(1)?.let { block ->
            "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"".toRegex().findAll(block)
                .associate { m -> m.groupValues[1] to m.groupValues[2] }
        } ?: emptyMap()
        Entry(
            traceId = jsonStr(json, "id") ?: "",
            actorName = jsonStr(json, "actor") ?: "SYSTEM",
            action = jsonStr(json, "action") ?: "",
            metadata = meta,
            timestamp = Instant.ofEpochMilli(jsonNum(json, "ts") ?: 0L),
            result = jsonStr(json, "result") ?: "SUCCESS",
        )
    }.getOrNull()

    private fun jsonStr(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex().find(json)?.groupValues?.get(1)

    private fun jsonNum(json: String, key: String): Long? =
        "\"$key\"\\s*:\\s*([0-9]+)".toRegex().find(json)?.groupValues?.get(1)?.toLongOrNull()
}
