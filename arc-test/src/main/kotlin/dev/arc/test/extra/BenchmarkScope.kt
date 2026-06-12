package dev.arc.test.extra

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Lightweight performance assertions for CI regression detection.
 *
 * ```kotlin
 * assertUnder(50.ms) { heavyInventoryCalc(player) }
 * benchmark("loadProfile") { loadProfile(uuid) }
 *     .assertP99Under(100.milliseconds)
 * ```
 */
object BenchmarkScope {

    /** Assert a suspend block completes in under [limitMs] milliseconds. */
    suspend fun assertUnder(limitMs: Long, block: suspend () -> Unit) {
        val elapsed = measureTime { block() }
        check(elapsed.inWholeMilliseconds <= limitMs) {
            "Expected block to complete in <${limitMs}ms but took ${elapsed.inWholeMilliseconds}ms"
        }
    }

    /** Assert a suspend block completes in under [limit]. */
    suspend fun assertUnder(limit: Duration, block: suspend () -> Unit) =
        assertUnder(limit.inWholeMilliseconds, block)

    /**
     * Run [block] [iterations] times and return timing statistics.
     *
     * ```kotlin
     * benchmark(50) { expensiveQuery() }.assertP99Under(20.milliseconds)
     * ```
     */
    suspend fun benchmark(iterations: Int = 50, block: suspend () -> Unit): BenchmarkResult {
        val samples = LongArray(iterations)
        repeat(iterations) { i -> samples[i] = measureTime { block() }.inWholeMilliseconds }
        return BenchmarkResult(samples.sorted())
    }
}

class BenchmarkResult(private val sortedMs: List<Long>) {
    val min: Long get() = sortedMs.first()
    val max: Long get() = sortedMs.last()
    val mean: Double get() = sortedMs.average()
    fun p(percentile: Int): Long = sortedMs[(sortedMs.size * percentile / 100).coerceIn(0, sortedMs.size - 1)]

    fun assertP50Under(limit: Duration) = assertPercentileUnder(50, limit)
    fun assertP95Under(limit: Duration) = assertPercentileUnder(95, limit)
    fun assertP99Under(limit: Duration) = assertPercentileUnder(99, limit)

    private fun assertPercentileUnder(pct: Int, limit: Duration) {
        val actual = p(pct)
        check(actual <= limit.inWholeMilliseconds) {
            "p$pct expected <${limit.inWholeMilliseconds}ms but was ${actual}ms (min=$min max=$max mean=%.1f)".format(mean)
        }
    }

    override fun toString() = "Benchmark(min=$min p50=${p(50)} p95=${p(95)} p99=${p(99)} max=$max mean=%.1fms)".format(mean)
}

/** Extension shorthand: `50.ms` */
val Int.ms: Duration get() = this.milliseconds
