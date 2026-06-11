@file:JvmName("Pipelines")

package dev.arc.api.task

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bukkit.plugin.Plugin

// ---------------------------------------------------------------------------

/**
 * A named step result carrying its value and execution time.
 */
data class StepResult<T>(val name: String, val value: T, val elapsedMs: Long)

/**
 * Context available inside a [pipeline] block.
 * Steps run sequentially by default; use [parallel] to fan out.
 */
class PipelineScope {
    val steps = mutableListOf<StepResult<*>>()

    /**
     * Run [block] as a named pipeline step. Returns the result.
     * ```kotlin
     * val profile = step("fetch-profile") { fetchProfile(uuid) }
     * ```
     */
    suspend fun <T> step(name: String, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        val value = block()
        steps += StepResult(name, value as Any, System.currentTimeMillis() - start)
        return value
    }

    /**
     * Run multiple steps concurrently and await all results.
     * ```kotlin
     * val (a, b) = parallel(
     *     step("enrich")   { enrich(profile) },
     *     step("validate") { validate(data)  },
     * )
     * ```
     */
    suspend fun <T> parallel(vararg blocks: suspend PipelineScope.() -> T): List<T> =
        coroutineScope {
            blocks.map { async { PipelineScope().run { it() } } }.awaitAll()
        }

    /**
     * Run two concurrent steps and return their results as a [Pair].
     */
    suspend fun <A, B> parallel(
        blockA: suspend PipelineScope.() -> A,
        blockB: suspend PipelineScope.() -> B,
    ): Pair<A, B> = coroutineScope {
        val a = async { PipelineScope().run { blockA() } }
        val b = async { PipelineScope().run { blockB() } }
        a.await() to b.await()
    }

    /**
     * Run three concurrent steps and return their results as a [Triple].
     */
    suspend fun <A, B, C> parallel(
        blockA: suspend PipelineScope.() -> A,
        blockB: suspend PipelineScope.() -> B,
        blockC: suspend PipelineScope.() -> C,
    ): Triple<A, B, C> = coroutineScope {
        val a = async { PipelineScope().run { blockA() } }
        val b = async { PipelineScope().run { blockB() } }
        val c = async { PipelineScope().run { blockC() } }
        Triple(a.await(), b.await(), c.await())
    }
}

/**
 * Execution summary returned from [pipeline].
 */
data class PipelineResult<T>(
    val name: String,
    val value: T,
    val totalMs: Long,
    val steps: List<StepResult<*>>,
)

// ---------------------------------------------------------------------------

/**
 * Run a named coroutine pipeline with sequential and parallel steps.
 * Returns a [PipelineResult] containing the final value and per-step timings.
 *
 * ```kotlin
 * plugin.launch {
 *     val result = pipeline("load-player") {
 *         val profile = step("fetch") { fetchProfile(uuid) }
 *         val (data, prefs) = parallel(
 *             { step("db")    { loadFromDb(uuid)    } },
 *             { step("prefs") { loadPrefs(uuid)     } },
 *         )
 *         PlayerBundle(profile, data, prefs)
 *     }
 *     player.sendMessage("Loaded in ${result.totalMs}ms")
 * }
 * ```
 */
suspend fun <T> pipeline(name: String = "pipeline", block: suspend PipelineScope.() -> T): PipelineResult<T> {
    val scope = PipelineScope()
    val start = System.currentTimeMillis()
    val value = scope.block()
    return PipelineResult(name, value, System.currentTimeMillis() - start, scope.steps)
}

/** Convenience extension — launch a pipeline from a plugin scope. */
suspend fun <T> Plugin.pipeline(name: String = "pipeline", block: suspend PipelineScope.() -> T): PipelineResult<T> =
    dev.arc.api.task.pipeline(name, block)
