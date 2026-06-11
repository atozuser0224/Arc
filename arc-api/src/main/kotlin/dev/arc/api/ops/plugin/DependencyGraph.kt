package dev.arc.api.ops.plugin

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * Computes plugin dependency relationships for reload-chain planning.
 *
 * "Dependents" of P = plugins that list P in `depend` or `softdepend`; those
 * must be disabled *before* P and re-enabled *after* P. Read-only: it only walks
 * the loaded plugins' description files.
 */
object DependencyGraph {

    /** Direct dependents of [name] (plugins that depend or softdepend on it). */
    fun directDependents(name: String): List<Plugin> {
        return Bukkit.getPluginManager().plugins.filter { p ->
            val d = p.description
            d.depend.any { it.equals(name, true) } || d.softDepend.any { it.equals(name, true) }
        }
    }

    /** Transitive dependents of [name], nearest first (excludes [name] itself). */
    fun transitiveDependents(name: String): List<Plugin> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Plugin>()
        val queue = ArrayDeque(directDependents(name))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            if (!seen.add(p.name)) continue
            out.add(p)
            queue.addAll(directDependents(p.name))
        }
        return out
    }

    /**
     * Reload-chain plan for [target].
     *
     * - [disableOrder]: dependents first, target last.
     * - [enableOrder]: target first, dependents after (reverse).
     *
     * Only currently-enabled plugins are included.
     */
    data class ChainPlan(val target: String, val disableOrder: List<String>, val enableOrder: List<String>) {
        fun render(): String = buildString {
            appendLine("===== Arc reload-chain: $target =====")
            appendLine("disable order: ${disableOrder.joinToString(" -> ")}")
            appendLine("enable order : ${enableOrder.joinToString(" -> ")}")
            if (disableOrder.size == 1) appendLine("(no dependents — single-plugin reload)")
        }
    }

    fun chainPlan(target: Plugin): ChainPlan {
        val dependents = transitiveDependents(target.name).filter { it.isEnabled }
        val disable = (dependents.map { it.name } + target.name)
        val enable = disable.reversed()
        return ChainPlan(target.name, disable, enable)
    }
}
