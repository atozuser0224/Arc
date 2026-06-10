package dev.arc.api.ops.status

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.arc.api.ops.metrics.ServerSnapshot
import dev.arc.api.ops.metrics.SnapshotService
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Tiny read-only status/metrics endpoint backed by the JDK's built-in
 * [HttpServer] (no third-party dependency).
 *
 * Thread-safety: handlers run on the HTTP server's own threads and ONLY read
 * the published [ServerSnapshot] (an immutable value rebuilt on the main
 * thread). They never touch World/Entity/Block state directly, so this is safe
 * to expose without risking the main-thread invariants.
 *
 * Endpoints:
 *  - GET /leaf/status       -> JSON: players, tps, mspt, memory
 *  - GET /leaf/worlds       -> JSON: per-world chunk/entity counts
 *  - GET /leaf/performance  -> JSON: tps/mspt/gc/plugin cost
 *  - GET /metrics           -> Prometheus text exposition
 */
class StatusHttpServer(
    private val bindAddress: String,
    private val port: Int,
    private val log: Logger,
) {
    private var server: HttpServer? = null

    fun start() {
        if (server != null) return
        val s = HttpServer.create(InetSocketAddress(bindAddress, port), 0)
        s.createContext("/leaf/status") { ex -> respond(ex, "application/json", statusJson(SnapshotService.latest)) }
        s.createContext("/leaf/worlds") { ex -> respond(ex, "application/json", worldsJson(SnapshotService.latest)) }
        s.createContext("/leaf/performance") { ex -> respond(ex, "application/json", performanceJson(SnapshotService.latest)) }
        s.createContext("/metrics") { ex -> respond(ex, "text/plain; version=0.0.4", prometheus(SnapshotService.latest)) }
        s.executor = null // default executor (a small internal pool)
        s.start()
        server = s
        log.info("[Leaf] Status server listening on http://$bindAddress:$port (/leaf/status, /metrics)")
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun respond(ex: HttpExchange, contentType: String, body: String) {
        runCatching {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", contentType)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        ex.close()
    }

    // ---- renderers ---------------------------------------------------------

    private fun statusJson(s: ServerSnapshot): String =
        """{"capturedAt":${s.capturedAtMillis},"players":${s.onlinePlayers},"maxPlayers":${s.maxPlayers},""" +
            """"tps1m":${f(s.tps1m)},"mspt":${f(s.paperMspt)},"usedMemoryMb":${s.usedMemoryMb},"maxMemoryMb":${s.maxMemoryMb},""" +
            """"loadedChunks":${s.totalLoadedChunks},"entities":${s.totalEntities}}"""

    private fun worldsJson(s: ServerSnapshot): String {
        val items = s.worlds.joinToString(",") { w ->
            """{"name":${q(w.name)},"players":${w.players},"loadedChunks":${w.loadedChunks},""" +
                """"forcedChunks":${w.forcedChunks},"entities":${w.entities},"tileEntities":${w.tileEntities}}"""
        }
        return """{"worlds":[$items]}"""
    }

    private fun performanceJson(s: ServerSnapshot): String {
        val cost = s.pluginTaskCost.entries.joinToString(",") { """${q(it.key)}:${f(it.value)}""" }
        return """{"tps1m":${f(s.tps1m)},"tps5m":${f(s.tps5m)},"tps15m":${f(s.tps15m)},""" +
            """"paperMspt":${f(s.paperMspt)},"avgMspt":${f(s.avgMspt)},"gcInWindow":${s.gcInWindow},""" +
            """"pluginTaskCostMs":{$cost}}"""
    }

    private fun prometheus(s: ServerSnapshot): String = buildString {
        appendLine("# HELP leaf_mspt_average Average milliseconds per tick (Paper).")
        appendLine("# TYPE leaf_mspt_average gauge")
        appendLine("leaf_mspt_average ${f(s.paperMspt)}")
        appendLine("# TYPE leaf_tps gauge")
        appendLine("leaf_tps{window=\"1m\"} ${f(s.tps1m)}")
        appendLine("leaf_tps{window=\"5m\"} ${f(s.tps5m)}")
        appendLine("leaf_tps{window=\"15m\"} ${f(s.tps15m)}")
        appendLine("# TYPE leaf_players gauge")
        appendLine("leaf_players ${s.onlinePlayers}")
        appendLine("# TYPE leaf_memory_used_bytes gauge")
        appendLine("leaf_memory_used_bytes ${s.usedMemoryMb * 1024 * 1024}")
        appendLine("# TYPE leaf_gc_collections gauge")
        appendLine("leaf_gc_collections ${s.gcInWindow}")
        appendLine("# TYPE leaf_loaded_chunks gauge")
        for (w in s.worlds) appendLine("leaf_loaded_chunks{world=${q(w.name)}} ${w.loadedChunks}")
        appendLine("# TYPE leaf_entities gauge")
        for (w in s.worlds) appendLine("leaf_entities{world=${q(w.name)}} ${w.entities}")
        appendLine("# TYPE leaf_plugin_task_cost gauge")
        for ((plugin, ms) in s.pluginTaskCost) appendLine("leaf_plugin_task_cost{plugin=${q(plugin)}} ${f(ms)}")
    }

    private fun f(v: Double): String = "%.2f".format(v)
    private fun q(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
