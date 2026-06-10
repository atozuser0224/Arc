package dev.arc.api.control

import java.util.concurrent.CopyOnWriteArrayList

enum class NmsThreadPolicy {
    /** Reject NMS access when the current thread does not own the target region. */
    STRICT,

    /** Allow access but emit one warning per operation name. */
    WARN,

    /** Disable ownership checks. Intended only for controlled server internals. */
    UNSAFE,
}

/**
 * Runtime-tunable knobs for Arc's optimization helpers. All access is
 * thread-safe and changes take effect on next use, so a command, config loader,
 * or another plugin can retune Arc live through [dev.arc.api.Arc.settings].
 *
 * Register [onChange] to react to live edits.
 */
class ArcSettings internal constructor() {

    /** Default per-tick time budget (ms) for [dev.arc.api.scheduling.TickDispatcher]. */
    @Volatile
    var tickBudgetMillis: Long = 2
        set(value) {
            field = value.coerceAtLeast(1)
            notifyChanged("tickBudgetMillis")
        }

    /** Default TTL (ms) suggested for [dev.arc.api.util.TtlCache] consumers. */
    @Volatile
    var defaultTtlMillis: Long = 1_000
        set(value) {
            field = value.coerceAtLeast(0)
            notifyChanged("defaultTtlMillis")
        }

    /** Default max size suggested for [dev.arc.api.util.ObjectPool] consumers. */
    @Volatile
    var defaultPoolSize: Int = 64
        set(value) {
            field = value.coerceAtLeast(0)
            notifyChanged("defaultPoolSize")
        }

    /** Maximum number of chunk-owned batch slices active at the same time. */
    @Volatile
    var regionBatchConcurrency: Int = 2
        set(value) {
            field = value.coerceAtLeast(1)
            notifyChanged("regionBatchConcurrency")
        }

    /** Maximum queued tasks accepted by each default [TickDispatcher][dev.arc.api.scheduling.TickDispatcher]. */
    @Volatile
    var dispatcherMaxPending: Int = 10_000
        set(value) {
            field = value.coerceAtLeast(1)
            notifyChanged("dispatcherMaxPending")
        }

    /** Behaviour when synchronous NMS APIs are called from a non-owning thread. */
    @Volatile
    var nmsThreadPolicy: NmsThreadPolicy = NmsThreadPolicy.STRICT
        set(value) {
            field = value
            notifyChanged("nmsThreadPolicy")
        }

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    /** Observe setting changes; the changed property name is passed to [listener]. */
    fun onChange(listener: (changedKey: String) -> Unit) {
        listeners.add(listener)
    }

    private fun notifyChanged(key: String) {
        listeners.forEach { it(key) }
    }
}
