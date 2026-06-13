package dev.arc.api.network

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Server registry backed by Redis. Every game server registers itself
 * with a heartbeat; the proxy reads this to route players.
 */
object ArcServerRegistry {

    private var relay: ArcRelayClient? = null
    private var heartbeatExecutor: java.util.concurrent.ScheduledExecutorService? = null
    private var heartbeatTask: java.util.concurrent.Future<*>? = null
    @Volatile var localServerInfo: ServerInfo? = null

    data class ServerInfo(
        val id: String,
        val group: String,
        val status: ServerStatus,
        val players: Int,
        val maxPlayers: Int,
        val tps: DoubleArray,       // [1m, 5m, 15m]
        val mspt: Double,
        val minecraftVersion: String,
        val arcVersion: String,
        val javaVersion: String,
        val cpuLoad: Double,
        val memoryUsedMb: Long,
        val memoryMaxMb: Long,
        val gcCount: Long,
        val uptimeMinutes: Long,
        val tags: List<String>,
        val lastHeartbeat: Long,
    )

    enum class ServerStatus {
        STARTING, ONLINE, DEGRADED, FULL, MAINTENANCE, STOPPING, OFFLINE
    }

    fun start(config: ArcNetworkConfig, relayClient: ArcRelayClient) {
        stop()
        this.relay = relayClient
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "Arc-Network-Heartbeat").apply { isDaemon = true }
        }
        heartbeatExecutor = executor
        heartbeatTask = executor.scheduleAtFixedRate(
            { sendHeartbeat(config) },
            1, config.heartbeat.intervalSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS
        )
    }

    fun stop() {
        heartbeatTask?.cancel(false)
        heartbeatExecutor?.shutdownNow()
        heartbeatTask = null
        heartbeatExecutor = null
        relay?.let { r ->
            runCatching { r.del("arc:server:${ArcNetworkConfig.serverId}") }
            runCatching { r.srem("arc:servers:online", ArcNetworkConfig.serverId) }
        }
        relay = null
    }

    /** Get all online server IDs */
    fun onlineServerIds(): List<String> {
        return relay?.smembers("arc:servers:online") ?: emptyList()
    }

    /** Get info for a specific server */
    fun getServer(serverId: String): ServerInfo? {
        val json = relay?.get("arc:server:$serverId") ?: return null
        return parseServerInfo(json)
    }

    /** Get all servers with status */
    fun allServers(): List<ServerInfo> {
        return onlineServerIds().mapNotNull { getServer(it) }
    }

    /** Get servers in a group */
    fun serversByGroup(group: String): List<ServerInfo> {
        return allServers().filter { it.group == group }
    }

    /** Set maintenance status */
    fun setStatus(serverId: String, status: ServerStatus) {
        val current = getServer(serverId) ?: return
        val updated = current.copy(status = status, lastHeartbeat = System.currentTimeMillis())
        relay?.setex("arc:server:$serverId", ArcNetworkConfig.heartbeat.ttlSeconds, serializeServerInfo(updated))
    }

    /** Set local server maintenance status */
    fun setLocalStatus(status: ServerStatus) {
        localServerInfo = localServerInfo?.copy(status = status)
    }

    private fun sendHeartbeat(config: ArcNetworkConfig) {
        try {
            val rt = Runtime.getRuntime()
            val info = ServerInfo(
                id = config.serverId,
                group = config.serverGroup,
                status = localServerInfo?.status ?: ServerStatus.ONLINE,
                players = org.bukkit.Bukkit.getOnlinePlayers().size,
                maxPlayers = org.bukkit.Bukkit.getMaxPlayers(),
                tps = getRecentTps(),
                mspt = getAverageMspt(),
                minecraftVersion = org.bukkit.Bukkit.getMinecraftVersion(),
                arcVersion = dev.arc.api.Arc::class.java.`package`?.implementationVersion ?: "dev",
                javaVersion = System.getProperty("java.version"),
                cpuLoad = getCpuLoad(),
                memoryUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                memoryMaxMb = rt.maxMemory() / (1024 * 1024),
                gcCount = getGcCount(),
                uptimeMinutes = (System.currentTimeMillis() - startTime) / 60_000,
                tags = config.tags,
                lastHeartbeat = System.currentTimeMillis(),
            )
            localServerInfo = info
            val json = serializeServerInfo(info)
            relay?.let { r ->
                r.setex("arc:server:${config.serverId}", config.heartbeat.ttlSeconds, json)
                r.sadd("arc:servers:online", config.serverId)
                r.zadd("arc:server:${config.serverId}:history", System.currentTimeMillis().toDouble(), info.status.name)
            }
        } catch (e: Exception) {
            // heartbeat failure — next cycle will retry
        }
    }

    private val startTime = System.currentTimeMillis()

    private fun getRecentTps(): DoubleArray {
        return try {
            val server = org.bukkit.Bukkit.getServer()
            val field = server.javaClass.getDeclaredField("recentTps")
            field.isAccessible = true
            field.get(server) as? DoubleArray ?: doubleArrayOf(20.0, 20.0, 20.0)
        } catch (e: Exception) {
            doubleArrayOf(20.0, 20.0, 20.0)
        }
    }

    private fun getAverageMspt(): Double {
        return try {
            val server = org.bukkit.Bukkit.getServer()
            val field = server.javaClass.getDeclaredField("averageTickTime")
            field.isAccessible = true
            (field.get(server) as? Double ?: 0.0) * 1.0E-6 // nanos to millis
        } catch (e: Exception) { 0.0 }
    }

    private fun getCpuLoad(): Double {
        return try {
            val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            if (bean is com.sun.management.OperatingSystemMXBean) bean.processCpuLoad else -1.0
        } catch (e: Exception) { -1.0 }
    }

    private fun getGcCount(): Long {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionCount }
    }

    private fun serializeServerInfo(info: ServerInfo): String {
        return """{"id":"${info.id}","group":"${info.group}","status":"${info.status.name}","players":${info.players},"maxPlayers":${info.maxPlayers},"tps":[${info.tps.joinToString()}],"mspt":${info.mspt},"minecraftVersion":"${info.minecraftVersion}","arcVersion":"${info.arcVersion}","javaVersion":"${info.javaVersion}","cpuLoad":${info.cpuLoad},"memoryUsedMb":${info.memoryUsedMb},"memoryMaxMb":${info.memoryMaxMb},"gcCount":${info.gcCount},"uptimeMinutes":${info.uptimeMinutes},"tags":[${info.tags.joinToString { "\"$it\"" }}],"lastHeartbeat":${info.lastHeartbeat}}"""
    }

    private fun parseServerInfo(json: String): ServerInfo? {
        return runCatching {
            val tpsMatch = "\"tps\":\\[([^]]+)]".toRegex().find(json)?.groupValues?.get(1)
            val tps = tpsMatch?.split(",")?.map { it.trim().toDoubleOrNull() ?: 20.0 }?.toDoubleArray() ?: doubleArrayOf(20.0, 20.0, 20.0)
            ServerInfo(
                id = jsonField(json, "id") ?: "unknown",
                group = jsonField(json, "group") ?: "default",
                status = runCatching { ServerStatus.valueOf(jsonField(json, "status") ?: "ONLINE") }.getOrDefault(ServerStatus.ONLINE),
                players = jsonField(json, "players")?.toIntOrNull() ?: 0,
                maxPlayers = jsonField(json, "maxPlayers")?.toIntOrNull() ?: 20,
                tps = tps,
                mspt = jsonField(json, "mspt")?.toDoubleOrNull() ?: 0.0,
                minecraftVersion = jsonField(json, "minecraftVersion") ?: "?",
                arcVersion = jsonField(json, "arcVersion") ?: "?",
                javaVersion = jsonField(json, "javaVersion") ?: "?",
                cpuLoad = jsonField(json, "cpuLoad")?.toDoubleOrNull() ?: -1.0,
                memoryUsedMb = jsonField(json, "memoryUsedMb")?.toLongOrNull() ?: 0,
                memoryMaxMb = jsonField(json, "memoryMaxMb")?.toLongOrNull() ?: 0,
                gcCount = jsonField(json, "gcCount")?.toLongOrNull() ?: 0,
                uptimeMinutes = jsonField(json, "uptimeMinutes")?.toLongOrNull() ?: 0,
                tags = jsonArrayField(json, "tags"),
                lastHeartbeat = jsonField(json, "lastHeartbeat")?.toLongOrNull() ?: 0,
            )
        }.getOrNull()
    }

    private fun jsonField(json: String, key: String): String? {
        val s = "\"$key\":\"?([^\"]*)\"?".toRegex().find(json)?.groupValues?.get(1)
        if (s != null && s.isNotEmpty()) return s
        return "\"$key\":([0-9.]+)".toRegex().find(json)?.groupValues?.get(1)
    }
    private fun jsonArrayField(json: String, key: String): List<String> {
        val m = "\"$key\":\\[([^]]*)]".toRegex().find(json) ?: return emptyList()
        return m.groupValues[1].split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    }
}
