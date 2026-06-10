package dev.arc.api.scheduling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class BoundedTaskQueueTest {

    @Test
    fun `rejects work at the live limit`() {
        var limit = 2
        val queue = BoundedTaskQueue { limit }

        assertTrue(queue.offer(Runnable {}))
        assertTrue(queue.offer(Runnable {}))
        assertFalse(queue.offer(Runnable {}))

        limit = 3
        assertTrue(queue.offer(Runnable {}))
        assertEquals(3, queue.size)
    }

    @Test
    fun `close discards pending work and permanently rejects producers`() {
        val queue = BoundedTaskQueue { 10 }
        queue.offer(Runnable {})
        queue.offer(Runnable {})

        assertEquals(2, queue.close())
        assertTrue(queue.isClosed)
        assertEquals(0, queue.size)
        assertNull(queue.poll())
        assertFalse(queue.offer(Runnable {}))
    }

    @Test
    fun `concurrent producers never exceed the limit`() {
        val limit = 50
        val queue = BoundedTaskQueue { limit }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)

        repeat(8) {
            executor.execute {
                start.await()
                repeat(100) { queue.offer(Runnable {}) }
                done.countDown()
            }
        }

        start.countDown()
        done.await()
        executor.shutdownNow()

        assertEquals(limit, queue.size)
    }
}
