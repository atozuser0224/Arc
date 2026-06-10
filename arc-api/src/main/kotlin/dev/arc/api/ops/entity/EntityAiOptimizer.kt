package dev.arc.api.ops.entity

import dev.arc.api.ops.OpsConfig
import org.bukkit.World
import org.bukkit.entity.Mob
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/**
 * Distance-based AI throttling — a *safe*, pure-Bukkit optimization.
 *
 * For each eligible mob, every [OpsConfig.aiCheckIntervalTicks] ticks:
 *  - within near-range of a player  -> AI fully on (`setAware(true)`)
 *  - beyond far-range               -> AI ticked only 1 of every `far-ai-interval`
 *                                       checks (`setAware` toggled)
 *  - in between                     -> left as-is (hysteresis band)
 *
 * Safety rules baked in:
 *  - default OFF; only runs for worlds in the allowlist (empty = all)
 *  - a mob currently targeting something is ALWAYS kept aware
 *    (`prioritize-targeting-mobs`) so combat never freezes
 *  - we only flip the public `setAware` flag — no NMS, no off-thread access,
 *    no goal-list surgery — so Paper plugins observing the mob still see a
 *    consistent, supported state
 *
 * Exposes [stats] so the effect is measurable (`throttled` vs `awake`).
 */
class EntityAiOptimizer(
    private val plugin: Plugin,
    private val config: OpsConfig,
) {
    data class Stats(
        @Volatile var lastRunMillis: Long = 0,
        @Volatile var awake: Int = 0,
        @Volatile var throttled: Int = 0,
        @Volatile var protectedTargeting: Int = 0,
    )

    val stats = Stats()
    private var task: BukkitTask? = null
    private var cycle = 0L

    fun start() {
        if (task != null) return
        val period = config.aiCheckIntervalTicks.coerceAtLeast(1).toLong()
        task = object : BukkitRunnable() {
            override fun run() = pass()
        }.runTaskTimer(plugin, period, period)
    }

    fun stop() {
        // Restore AI on everything we may have touched before shutting down.
        runCatching {
            for (world in targetWorlds()) for (e in world.entities) (e as? Mob)?.isAware = true
        }
        task?.cancel()
        task = null
    }

    private fun targetWorlds(): List<World> {
        val all = plugin.server.worlds
        val allow = config.entityOptWorlds
        return if (allow.isEmpty()) all else all.filter { w -> allow.any { it.equals(w.name, true) } }
    }

    private fun pass() {
        if (!config.entityOptEnabled || !config.distanceAiEnabled) return
        cycle++
        val near2 = config.aiNearRange * config.aiNearRange
        val far2 = config.aiFarRange * config.aiFarRange
        val farInterval = config.aiFarInterval.coerceAtLeast(1).toLong()
        val farTickNow = (cycle % farInterval == 0L)

        var awake = 0
        var throttled = 0
        var targeting = 0

        for (world in targetWorlds()) {
            val players = world.players
            if (players.isEmpty()) {
                // No observers: throttle everything except targeting mobs.
                for (e in world.entities) {
                    val mob = e as? Mob ?: continue
                    if (mob.target != null) { mob.isAware = true; targeting++; awake++; continue }
                    mob.isAware = farTickNow
                    if (farTickNow) awake++ else throttled++
                }
                continue
            }
            for (e in world.entities) {
                val mob = e as? Mob ?: continue
                if (mob.target != null) { mob.isAware = true; targeting++; awake++; continue }
                val loc = mob.location
                var nearest = Double.MAX_VALUE
                for (p in players) {
                    if (p.world !== world) continue
                    val d = p.location.distanceSquared(loc)
                    if (d < nearest) nearest = d
                }
                when {
                    nearest <= near2 -> { mob.isAware = true; awake++ }
                    nearest >= far2 -> { mob.isAware = farTickNow; if (farTickNow) awake++ else throttled++ }
                    else -> awake++ // hysteresis band: leave running
                }
            }
        }

        stats.lastRunMillis = System.currentTimeMillis()
        stats.awake = awake
        stats.throttled = throttled
        stats.protectedTargeting = targeting
    }
}
