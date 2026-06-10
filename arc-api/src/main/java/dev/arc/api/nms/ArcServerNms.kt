package dev.arc.api.nms

import dev.arc.api.scheduling.callGlobal
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

/** Runtime diagnostics for the server-side NMS bridge. */
data class NmsDiagnostics(
    val implementation: String,
    val cachedClasses: Int = 0,
    val cachedMethods: Int = 0,
    val cachedFields: Int = 0,
    val cachedConstructors: Int = 0,
    val cachedMethodHandles: Int = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val resolutionFailures: Long = 0,
    val invocationFailures: Long = 0,
    val rejectedThreadAccesses: Long = 0,
)

/** Availability snapshot for version-sensitive NMS capabilities. */
data class NmsCapabilities(
    val available: Set<String>,
    val unavailable: Set<String>,
) {
    fun supports(capability: String): Boolean = capability in available
}

/** Whole-server capabilities that require server internals (NMS). */
interface ArcServerNms {

    /** Rolling mean tick time in milliseconds (MSPT). */
    fun mspt(): Double

    /** Total ticks the server has run since start (`MinecraftServer#tickCount`). */
    fun tickCount(): Int

    /** Recent TPS averages (typically 1m / 5m / 15m). */
    fun tps(): DoubleArray

    /** The raw `MinecraftServer` instance (escape hatch). */
    fun handle(): Any?

    /** Current bridge/cache diagnostics for observability and profiling. */
    fun diagnostics(): NmsDiagnostics = NmsDiagnostics(implementation = javaClass.name)

    /** Clear version-sensitive reflection caches. Returns false if unsupported. */
    fun clearReflectionCaches(): Boolean = false

    /** Probe and pre-cache version-sensitive classes and core server members. */
    fun probeCapabilities(): NmsCapabilities = NmsCapabilities(emptySet(), emptySet())

    fun msptAsync(plugin: Plugin): CompletableFuture<Double> = plugin.callGlobal { mspt() }

    fun tickCountAsync(plugin: Plugin): CompletableFuture<Int> = plugin.callGlobal { tickCount() }

    fun tpsAsync(plugin: Plugin): CompletableFuture<DoubleArray> = plugin.callGlobal { tps() }

    fun diagnosticsAsync(plugin: Plugin): CompletableFuture<NmsDiagnostics> =
        plugin.callGlobal { diagnostics() }

    fun probeCapabilitiesAsync(plugin: Plugin): CompletableFuture<NmsCapabilities> =
        plugin.callGlobal { probeCapabilities() }
}
