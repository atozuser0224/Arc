package dev.arc.api.ops.configcheck

import org.bukkit.Bukkit
import org.bukkit.entity.SpawnCategory

/** Severity of a config finding. */
enum class Severity { INFO, WARN, DANGER }

data class Finding(
    val severity: Severity,
    val path: String,
    val current: String,
    val message: String,
    val recommended: String,
)

/** Documentation entry for `/arc config explain`. */
data class SettingDoc(
    val path: String,
    val meaning: String,
    val performanceImpact: String,
    val recommended: String,
    val requiresRestart: Boolean,
)

/**
 * Rule-based validator for performance-sensitive server settings.
 *
 * Where possible it reads **live** values through the Bukkit/Paper API (these
 * reflect what the server actually applied, including per-world overrides),
 * rather than re-parsing YAML which can drift from runtime state.
 *
 * The rules below are conservative heuristics, not hard truths — a 32-chunk
 * view distance is fine on a 4-player creative server and disastrous on a
 * 200-player survival one. Findings are advisory; nothing is auto-changed.
 */
object ConfigValidator {

    fun check(): List<Finding> {
        val findings = mutableListOf<Finding>()
        val onlineMax = Bukkit.getMaxPlayers()

        // view-distance
        val viewDistance = Bukkit.getViewDistance()
        if (viewDistance >= 12) {
            findings += Finding(
                Severity.WARN, "view-distance", viewDistance.toString(),
                "High global view-distance multiplies loaded chunks (~(2r+1)^2 per player) and chunk-gen pressure.",
                "8-10 for survival; raise per-world only where needed",
            )
        }
        // simulation-distance
        val simDistance = Bukkit.getSimulationDistance()
        if (simDistance >= viewDistance && simDistance >= 8) {
            findings += Finding(
                Severity.WARN, "simulation-distance", simDistance.toString(),
                "Simulation distance drives entity/redstone/tick cost. Keeping it >= view-distance is rarely needed.",
                "4-6, and <= view-distance",
            )
        }
        // spawn limits
        for (cat in SpawnCategory.values()) {
            if (cat == SpawnCategory.MISC) continue
            val limit = runCatching { Bukkit.getSpawnLimit(cat) }.getOrDefault(-1)
            if (limit > 70 && cat == SpawnCategory.MONSTER) {
                findings += Finding(
                    Severity.WARN, "spawn-limits.${cat.name.lowercase()}", limit.toString(),
                    "High monster spawn limit increases per-tick mob AI and pathfinding cost.",
                    "<= 70 monsters; lower on large servers",
                )
            }
        }
        // per-world chunk/entity load
        for (world in Bukkit.getWorlds()) {
            val loaded = world.loadedChunks.size
            if (loaded > 10_000) {
                findings += Finding(
                    Severity.WARN, "world.${world.name}.loaded-chunks", loaded.toString(),
                    "Very high loaded-chunk count — check for leaking chunk tickets / force-loaded regions.",
                    "investigate with /arc chunks tickets",
                )
            }
            val forced = runCatching { world.forceLoadedChunks.size }.getOrDefault(0)
            if (forced > 200) {
                findings += Finding(
                    Severity.WARN, "world.${world.name}.force-loaded-chunks", forced.toString(),
                    "Large number of force-loaded chunks keeps them ticking forever.",
                    "audit plugins calling setChunkForceLoaded()",
                )
            }
        }
        // online-mode
        if (!Bukkit.getOnlineMode()) {
            findings += Finding(
                Severity.DANGER, "online-mode", "false",
                "Offline-mode without a trusted proxy in front allows account spoofing.",
                "true, or terminate auth at a proxy (Velocity) with player-info-forwarding",
            )
        }
        // memory headroom
        val rt = Runtime.getRuntime()
        val maxMb = rt.maxMemory() / (1024 * 1024)
        if (maxMb < 2048) {
            findings += Finding(
                Severity.WARN, "jvm.-Xmx", "${maxMb}MB",
                "Low max heap can cause frequent GC pauses (visible as lag spikes).",
                ">= 4G for a populated survival server",
            )
        }
        if (onlineMax > 50 && maxMb < 6144) {
            findings += Finding(
                Severity.INFO, "jvm.-Xmx", "${maxMb}MB for $onlineMax slots",
                "Heap may be tight for the advertised slot count.",
                "size heap to player count + world size",
            )
        }
        return findings
    }

    fun explain(path: String): SettingDoc? = DOCS.entries.firstOrNull {
        path.equals(it.key, true) || path.endsWith(it.key, true)
    }?.value

    fun knownPaths(): List<String> = DOCS.keys.toList()

    private val DOCS: Map<String, SettingDoc> = linkedMapOf(
        "view-distance" to SettingDoc(
            "view-distance",
            "How many chunks around a player are sent and kept loaded.",
            "Loaded chunks grow ~quadratically; biggest single lever on memory + chunk-gen load.",
            "8-10 survival, lower for big servers", requiresRestart = false,
        ),
        "simulation-distance" to SettingDoc(
            "simulation-distance",
            "How many chunks around a player actually tick (entities, redstone, growth).",
            "Directly scales per-tick entity/redstone work.",
            "4-6, <= view-distance", requiresRestart = false,
        ),
        "spawn-limits.monster" to SettingDoc(
            "spawn-limits.monster",
            "Per-player cap on naturally-spawned monsters considered for spawning.",
            "Higher = more mob AI + pathfinding per tick.",
            "<= 70", requiresRestart = false,
        ),
        "ticks-per.monster-spawns" to SettingDoc(
            "ticks-per.monster-spawns",
            "How often the spawn routine runs for monsters.",
            "Lower interval = more frequent (costlier) spawn attempts.",
            "1 (vanilla) unless profiling says otherwise", requiresRestart = false,
        ),
        "hopper.transfer" to SettingDoc(
            "hopper.transfer / hopper.check",
            "Tick cadence of hopper item transfer and inventory checks.",
            "Dense hopper arrays are a classic tile-entity hotspot.",
            "raise transfer/check ticks if hoppers dominate tile-entity time", requiresRestart = false,
        ),
        "auto-save-interval" to SettingDoc(
            "ticks-per.autosave",
            "How often worlds flush to disk.",
            "Too frequent = periodic I/O stalls; too rare = larger data-loss window on crash.",
            "6000 ticks (5 min); ensure incremental save is on", requiresRestart = false,
        ),
        "max-tick-time" to SettingDoc(
            "max-tick-time.tile / .entity",
            "Watchdog budget for tile-entity and entity ticking per tick.",
            "Caps runaway tick categories but can hide real problems.",
            "leave default unless used as a deliberate safety valve", requiresRestart = true,
        ),
    )
}
