package dev.arc.api.network

import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Network audit log — records all cross-server operations.
 */
object ArcNetworkAudit {

    data class Entry(
        val traceId: String,
        val actorName: String,
        val action: String,
        val metadata: Map<String, String>,
        val timestamp: Instant,
        val result: String = "SUCCESS",
    )

    private val buffer = ConcurrentLinkedQueue<Entry>()
    private const val MAX_BUFFER = 500

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
    }

    fun recent(n: Int = 20): List<Entry> = buffer.toList().takeLast(n).reversed()

    fun byPlayer(name: String): List<Entry> =
        buffer.filter { it.metadata["playerName"]?.equals(name, true) == true || it.actorName.equals(name, true) }

    fun byAction(action: String): List<Entry> =
        buffer.filter { it.action.equals(action, true) }

    fun clear() = buffer.clear()
}
