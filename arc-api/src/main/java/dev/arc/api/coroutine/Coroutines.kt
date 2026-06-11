package dev.arc.api.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * A [CoroutineDispatcher] that resumes continuations on Paper's global region
 * thread. The historical class name is retained for source compatibility.
 */
class BukkitMainDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!plugin.isEnabled) {
            context[Job]?.cancel(CancellationException("Plugin disabled"))
            return
        }
        if (Bukkit.isGlobalTickThread()) {
            block.run()
        } else {
            runCatching { Bukkit.getGlobalRegionScheduler().execute(plugin, block) }
                .onFailure { error ->
                    val cancellation = CancellationException("Dispatch rejected")
                    cancellation.initCause(error)
                    context[Job]?.cancel(cancellation)
                }
        }
    }
}

private val dispatchers = ConcurrentHashMap<Plugin, BukkitMainDispatcher>()
private val scopes = ConcurrentHashMap<Plugin, CoroutineScope>()
private val lifecycleListeners = ConcurrentHashMap<Plugin, Listener>()

private fun Plugin.ensureCoroutineLifecycle() {
    if (lifecycleListeners.containsKey(this)) return
    val plugin = this
    val listener = object : Listener {
        @EventHandler
        fun onDisable(event: PluginDisableEvent) {
            if (event.plugin !== plugin) return
            scopes.remove(plugin)?.cancel("Plugin disabled")
            dispatchers.remove(plugin)
            lifecycleListeners.remove(plugin)?.let(HandlerList::unregisterAll)
        }
    }
    if (lifecycleListeners.putIfAbsent(plugin, listener) != null) return

    val register = Runnable {
        if (plugin.isEnabled && lifecycleListeners[plugin] === listener) {
            Bukkit.getPluginManager().registerEvents(listener, plugin)
        }
    }
    if (Bukkit.isGlobalTickThread()) {
        register.run()
    } else {
        runCatching { Bukkit.getGlobalRegionScheduler().execute(plugin, register) }
            .onFailure {
                lifecycleListeners.remove(plugin, listener)
                scopes.remove(plugin)?.cancel("Lifecycle registration rejected")
                dispatchers.remove(plugin)
            }
    }
}

/** The plugin's shared global-region dispatcher. */
val Plugin.mainDispatcher: BukkitMainDispatcher
    get() {
        ensureCoroutineLifecycle()
        return dispatchers.computeIfAbsent(this) { BukkitMainDispatcher(it) }
    }

/** A supervised [CoroutineScope] bound to Paper's global region thread. */
val Plugin.scope: CoroutineScope
    get() {
        ensureCoroutineLifecycle()
        return scopes.computeIfAbsent(this) { CoroutineScope(SupervisorJob() + mainDispatcher) }
    }

/**
 * Launch a coroutine on Paper's global region thread.
 *
 * ```
 * plugin.launchMain {
 *     player.send("<gray>3...")
 *     plugin.delayTicks(20); player.send("<gray>2...")
 *     plugin.delayTicks(20); player.send("<gray>1...")
 *     plugin.delayTicks(20); player.send("<green>Go!")
 * }
 * ```
 */
fun Plugin.launchMain(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

/** Alias for [launchMain] — launch a coroutine on the main thread. */
fun Plugin.launch(block: suspend CoroutineScope.() -> Unit): Job = launchMain(block)

/**
 * Launch a coroutine on the main thread that repeats [block] every [periodTicks] ticks until cancelled.
 * The first execution happens immediately; delays occur after each invocation.
 */
fun Plugin.launchEveryTicks(periodTicks: Long, block: suspend CoroutineScope.() -> Unit): Job {
    val plugin = this
    return scope.launch {
        while (isActive) {
            block()
            plugin.delayTicks(periodTicks)
        }
    }
}

/**
 * Suspend for exactly [ticks] scheduler ticks. Unlike a millisecond delay this
 * remains aligned with the server tick loop during lag.
 */
suspend fun Plugin.delayTicks(ticks: Long) {
    require(ticks >= 0) { "ticks must be >= 0" }
    if (ticks == 0L) return
    suspendCancellableCoroutine { continuation ->
        val task = Bukkit.getGlobalRegionScheduler().runDelayed(
            this,
            {
                if (continuation.isActive) continuation.resume(Unit)
            },
            ticks,
        )
        continuation.invokeOnCancellation { task.cancel() }
    }
}
