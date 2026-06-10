package dev.arc.api

import dev.arc.api.control.ArcFeatures
import dev.arc.api.control.ArcSettings
import dev.arc.api.nms.ArcNms
import dev.arc.api.scheduling.ArcThreadScheduler

/**
 * Central control hub for Arc.
 *
 * - [settings] — live, thread-safe tunables for the optimization helpers.
 * - [features] — runtime on/off flags for Arc's built-in features.
 * - [nms]      — the NMS bridge (installed by `arc-server` at start-up).
 *
 * Everything Arc adds can be controlled from here at runtime:
 *
 * ```
 * Arc.settings.tickBudgetMillis = 4
 * Arc.features.disable(ArcFeatures.MENUS)
 * Arc.nms.entities.saveNbt(entity)
 * ```
 */
object Arc {

    /** Live, thread-safe tunables shared by Arc's optimization helpers. */
    @JvmField
    val settings: ArcSettings = ArcSettings()

    /** Runtime feature-flag registry. */
    @JvmField
    val features: ArcFeatures = ArcFeatures()

    @Volatile
    private var nmsProvider: ArcNms? = null

    @Volatile
    private var threadSchedulerProvider: ArcThreadScheduler? = null

    init {
        // Register all built-in toggleable features (on by default).
        ArcFeatures.BUILTINS.forEach { features.register(it, default = true) }
    }

    /** Installed by `arc-server` at start-up. Calling twice replaces the bridge. */
    fun registerNms(provider: ArcNms) {
        nmsProvider = provider
    }

    /** Installed by server integrations that have a non-standard tick owner. */
    fun registerThreadScheduler(provider: ArcThreadScheduler) {
        threadSchedulerProvider = provider
    }

    internal val threadScheduler: ArcThreadScheduler?
        get() = threadSchedulerProvider?.takeIf { it.isActive() }

    /** The NMS bridge. Throws if `arc-server` has not initialised it yet. */
    val nms: ArcNms
        get() = nmsProvider
            ?: error("Arc NMS provider not initialised — is arc-server installed and started?")

    /** Whether the NMS bridge is available. */
    val isReady: Boolean
        get() = nmsProvider != null
}
