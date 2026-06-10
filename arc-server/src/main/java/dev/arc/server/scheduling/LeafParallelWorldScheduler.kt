package dev.arc.server.scheduling

import dev.arc.api.scheduling.ArcThreadScheduler
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Bridges Arc ownership-aware futures to Leaf 1.21.4 parallel world ticking.
 * Paper's fallback RegionScheduler always targets the global thread, which does
 * not own a world while PWT is enabled.
 */
internal object LeafParallelWorldScheduler : ArcThreadScheduler {
    private val handleMethods = ConcurrentHashMap<Class<*>, Method>()
    private val executorFields = ConcurrentHashMap<Class<*>, Field>()

    private val enabledField: Field? by lazy {
        runCatching {
            Class.forName(
                "org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking",
            ).getField("enabled")
        }.getOrNull()
    }

    override fun isActive(): Boolean =
        runCatching { enabledField?.getBoolean(null) == true }.getOrDefault(false)

    override fun executeWorld(plugin: Plugin, world: World, task: Runnable): Boolean {
        if (!plugin.isEnabled || !isActive()) return false
        val handle = worldHandle(world) ?: return false
        val executor = worldExecutor(handle) ?: return false
        executor.execute {
            if (plugin.isEnabled) task.run()
        }
        return true
    }

    override fun executeEntity(plugin: Plugin, entity: Entity, task: Runnable): Boolean {
        val expectedWorld = entity.world
        return executeWorld(plugin, expectedWorld) {
            if (entity.isValid && entity.world === expectedWorld) task.run()
        }
    }

    private fun worldHandle(world: World): Any? = runCatching {
        val method = handleMethods.computeIfAbsent(world.javaClass) {
            it.getMethod("getHandle").apply { isAccessible = true }
        }
        method.invoke(world)
    }.getOrNull()

    private fun worldExecutor(handle: Any): Executor? = runCatching {
        val field = executorFields.computeIfAbsent(handle.javaClass) {
            it.getField("tickExecutor").apply { isAccessible = true }
        }
        field.get(handle) as? Executor
    }.getOrNull()
}
