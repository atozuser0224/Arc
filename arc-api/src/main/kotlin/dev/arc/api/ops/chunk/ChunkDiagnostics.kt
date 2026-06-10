package dev.arc.api.ops.chunk

import org.bukkit.Bukkit

/**
 * Chunk-system diagnostics built on the public Bukkit/Paper chunk API.
 *
 * Reachable from the API (used here):
 *  - loaded chunk count per world
 *  - force-loaded chunks (World#getForceLoadedChunks)
 *  - plugin-held chunk tickets (World#getPluginChunkTickets)
 *
 * NOT reachable from the public API (reported as "needs NMS"):
 *  - the full chunk-ticket table with ticket *types* (PLAYER, PORTAL, UNKNOWN…)
 *    and expiry ticks
 *  - chunk save backlog / unsaved-chunk queue depth
 *  - chunk generation queue length
 * These require server-internal access (a documented extension point for the
 * arc-server reflective bridge).
 */
object ChunkDiagnostics {

    fun report(worldFilter: String? = null): String = buildString {
        appendLine("===== Arc Chunks: report =====")
        val worlds = Bukkit.getWorlds().filter { worldFilter == null || it.name.equals(worldFilter, true) }
        for (w in worlds) {
            val loaded = w.loadedChunks.size
            val forced = runCatching { w.forceLoadedChunks.size }.getOrDefault(0)
            val tickets = runCatching { w.pluginChunkTickets.values.sumOf { it.size } }.getOrDefault(0)
            appendLine("[${w.name}]")
            appendLine("  loaded chunks      : $loaded")
            appendLine("  force-loaded       : $forced")
            appendLine("  plugin-ticket chunks: $tickets")
        }
        append("save-backlog / generation-queue: needs server-internal probe (see /arc chunks backlog)")
    }

    fun tickets(worldFilter: String? = null): String = buildString {
        appendLine("===== Arc Chunks: tickets =====")
        val worlds = Bukkit.getWorlds().filter { worldFilter == null || it.name.equals(worldFilter, true) }
        for (w in worlds) {
            appendLine("[${w.name}] plugin-held chunk tickets:")
            val map = runCatching { w.pluginChunkTickets }.getOrDefault(emptyMap())
            if (map.isEmpty()) {
                appendLine("  (none)")
            } else {
                map.entries.sortedByDescending { it.value.size }.forEach { (plugin, chunks) ->
                    appendLine("  ${plugin.name}: ${chunks.size} chunk(s)")
                    if (chunks.size <= 8) {
                        chunks.forEach { appendLine("    (${it.x}, ${it.z})") }
                    }
                    if (chunks.size > 500) {
                        appendLine("    ^ suspiciously large — possible leaked ticket")
                    }
                }
            }
            val forced = runCatching { w.forceLoadedChunks }.getOrDefault(emptySet())
            if (forced.isNotEmpty()) {
                appendLine("  force-loaded (setChunkForceLoaded): ${forced.size}")
            }
        }
        append("note: vanilla ticket types (PLAYER/PORTAL/etc) + expiry not exposed by the public API")
    }

    fun backlog(): String = buildString {
        appendLine("===== Arc Chunks: backlog =====")
        appendLine("Chunk save backlog and generation-queue depth are not available through")
        appendLine("the public Bukkit API. To surface them, the arc-server reflective bridge")
        appendLine("must read the server chunk system (ChunkHolderManager / unloadQueue).")
        appendLine("Until then, use loaded-chunk trend (/arc chunks report) + tickets as a proxy:")
        appendLine("a steadily climbing loaded-chunk count with stable players usually means a")
        append("leaked ticket rather than save backlog.")
    }
}
