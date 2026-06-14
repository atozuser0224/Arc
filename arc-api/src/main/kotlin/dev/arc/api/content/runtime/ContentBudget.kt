package dev.arc.api.content.runtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

public data class ContentBudgetMetrics(
    public val handlerCount: Long,
    public val totalHandlerNanos: Long,
    public val peakHandlerNanos: Long,
    public val violations: Long,
    public val throttles: Long,
)

public class ContentBudget(
    public val maxQueuedTasks: Int = 4_096,
    public val maxHandlerNanos: Long = 2_000_000,
    public val violationsBeforeThrottle: Int = 3,
    public val throttleNanos: Long = 1_000_000_000,
    private val clock: () -> Long = System::nanoTime,
) {
    private class State {
        var handlerCount = 0L
        var totalHandlerNanos = 0L
        var peakHandlerNanos = 0L
        var violations = 0L
        var consecutiveViolations = 0
        var throttles = 0L
        var throttledUntil = 0L
    }

    private val queued = AtomicInteger()
    private val states = ConcurrentHashMap<String, State>()

    init {
        require(maxQueuedTasks > 0) { "maxQueuedTasks must be positive" }
        require(maxHandlerNanos > 0) { "maxHandlerNanos must be positive" }
        require(violationsBeforeThrottle > 0) { "violationsBeforeThrottle must be positive" }
        require(throttleNanos > 0) { "throttleNanos must be positive" }
    }

    public fun tryAcquireQueue(): Boolean {
        while (true) {
            val current = queued.get()
            if (current >= maxQueuedTasks) return false
            if (queued.compareAndSet(current, current + 1)) return true
        }
    }

    public fun releaseQueue() {
        while (true) {
            val current = queued.get()
            check(current > 0) { "Content queue permit released without acquisition" }
            if (queued.compareAndSet(current, current - 1)) return
        }
    }

    public fun allows(contentId: String): Boolean {
        val state = states[contentId] ?: return true
        synchronized(state) {
            val now = clock()
            if (state.throttledUntil == 0L) return true
            if (now < state.throttledUntil) return false
            state.throttledUntil = 0L
            state.consecutiveViolations = 0
            return true
        }
    }

    public fun record(contentId: String, elapsedNanos: Long) {
        require(contentId.isNotBlank()) { "contentId must not be blank" }
        require(elapsedNanos >= 0) { "elapsedNanos must not be negative" }
        val state = states.computeIfAbsent(contentId) { State() }
        synchronized(state) {
            state.handlerCount++
            state.totalHandlerNanos += elapsedNanos
            state.peakHandlerNanos = maxOf(state.peakHandlerNanos, elapsedNanos)
            if (elapsedNanos > maxHandlerNanos) {
                state.violations++
                state.consecutiveViolations++
                if (state.consecutiveViolations >= violationsBeforeThrottle) {
                    state.throttles++
                    state.throttledUntil = clock() + throttleNanos
                    state.consecutiveViolations = 0
                }
            } else {
                state.consecutiveViolations = 0
            }
        }
    }

    public fun metrics(contentId: String): ContentBudgetMetrics {
        val state = states[contentId] ?: return ContentBudgetMetrics(0, 0, 0, 0, 0)
        synchronized(state) {
            return ContentBudgetMetrics(
                state.handlerCount,
                state.totalHandlerNanos,
                state.peakHandlerNanos,
                state.violations,
                state.throttles,
            )
        }
    }
}
