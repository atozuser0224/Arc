package dev.arc.server.nms

import dev.arc.api.Arc
import dev.arc.api.control.NmsThreadPolicy
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

internal object NmsThreadGuard {
    private val warnedOperations = ConcurrentHashMap.newKeySet<String>()
    private val rejected = LongAdder()

    fun requireOwned(owned: Boolean, operation: String) {
        if (owned || Arc.settings.nmsThreadPolicy == NmsThreadPolicy.UNSAFE) return

        val message = "Arc NMS operation '$operation' called from a non-owning thread"
        when (Arc.settings.nmsThreadPolicy) {
            NmsThreadPolicy.STRICT -> {
                rejected.increment()
                throw IllegalStateException("$message; use the async API or region scheduler")
            }
            NmsThreadPolicy.WARN -> if (warnedOperations.add(operation)) {
                Bukkit.getLogger().warning("$message; this may corrupt server state")
            }
            NmsThreadPolicy.UNSAFE -> Unit
        }
    }

    fun rejectedCount(): Long = rejected.sum()

    fun reset() {
        warnedOperations.clear()
        rejected.reset()
    }
}
