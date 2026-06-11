package dev.arc.api.ops.plugin

import dev.arc.api.lifecycle.ArcReloadable
import dev.arc.api.lifecycle.ReloadRating
import org.bukkit.plugin.Plugin

/**
 * Grades how safe it is to reload a given plugin.
 *
 * The grading is deliberately conservative and **never claims a plain Paper
 * plugin is fully safe**: without the [ArcReloadable] contract Arc cannot know
 * what static/reflective/native state a plugin holds, so the best a non-Arc
 * plugin earns is [ReloadRating.UNKNOWN] (clean) or worse. Hard blockers
 * (non-daemon classloader-owned threads, active async workers) force
 * [ReloadRating.UNSAFE].
 */
object ReloadSafetyAnalyzer {

    data class Assessment(
        val plugin: String,
        val rating: ReloadRating,
        val blockers: List<String>,
        val warnings: List<String>,
        val arcManaged: Boolean,
    ) {
        fun render(): String = buildString {
            appendLine("===== Arc reload-safety: $plugin =====")
            appendLine("rating : $rating${if (arcManaged) " (Arc-managed)" else ""}")
            if (blockers.isNotEmpty()) {
                appendLine("blockers (UNSAFE):")
                blockers.forEach { appendLine("  - $it") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("warnings:")
                warnings.forEach { appendLine("  - $it") }
            }
            if (blockers.isEmpty() && warnings.isEmpty()) appendLine("no issues detected by inspection")
            when (rating) {
                ReloadRating.SAFE -> appendLine("note: plugin opts into Arc lifecycle management")
                ReloadRating.UNKNOWN -> appendLine("note: non-Arc plugin — reflective/static/native state cannot be verified")
                ReloadRating.WARNING -> appendLine("note: reload may work but watch the warnings above")
                ReloadRating.UNSAFE -> appendLine("note: reload is likely to leak resources — use --force at your own risk")
            }
        }
    }

    fun assess(plugin: Plugin): Assessment {
        val blockers = ArrayList<String>()
        val warnings = ArrayList<String>()

        val threads = PluginInspector.threadsOwnedBy(plugin)
        val nonDaemon = threads.filter { !it.daemon }
        if (nonDaemon.isNotEmpty()) {
            blockers += "non-daemon thread(s) bound to plugin classloader: ${nonDaemon.joinToString { it.name }}"
        } else if (threads.isNotEmpty()) {
            warnings += "${threads.size} daemon thread(s) bound to plugin classloader (may pin classloader)"
        }

        val counts = PluginInspector.counts(plugin)
        if (counts.activeWorkers > 0) blockers += "${counts.activeWorkers} active async worker(s) still running"

        val dependents = DependencyGraph.directDependents(plugin.name).filter { it.isEnabled }
        if (dependents.isNotEmpty()) {
            warnings += "${dependents.size} enabled dependent(s): ${dependents.joinToString { it.name }} — consider reload-chain"
        }
        if (counts.services > 0) warnings += "provides ${counts.services} service(s); consumers may hold stale references"
        if (counts.incomingChannels + counts.outgoingChannels > 0) {
            warnings += "uses ${counts.incomingChannels + counts.outgoingChannels} plugin-messaging channel(s)"
        }

        // Arc-managed path can reach SAFE.
        val reloadable = plugin as? ArcReloadable
        if (reloadable != null) {
            val result = runCatching { reloadable.canReload() }.getOrNull()
            if (result != null) {
                when (result.rating) {
                    ReloadRating.UNSAFE -> blockers += "ArcReloadable.canReload(): ${result.message ?: "unsafe"}"
                    ReloadRating.WARNING -> warnings += "ArcReloadable.canReload(): ${result.message ?: "warning"}"
                    else -> {}
                }
            }
        }

        val rating = when {
            blockers.isNotEmpty() -> ReloadRating.UNSAFE
            reloadable != null -> if (warnings.isEmpty()) ReloadRating.SAFE else ReloadRating.WARNING
            warnings.isNotEmpty() -> ReloadRating.WARNING
            else -> ReloadRating.UNKNOWN
        }

        return Assessment(plugin.name, rating, blockers, warnings, reloadable != null)
    }
}
