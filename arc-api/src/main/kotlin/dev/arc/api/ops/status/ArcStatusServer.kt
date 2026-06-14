package dev.arc.api.ops.status

import com.sun.net.httpserver.HttpServer
import dev.arc.api.ops.OpsConfig
import dev.arc.api.perf.ServerLoad
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.net.InetSocketAddress

/**
 * Minimal Prometheus-compatible /metrics endpoint.
 * Uses JDK's built-in HttpServer — no extra dependencies.
 * Enabled via arc-ops.yml: status.enabled: true
 */
object ArcStatusServer {

    private var server: HttpServer? = null

    fun start(plugin: Plugin, config: OpsConfig) {
        if (!config.statusEnabled) return
        stop()
        runCatching {
            val addr = InetSocketAddress(config.statusBindAddress, config.statusPort)
            server = HttpServer.create(addr, 0).also { s ->
                s.createContext("/metrics") { exchange ->
                    val body = buildMetrics().toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                s.createContext("/health") { exchange ->
                    val body = "OK".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "text/plain")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                s.executor = null
                s.start()
            }
            plugin.logger.info("[Arc] Status server listening on ${config.statusBindAddress}:${config.statusPort}/metrics")
        }.onFailure { e ->
            plugin.logger.warning("[Arc] Status server failed: ${e.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun buildMetrics(): String {
        val sb = StringBuilder()
        val rt = Runtime.getRuntime()

        sb.metric("arc_tps", "gauge", "Ticks per second (1 min avg)", ServerLoad.tps(1).toString())
        sb.metric("arc_tps_5m", "gauge", "Ticks per second (5 min avg)", ServerLoad.tps(5).toString())
        sb.metric("arc_mspt", "gauge", "Milliseconds per tick", ServerLoad.mspt().toString())
        sb.metric("arc_players_online", "gauge", "Online player count", Bukkit.getOnlinePlayers().size.toString())
        sb.metric("arc_players_max", "gauge", "Server player cap", Bukkit.getMaxPlayers().toString())

        val usedMem = rt.totalMemory() - rt.freeMemory()
        val maxMem = rt.maxMemory()
        sb.metric("arc_memory_used_bytes", "gauge", "JVM used heap", usedMem.toString())
        sb.metric("arc_memory_max_bytes", "gauge", "JVM max heap", maxMem.toString())
        sb.metric("arc_memory_used_fraction", "gauge", "JVM heap usage 0.0–1.0",
            "%.4f".format(usedMem.toDouble() / maxMem.toDouble()))

        return sb.toString()
    }

    private fun StringBuilder.metric(name: String, type: String, help: String, value: String) {
        append("# HELP $name $help\n# TYPE $name $type\n$name $value\n")
    }
}
