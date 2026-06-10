package dev.arc.api.ops.metrics

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.atomic.AtomicReference

/** Immutable per-world counters, safe to read from any thread. */
data class WorldSnapshot(
    val name: String,
    val players: Int,
    val loadedChunks: Int,
    val forcedChunks: Int,
    val entities: Int,
    val tileEntities: Int,
    val entityCountsByType: Map<String, Int>,
)

/** Immutable whole-server snapshot, rebuilt on the main thread every N ticks. */
data class ServerSnapshot(
    val capturedAtMillis: Long,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val avgMspt: Double,
    val paperMspt: Double,
    val tps1m: Double,
    val tps5m: Double,
    val tps15m: Double,
    val usedMemoryMb: Long,
    val maxMemoryMb: Long,
    val gcInWindow: Long,
    val worlds: List<WorldSnapshot>,
    val pluginTaskCost: Map<String, Double>,
) {
    val totalEntities: Int get() = worlds.sumOf { it.entities }
    val totalLoadedChunks: Int get() = worlds.sumOf { it.loadedChunks }

    companion object {
        val EMPTY = ServerSnapshot(
            0L, 0, 0, 0.0, 0.0, 20.0, 20.0, 20.0, 0, 0, 0, emptyList(), emptyMap(),
        )
    }
}

/**
 * Maintains the latest [ServerSnapshot] in an [AtomicReference]. The build runs
 * on the main thread (Bukkit world/entity access is main-thread only); off-main
 * consumers (the status HTTP server) only ever read the published reference.
 */
object SnapshotService {

    private val ref = AtomicReference(ServerSnapshot.EMPTY)
    private var task: BukkitTask? = null

    val latest: ServerSnapshot get() = ref.get()

    fun start(plugin: Plugin, intervalTicks: Int) {
        if (task != null) return
        val period = intervalTicks.coerceAtLeast(1).toLong()
        task = object : BukkitRunnable() {
            override fun run() = ref.set(build())
        }.runTaskTimer(plugin, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /** Force a rebuild now. Must be called on the main thread. */
    fun refreshNow() = ref.set(build())

    private fun build(): ServerSnapshot {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)
        val tps = TickSampler.tps()

        val worlds = Bukkit.getWorlds().map { world ->
            val entities = world.entities
            val byType = HashMap<String, Int>()
            var tiles = 0
            for (e in entities) {
                val key = e.type.name
                byType[key] = (byType[key] ?: 0) + 1
            }
            // Tile entities: counting BlockState across chunks is expensive; we
            // approximate via loaded tile-entity-bearing tracking when available.
            tiles = runCatching { world.loadedChunks.sumOf { it.tileEntities.size } }.getOrDefault(0)
            WorldSnapshot(
                name = world.name,
                players = world.players.size,
                loadedChunks = world.loadedChunks.size,
                forcedChunks = runCatching { world.forceLoadedChunks.size }.getOrDefault(0),
                entities = entities.size,
                tileEntities = tiles,
                entityCountsByType = byType,
            )
        }

        return ServerSnapshot(
            capturedAtMillis = System.currentTimeMillis(),
            onlinePlayers = Bukkit.getOnlinePlayers().size,
            maxPlayers = Bukkit.getMaxPlayers(),
            avgMspt = TickSampler.avgMspt(),
            paperMspt = TickSampler.paperAverageMspt(),
            tps1m = tps.getOrElse(0) { 20.0 },
            tps5m = tps.getOrElse(1) { 20.0 },
            tps15m = tps.getOrElse(2) { 20.0 },
            usedMemoryMb = used,
            maxMemoryMb = max,
            gcInWindow = TickSampler.gcCountInWindow(),
            worlds = worlds,
            pluginTaskCost = dev.arc.api.ops.plugincost.PluginCostTracker.snapshotCost(),
        )
    }
}
