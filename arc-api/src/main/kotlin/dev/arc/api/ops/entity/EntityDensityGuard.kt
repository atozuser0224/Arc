package dev.arc.api.ops.entity

import dev.arc.api.ops.OpsConfig
import dev.arc.api.ops.metrics.TickSampler
import org.bukkit.entity.Animals
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Monster
import org.bukkit.entity.Tameable
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/**
 * Caps the number of natural mobs per chunk, culling the excess — but only
 * under pressure and only for mobs that are safe to remove.
 *
 * Safety gates (all must pass before a mob is eligible for culling):
 *  - feature enabled AND current TPS below [OpsConfig] activation threshold
 *  - the mob is a [Mob] that is NOT: named, leashed, carrying/being a passenger,
 *    tamed, persistent, or removed-when-far-away == false (i.e. "important")
 *  - it is over the per-chunk cap for its category (monster vs animal counted
 *    separately so farms aren't wiped to make room for a mob grinder)
 *
 * Default OFF. This changes the live world, so it is deliberately conservative
 * and fully measurable via [stats].
 */
class EntityDensityGuard(
    private val plugin: Plugin,
    private val config: OpsConfig,
) {
    data class Stats(
        @Volatile var lastRunMillis: Long = 0,
        @Volatile var culledThisRun: Int = 0,
        @Volatile var culledTotal: Long = 0,
        @Volatile var lastActive: Boolean = false,
    )

    val stats = Stats()
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        val period = config.densityCheckIntervalTicks.coerceAtLeast(20).toLong()
        task = object : BukkitRunnable() {
            override fun run() = pass()
        }.runTaskTimer(plugin, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun targetWorlds() = plugin.server.worlds.filter { w ->
        config.densityWorlds.isEmpty() || config.densityWorlds.any { it.equals(w.name, true) }
    }

    private fun pass() {
        stats.culledThisRun = 0
        if (!config.densityEnabled) { stats.lastActive = false; return }

        // Only act under real pressure — never cull a healthy server.
        val tps = TickSampler.tps().getOrElse(0) { 20.0 }
        if (tps >= config.densityActivationTps) { stats.lastActive = false; return }
        stats.lastActive = true

        var culled = 0
        val cap = config.densityMaxMobsPerChunk.coerceAtLeast(1)

        for (world in targetWorlds()) {
            // Bucket eligible mobs by chunk + category.
            val monsters = HashMap<Long, MutableList<Mob>>()
            val animals = HashMap<Long, MutableList<Mob>>()
            for (e in world.entities) {
                val mob = e as? Mob ?: continue
                if (!isEligible(mob)) continue
                val key = chunkKey(mob)
                when (mob) {
                    is Monster -> monsters.getOrPut(key) { ArrayList() }.add(mob)
                    is Animals -> animals.getOrPut(key) { ArrayList() }.add(mob)
                    else -> {}
                }
            }
            culled += cullExcess(monsters, cap)
            culled += cullExcess(animals, cap)
        }

        stats.culledThisRun = culled
        stats.culledTotal += culled
        stats.lastRunMillis = System.currentTimeMillis()
        if (culled > 0) {
            plugin.logger.fine("[Arc] density guard culled $culled mob(s) (tps=${"%.1f".format(tps)})")
        }
    }

    private fun cullExcess(buckets: Map<Long, MutableList<Mob>>, cap: Int): Int {
        var removed = 0
        for (list in buckets.values) {
            if (list.size <= cap) continue
            // Remove the newest/extra ones; keep [cap] of them.
            for (i in cap until list.size) {
                runCatching { list[i].remove() }.onSuccess { removed++ }
            }
        }
        return removed
    }

    private fun isEligible(mob: Mob): Boolean {
        if (mob.customName() != null) return false
        if (mob.isLeashed) return false
        if (!mob.passengers.isEmpty() || mob.isInsideVehicle) return false
        if (mob is Tameable && mob.isTamed) return false
        // "persistent / important" mobs ask not to be removed when far away.
        if (mob is LivingEntity && !mob.removeWhenFarAway) return false
        if (mob.isPersistent) return false
        return true
    }

    private fun chunkKey(e: Entity): Long {
        val cx = e.location.blockX shr 4
        val cz = e.location.blockZ shr 4
        return (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)
    }
}
