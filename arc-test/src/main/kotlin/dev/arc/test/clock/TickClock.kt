package dev.arc.test.clock

import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * Unified tick controller that advances both the Bukkit scheduler mock
 * and the kotlinx-coroutines [TestCoroutineScheduler] together.
 *
 * `advance(20)` == advance 1 second:
 * - Fires all Bukkit tasks due within those 20 ticks
 * - Advances coroutine delays by 20 × 50 ms = 1000 ms
 */
class TickClock(
    private val server: ServerMock,
    private val coroutineScheduler: TestCoroutineScheduler,
) {
    private var currentTick: Long = 0L

    val tick: Long get() = currentTick

    /** Advance by [ticks] ticks (50 ms each). */
    fun advance(ticks: Int) {
        val bukkit = server.scheduler as? BukkitSchedulerMock
        repeat(ticks) {
            bukkit?.performOneTick()
            coroutineScheduler.advanceTimeBy(50L)
            currentTick++
        }
    }

    /** Advance until the given absolute tick. */
    fun advanceTo(absoluteTick: Long) {
        val delta = (absoluteTick - currentTick).toInt()
        if (delta > 0) advance(delta)
    }

    /** Advance by [seconds] real-time seconds (= seconds × 20 ticks). */
    fun advanceSeconds(seconds: Int) = advance(seconds * 20)

    fun reset() { currentTick = 0L }
}
