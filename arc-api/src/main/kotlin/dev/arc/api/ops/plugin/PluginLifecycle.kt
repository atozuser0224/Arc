package dev.arc.api.ops.plugin

import dev.arc.api.lifecycle.ArcReloadable
import org.bukkit.Bukkit
import org.bukkit.command.PluginCommand
import org.bukkit.command.SimpleCommandMap
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Live plugin enable / disable / reload operations.
 *
 * **Safety contract.** `enable` and `disable` use the official [org.bukkit.plugin.PluginManager]
 * API — the same calls Bukkit makes at start/stop. `reload` (disable → reload
 * classes → enable) is **best-effort and experimental**: no Paper plugin is
 * guaranteed to reload cleanly, so every step is isolated and the result lists
 * exactly what happened. Callers must already have gated dangerous operations
 * behind confirmation and permissions.
 *
 * After a disable Arc reports classloader-owned threads as leak candidates; it
 * cannot force-collect a leaked classloader.
 */
object PluginLifecycle {

    data class Result(val success: Boolean, val lines: List<String>) {
        fun render(): String = lines.joinToString("\n")
    }

    fun disable(plugin: Plugin): Result {
        val out = ArrayList<String>()
        if (!plugin.isEnabled) return Result(false, listOf("[Arc] ${plugin.name} is already disabled"))
        (plugin as? ArcReloadable)?.let { runCatching { it.cleanupResources() } }
        val ok = runCatching { Bukkit.getPluginManager().disablePlugin(plugin) }
            .onFailure { out += "[Arc] disablePlugin threw: ${it.message}" }.isSuccess
        val disabled = ok && !plugin.isEnabled
        out += if (disabled) "[Arc] disabled ${plugin.name}" else "[Arc] ${plugin.name} did not fully disable"
        leakReport(plugin, out)
        return Result(disabled, out)
    }

    fun enable(plugin: Plugin): Result {
        val out = ArrayList<String>()
        if (plugin.isEnabled) return Result(false, listOf("[Arc] ${plugin.name} is already enabled"))
        missingDependencies(plugin).let { if (it.isNotEmpty()) out += "[Arc] missing/disabled dependencies: ${it.joinToString()}" }
        val ok = runCatching { Bukkit.getPluginManager().enablePlugin(plugin) }
            .onFailure { out += "[Arc] enablePlugin threw: ${it.message}" }.isSuccess
        out += if (ok && plugin.isEnabled) "[Arc] enabled ${plugin.name}" else "[Arc] ${plugin.name} did not enable"
        return Result(ok && plugin.isEnabled, out)
    }

    /** Experimental: disable, reload classes from the jar on disk, re-enable. */
    fun reload(plugin: Plugin): Result {
        val out = ArrayList<String>()
        val file = pluginFile(plugin)
        if (file == null || !file.isFile) {
            return Result(false, listOf("[Arc] cannot locate jar for ${plugin.name}; reload aborted (try disable+enable)"))
        }
        out += "[Arc] reloading ${plugin.name} from ${file.name} (experimental)"
        (plugin as? ArcReloadable)?.let { runCatching { it.beforeReload() } }

        val disableOk = runCatching { Bukkit.getPluginManager().disablePlugin(plugin) }
            .onFailure { out += "[Arc] disable step failed: ${it.message}" }.isSuccess
        if (!disableOk || plugin.isEnabled) {
            out += "[Arc] reload aborted: ${plugin.name} could not be disabled"
            return Result(false, out)
        }
        unregisterCommands(plugin, out)
        leakReport(plugin, out)

        val fresh = runCatching { Bukkit.getPluginManager().loadPlugin(file) }
            .onFailure { out += "[Arc] load step failed: ${it.message}; ${plugin.name} is now DISABLED" }
            .getOrNull() ?: return Result(false, out)

        // loadPlugin() already calls onLoad() internally — do NOT call it again here
        val enabled = runCatching { Bukkit.getPluginManager().enablePlugin(fresh) }
            .onFailure { out += "[Arc] enable step failed: ${it.message}" }.isSuccess && fresh.isEnabled
        if (enabled) (fresh as? ArcReloadable)?.let { runCatching { it.afterReload() } }
        resyncCommandTree()
        out += if (enabled) "[Arc] reloaded ${fresh.name} ${fresh.description.version}" else "[Arc] ${plugin.name} failed to re-enable"
        return Result(enabled, out)
    }

    fun restart(plugin: Plugin): Result {
        val d = disable(plugin)
        val e = enable(plugin)
        return Result(e.success, d.lines + e.lines)
    }

    private fun missingDependencies(plugin: Plugin): List<String> {
        val pm = Bukkit.getPluginManager()
        return plugin.description.depend.filter { pm.getPlugin(it)?.isEnabled != true }
    }

    private fun leakReport(plugin: Plugin, out: MutableList<String>) {
        val threads = PluginInspector.threadsOwnedBy(plugin)
        if (threads.isEmpty()) {
            out += "[Arc] leak check: no classloader-owned threads remain"
        } else {
            out += "[Arc] leak check: ${threads.size} thread(s) still reference the plugin classloader:"
            threads.forEach { out += "  - ${it.name} daemon=${it.daemon} state=${it.state}" }
        }
    }

    private fun pluginFile(plugin: Plugin): File? = runCatching {
        val m = JavaPlugin::class.java.getDeclaredMethod("getFile").apply { isAccessible = true }
        m.invoke(plugin) as? File
    }.getOrNull()

    private fun unregisterCommands(plugin: Plugin, out: MutableList<String>) {
        val map = Bukkit.getCommandMap() as? SimpleCommandMap ?: return
        runCatching {
            val known = map.knownCommands
            val owned = known.values.filterIsInstance<PluginCommand>().filter { it.plugin === plugin }.toSet()
            owned.forEach { it.unregister(map) }
            known.entries.removeIf { it.value in owned }
            if (owned.isNotEmpty()) out += "[Arc] unregistered ${owned.size} command(s)"
        }.onFailure { out += "[Arc] command unregister skipped: ${it.message}" }
    }

    /** Execute a reload-chain plan: disable in order, then enable in reverse. */
    fun reloadChain(plan: DependencyGraph.ChainPlan): List<Result> {
        val results = ArrayList<Result>()
        // Disable phase — dependents first
        for (name in plan.disableOrder) {
            val plugin = Bukkit.getPluginManager().getPlugin(name) ?: continue
            if (!plugin.isEnabled) continue
            results += disable(plugin)
        }
        // Enable phase — dependencies first (reverse order)
        for (name in plan.enableOrder) {
            val plugin = Bukkit.getPluginManager().getPlugin(name) ?: continue
            if (plugin.isEnabled) continue
            results += enable(plugin)
        }
        resyncCommandTree()
        return results
    }

    private fun resyncCommandTree() {
        runCatching { Bukkit.getOnlinePlayers().forEach { it.updateCommands() } }
    }
}
