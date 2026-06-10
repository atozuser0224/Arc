@file:JvmName("AdaptiveGovernors")

package dev.arc.api.perf

import dev.arc.api.coroutine.launchEveryTicks
import kotlinx.coroutines.Job
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.math.max

/**
 * An adaptive performance governor: it watches [ServerLoad] and, when the server falls behind, **aggressively
 * scales down per-world view and simulation distance**, then restores the originals once load recovers.
 *
 * View/simulation distance are the single biggest levers on tick cost (they drive chunk ticking, entity
 * ticking and packet volume). Dropping them a step at a time under load - and only while load stays high -
 * trades a little render range for staying at 20 TPS, automatically, with no operator babysitting.
 *
 * It steps by one each check (gentle on the way down, full restore on recovery) to avoid oscillation, and
 * only touches worlds whose originals it has saved.
 *
 * ```kotlin
 * val governor = plugin.adaptiveGovernor().apply { minViewDistance = 3 }
 * governor.start()   // runs until the plugin disables (or governor.stop())
 * ```
 */
public class AdaptiveGovernor internal constructor(private val plugin: Plugin) {

    /** View distance is never reduced below this. */
    public var minViewDistance: Int = 4

    /** Simulation distance is never reduced below this. */
    public var minSimulationDistance: Int = 4

    /** How often (in ticks) to re-evaluate load. */
    public var checkPeriodTicks: Long = 100L

    private val savedView = HashMap<UUID, Int>()
    private val savedSim = HashMap<UUID, Int>()
    private var job: Job? = null

    /** `true` while at least one world is currently scaled down. */
    public val isThrottling: Boolean
        get() = savedView.isNotEmpty()

    /** Begin governing. Idempotent. */
    public fun start() {
        if (job != null) return
        job = plugin.launchEveryTicks(checkPeriodTicks) { evaluate() }
    }

    /** Stop governing and restore every world to its saved distances. */
    public fun stop() {
        job?.cancel()
        job = null
        restoreAll()
    }

    private fun evaluate() {
        when (ServerLoad.level) {
            LoadLevel.HIGH, LoadLevel.CRITICAL -> tightenOneStep()
            LoadLevel.LOW -> restoreAll()
            LoadLevel.NORMAL -> Unit
        }
    }

    private fun tightenOneStep() {
        for (world in Bukkit.getWorlds()) {
            savedView.putIfAbsent(world.uid, world.viewDistance)
            savedSim.putIfAbsent(world.uid, world.simulationDistance)
            world.viewDistance = max(minViewDistance, world.viewDistance - 1)
            world.simulationDistance = max(minSimulationDistance, world.simulationDistance - 1)
        }
    }

    private fun restoreAll() {
        for (world in Bukkit.getWorlds()) {
            savedView.remove(world.uid)?.let { world.viewDistance = it }
            savedSim.remove(world.uid)?.let { world.simulationDistance = it }
        }
    }
}

/** Create an [AdaptiveGovernor] bound to this plugin (call [AdaptiveGovernor.start] to activate). */
public fun Plugin.adaptiveGovernor(): AdaptiveGovernor = AdaptiveGovernor(this)
