package dev.arc.server

import dev.arc.api.Arc
import dev.arc.api.control.ArcControlCommand
import dev.arc.api.nms.GatedArcNms
import dev.arc.api.ops.ArcOps
import dev.arc.api.npc.ArcNpcs
import dev.arc.api.player.ArcClientWorldStates
import dev.arc.api.registry.ArcRegistries
import dev.arc.server.nms.NmsClientWorldStateBackend
import dev.arc.server.nms.NmsNpcBackend
import dev.arc.server.nms.NmsRegistryBackend
import dev.arc.server.nms.ReflectiveArcNms
import dev.arc.server.scheduling.LeafParallelWorldScheduler
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires Arc's server-side implementations into the [Arc] facade.
 *
 * Call one of the [install] overloads once, early in server start-up. Both are
 * idempotent for the NMS bridge.
 */
object ArcBootstrap {

    private val installed = AtomicBoolean()
    private val standaloneCommandInstalled = AtomicBoolean()
    private val commandOwners = ConcurrentHashMap.newKeySet<Plugin>()

    /** Install the (feature-gated) NMS bridge only. */
    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        // Load config first so settings/features are applied before the bridge activates.
        runCatching {
            Arc.config.reload()
        }.onFailure { e ->
            java.util.logging.Logger.getLogger("Arc").warning("[Arc] Config load failed: ${e.message}")
        }
        // Wrap the reflective bridge so every NMS group obeys its feature flag.
        try {
            val provider = ReflectiveArcNms()
            provider.server.probeCapabilities()
            Arc.registerNms(GatedArcNms(provider))
            Arc.registerThreadScheduler(LeafParallelWorldScheduler)
            ArcRegistries.backend = NmsRegistryBackend
            ArcNpcs.backend = NmsNpcBackend
            ArcClientWorldStates.installBackend(NmsClientWorldStateBackend)
            if (standaloneCommandInstalled.compareAndSet(false, true)) {
                try {
                    ArcControlCommand.register()
                } catch (error: Throwable) {
                    standaloneCommandInstalled.set(false)
                    throw error
                }
            }
        } catch (error: Throwable) {
            installed.set(false)
            throw error
        }
    }

    /** Install the NMS bridge and register the built-in `/arc` control command. */
    @JvmStatic
    fun install(plugin: Plugin) {
        install()
        if (!standaloneCommandInstalled.get() && commandOwners.add(plugin)) {
            try {
                ArcControlCommand.register(plugin)
            } catch (error: Throwable) {
                commandOwners.remove(plugin)
                throw error
            }
        }
        // Boot the Arc Operations Suite (diagnostics, lag-spike capture, status
        // server, /arc ops subcommands). Needs a plugin to own its repeating tasks.
        runCatching { ArcOps.install(plugin) }.onFailure { e ->
            plugin.logger.warning("[Arc] ArcOps install failed: ${e.message}")
        }
    }
}
