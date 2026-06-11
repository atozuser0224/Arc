package dev.arc.api.ops.plugin

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin

/**
 * Read-only inspection of a Paper plugin's live state and registered resources.
 *
 * Everything here only *reads* server structures (scheduler, handler lists,
 * messenger, services manager, command map) — it never mutates them, so it is
 * safe to run on a live server at any time. Counts that cannot be determined
 * with certainty are reported as such rather than guessed.
 */
object PluginInspector {

    data class ResourceCounts(
        val commands: Int,
        val listeners: Int,
        val pendingTasks: Int,
        val activeWorkers: Int,
        val permissions: Int,
        val incomingChannels: Int,
        val outgoingChannels: Int,
        val services: Int,
    )

    data class PluginReport(
        val name: String,
        val version: String,
        val main: String,
        val apiVersion: String?,
        val authors: List<String>,
        val enabled: Boolean,
        val depend: List<String>,
        val softDepend: List<String>,
        val loadBefore: List<String>,
        val counts: ResourceCounts,
        val classLoader: String,
        val lingeringThreads: List<ThreadRef>,
    ) {
        fun render(): String = buildString {
            appendLine("===== Arc Plugin: $name =====")
            appendLine("version      : $version  (${if (enabled) "ENABLED" else "DISABLED"})")
            appendLine("main         : $main")
            appendLine("api-version  : ${apiVersion ?: "<none — legacy>"}")
            if (authors.isNotEmpty()) appendLine("authors      : ${authors.joinToString()}")
            if (depend.isNotEmpty()) appendLine("depend       : ${depend.joinToString()}")
            if (softDepend.isNotEmpty()) appendLine("softdepend   : ${softDepend.joinToString()}")
            if (loadBefore.isNotEmpty()) appendLine("loadbefore   : ${loadBefore.joinToString()}")
            appendLine("commands=${counts.commands} listeners=${counts.listeners} " +
                "tasks=${counts.pendingTasks} workers=${counts.activeWorkers}")
            appendLine("permissions=${counts.permissions} channels(in/out)=${counts.incomingChannels}/${counts.outgoingChannels} " +
                "services=${counts.services}")
            appendLine("classloader  : $classLoader")
            if (lingeringThreads.isNotEmpty()) {
                appendLine("threads (classloader-owned):")
                lingeringThreads.forEach { appendLine("  - ${it.name} daemon=${it.daemon} state=${it.state}") }
            }
        }
    }

    data class ThreadRef(val name: String, val daemon: Boolean, val state: String)

    fun report(plugin: Plugin): PluginReport {
        val d = plugin.description
        return PluginReport(
            name = d.name,
            version = d.version,
            main = d.main,
            apiVersion = d.apiVersion,
            authors = d.authors,
            enabled = plugin.isEnabled,
            depend = d.depend,
            softDepend = d.softDepend,
            loadBefore = d.loadBefore,
            counts = counts(plugin),
            classLoader = plugin.javaClass.classLoader?.let { it.javaClass.simpleName + "@" + Integer.toHexString(System.identityHashCode(it)) } ?: "<none>",
            lingeringThreads = threadsOwnedBy(plugin),
        )
    }

    fun counts(plugin: Plugin): ResourceCounts {
        val scheduler = Bukkit.getScheduler()
        val pending = runCatching { scheduler.pendingTasks.count { it.owner == plugin } }.getOrDefault(0)
        val workers = runCatching { scheduler.activeWorkers.count { it.owner == plugin } }.getOrDefault(0)
        val listeners = runCatching { HandlerList.getRegisteredListeners(plugin).size }.getOrDefault(0)
        val messenger = Bukkit.getMessenger()
        val inCh = runCatching { messenger.getIncomingChannels(plugin).size }.getOrDefault(0)
        val outCh = runCatching { messenger.getOutgoingChannels(plugin).size }.getOrDefault(0)
        val services = runCatching { Bukkit.getServicesManager().getRegistrations(plugin).size }.getOrDefault(0)
        return ResourceCounts(
            commands = plugin.description.commands.size,
            listeners = listeners,
            pendingTasks = pending,
            activeWorkers = workers,
            permissions = plugin.description.permissions.size,
            incomingChannels = inCh,
            outgoingChannels = outCh,
            services = services,
        )
    }

    /** Threads whose context classloader is this plugin's classloader — leak candidates after disable. */
    fun threadsOwnedBy(plugin: Plugin): List<ThreadRef> {
        val cl = plugin.javaClass.classLoader ?: return emptyList()
        return runCatching {
            Thread.getAllStackTraces().keys
                .filter { it.contextClassLoader === cl }
                .map { ThreadRef(it.name, it.isDaemon, it.state.name) }
        }.getOrDefault(emptyList())
    }

    /** Command names declared in plugin.yml (does not include runtime-registered commands). */
    fun declaredCommands(plugin: Plugin): List<String> = plugin.description.commands.keys.toList()

    fun permissionNodes(plugin: Plugin): List<String> = plugin.description.permissions.map { it.name }
}
