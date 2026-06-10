package dev.arc.api.control

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A named, runtime-toggleable feature flag. */
class ArcFeature internal constructor(val id: String, enabled: Boolean) {
    @Volatile
    var isEnabled: Boolean = enabled
}

/**
 * Central registry of toggleable Arc features. Any optimization helper registers
 * a flag here; commands / config loaders / other plugins flip them at runtime
 * through the single control surface [dev.arc.api.Arc.features].
 *
 * ```
 * Arc.features.disable(ArcFeatures.TICK_DISPATCHER)   // turn off load-spreading live
 * if (Arc.features.isEnabled(ArcFeatures.MENUS)) { … }
 * ```
 */
class ArcFeatures internal constructor() {

    private val features = ConcurrentHashMap<String, ArcFeature>()
    private val listeners = CopyOnWriteArrayList<(ArcFeature) -> Unit>()

    /** Register (or return the existing) flag for [id], defaulting to [default]. */
    fun register(id: String, default: Boolean = true): ArcFeature =
        features.computeIfAbsent(id) { ArcFeature(it, default) }

    fun get(id: String): ArcFeature? = features[id]

    /** True only if [id] is registered and currently enabled. */
    fun isEnabled(id: String): Boolean = features[id]?.isEnabled ?: false

    fun setEnabled(id: String, enabled: Boolean) {
        val feature = features[id] ?: return
        if (feature.isEnabled != enabled) {
            feature.isEnabled = enabled
            listeners.forEach { it(feature) }
        }
    }

    fun enable(id: String) = setEnabled(id, true)
    fun disable(id: String) = setEnabled(id, false)

    /** Snapshot of all registered features. */
    fun all(): List<ArcFeature> = features.values.toList()

    /** Observe toggles; the changed [ArcFeature] is passed to [listener]. */
    fun onToggle(listener: (ArcFeature) -> Unit) {
        listeners.add(listener)
    }

    companion object {
        /** Canonical ids for Arc's built-in toggleable features. */
        const val MENUS = "arc:menus"
        const val TICK_DISPATCHER = "arc:tick-dispatcher"
        const val COROUTINES = "arc:coroutines"

        /** Master NMS switch; disabling it gates every NMS group at once. */
        const val NMS = "arc:nms"
        const val NMS_ENTITIES = "arc:nms.entities"
        const val NMS_ITEMS = "arc:nms.items"
        const val NMS_BLOCKS = "arc:nms.blocks"
        const val NMS_PLAYERS = "arc:nms.players"
        const val NMS_SERVER = "arc:nms.server"

        /**
         * Fake-server simulation: spawning NMS ServerPlayer entities with no-op
         * connections and running full-pipeline combat / interaction events.
         */
        const val NMS_FAKE = "arc:nms.fake"

        /** All built-in feature ids, registered on by default. */
        @JvmField
        val BUILTINS: List<String> = listOf(
            MENUS, TICK_DISPATCHER, COROUTINES,
            NMS, NMS_ENTITIES, NMS_ITEMS, NMS_BLOCKS, NMS_PLAYERS, NMS_SERVER, NMS_FAKE,
        )
    }
}
