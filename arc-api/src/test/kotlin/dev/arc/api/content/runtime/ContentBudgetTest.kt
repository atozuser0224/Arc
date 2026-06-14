package dev.arc.api.content.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentBudgetTest {

    @Test
    fun `repeated slow handlers throttle then recover`() {
        var now = 0L
        val budget = ContentBudget(
            maxHandlerNanos = 100,
            violationsBeforeThrottle = 2,
            throttleNanos = 1_000,
            clock = { now },
        )

        budget.record("magic:wand", 101)
        assertTrue(budget.allows("magic:wand"))
        budget.record("magic:wand", 200)
        assertFalse(budget.allows("magic:wand"))

        now = 1_001
        assertTrue(budget.allows("magic:wand"))
        assertEquals(1, budget.metrics("magic:wand").throttles)
    }

    @Test
    fun `queue permits are bounded and reusable`() {
        val budget = ContentBudget(maxQueuedTasks = 1)

        assertTrue(budget.tryAcquireQueue())
        assertFalse(budget.tryAcquireQueue())
        budget.releaseQueue()
        assertTrue(budget.tryAcquireQueue())
    }
}
