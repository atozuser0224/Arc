package dev.arc.api.ops.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Low-level dispatch that targets the Folia-style region schedulers when the
 * running server exposes them, and transparently falls back to the classic
 * [org.bukkit.scheduler.BukkitScheduler] on plain Paper/Leaf.
 *
 * Why reflection instead of a direct compile dependency: it lets the same jar
 * run on a server that has the threaded-region scheduler API and one that does
 * not, without a hard link to `io.papermc.paper.threadedregions.*`. Each lookup
 * is cached, so the steady-state cost is a map hit.
 *
 * All `run*` helpers return `true` if a region scheduler handled the dispatch,
 * `false` if the caller should use (or already fell back to) the Bukkit
 * scheduler. The fluent [ArcScheduler] facade hides this detail.
 */
object ArcSchedulers {

    private val methodCache = ConcurrentHashMap<String, Method?>()

    private fun method(key: String, resolver: () -> Method?): Method? =
        methodCache.computeIfAbsent(key) { runCatching(resolver).getOrNull() }

    private fun consumer(action: () -> Unit): Consumer<Any?> = Consumer { action() }

    /** Run on the global region (main) scheduler. */
    fun runGlobal(plugin: Plugin, action: () -> Unit): Boolean {
        val getScheduler = method("globalGetter") {
            Bukkit::class.java.getMethod("getGlobalRegionScheduler")
        } ?: return false
        return runCatching {
            val scheduler = getScheduler.invoke(null)
            val run = method("globalRun") {
                scheduler.javaClass.methods.first { it.name == "run" && it.parameterCount == 2 }
            } ?: return false
            run.invoke(scheduler, plugin, consumer(action))
            true
        }.getOrDefault(false)
    }

    /** Run owned by an entity (entity's region thread on Folia). */
    fun runEntity(plugin: Plugin, entity: Entity, action: () -> Unit, retired: (() -> Unit)? = null): Boolean {
        val getScheduler = method("entityGetter") {
            Entity::class.java.getMethod("getScheduler")
        } ?: return false
        return runCatching {
            val scheduler = getScheduler.invoke(entity)
            val run = method("entityRun") {
                scheduler.javaClass.methods.first { it.name == "run" && it.parameterCount == 3 }
            } ?: return false
            run.invoke(scheduler, plugin, consumer(action), retired?.let { Runnable(it) })
            true
        }.getOrDefault(false)
    }

    /** Run owned by a chunk/region (world + chunk coords). */
    fun runRegion(plugin: Plugin, world: World, chunkX: Int, chunkZ: Int, action: () -> Unit): Boolean {
        val getScheduler = method("regionGetter") {
            Bukkit::class.java.getMethod("getRegionScheduler")
        } ?: return false
        return runCatching {
            val scheduler = getScheduler.invoke(null)
            val run = method("regionRun") {
                scheduler.javaClass.methods.first {
                    it.name == "run" && it.parameterCount == 5 &&
                        it.parameterTypes[1] == World::class.java
                }
            } ?: return false
            run.invoke(scheduler, plugin, world, chunkX, chunkZ, consumer(action))
            true
        }.getOrDefault(false)
    }

    /** Run on the async scheduler (off the region threads). */
    fun runAsyncNow(plugin: Plugin, action: () -> Unit): Boolean {
        val getScheduler = method("asyncGetter") {
            Bukkit::class.java.getMethod("getAsyncScheduler")
        } ?: return false
        return runCatching {
            val scheduler = getScheduler.invoke(null)
            val run = method("asyncRun") {
                scheduler.javaClass.methods.first { it.name == "runNow" && it.parameterCount == 2 }
            } ?: return false
            run.invoke(scheduler, plugin, consumer(action))
            true
        }.getOrDefault(false)
    }
}

/**
 * Fluent, Folia-ready scheduling facade.
 *
 * ```java
 * ArcScheduler.entity(entity).run(() -> entity.remove());
 * ArcScheduler.chunk(world, cx, cz).run(task);
 * ArcScheduler.global().run(task);
 * ArcScheduler.async().run(task);
 * ```
 *
 * On plain Paper/Leaf every target maps to the main thread (or the async pool
 * for [async]); on a region-threaded server each target runs on the correct
 * owning thread. Either way the calling code is identical, so plugins written
 * against this API survive a future Folia migration unchanged.
 */
object ArcScheduler {

    @Volatile private var plugin: Plugin? = null
    fun init(plugin: Plugin) { this.plugin = plugin }
    private fun owner(): Plugin = plugin ?: error("ArcScheduler not initialised — call ArcScheduler.init(plugin)")

    fun interface Target { fun run(task: Runnable) }

    fun global(): Target = Target { task ->
        val p = owner()
        if (!ArcSchedulers.runGlobal(p) { task.run() }) Bukkit.getScheduler().runTask(p, task)
    }

    fun entity(entity: Entity): Target = Target { task ->
        val p = owner()
        if (!ArcSchedulers.runEntity(p, entity, { task.run() })) Bukkit.getScheduler().runTask(p, task)
    }

    fun chunk(world: World, chunkX: Int, chunkZ: Int): Target = Target { task ->
        val p = owner()
        if (!ArcSchedulers.runRegion(p, world, chunkX, chunkZ) { task.run() }) Bukkit.getScheduler().runTask(p, task)
    }

    fun location(location: Location): Target =
        chunk(location.world ?: error("location has no world"), location.blockX shr 4, location.blockZ shr 4)

    fun async(): Target = Target { task ->
        val p = owner()
        if (!ArcSchedulers.runAsyncNow(p) { task.run() }) Bukkit.getScheduler().runTaskAsynchronously(p, task)
    }
}
