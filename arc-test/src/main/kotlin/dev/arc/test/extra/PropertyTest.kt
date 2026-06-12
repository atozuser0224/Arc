package dev.arc.test.extra

import kotlin.random.Random

/**
 * Minimal property-based testing support (Kotest-Arb style, no external dep).
 *
 * ```kotlin
 * forAll(arbString(), arbInt(0..1000)) { name, coins ->
 *     // invariant: economy can never go negative
 *     val result = economy.deduct(name, coins)
 *     result.balance >= 0
 * }
 * ```
 */

typealias Arb<T> = (Random) -> T

// ---- Arbitrary generators ----

fun arbString(maxLen: Int = 32): Arb<String> = { rng ->
    (1..rng.nextInt(1, maxLen)).map { ('a'..'z').random(rng) }.joinToString("")
}

fun arbInt(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Arb<Int> = { rng ->
    rng.nextInt(range.first, range.last)
}

fun arbLong(range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Arb<Long> = { rng ->
    rng.nextLong(range.first, range.last)
}

fun arbDouble(from: Double = -1e6, to: Double = 1e6): Arb<Double> = { rng ->
    from + rng.nextDouble() * (to - from)
}

fun arbBool(): Arb<Boolean> = { rng -> rng.nextBoolean() }

fun <T> arbEnum(values: Array<T>): Arb<T> = { rng -> values.random(rng) }

fun <T> arbNullable(inner: Arb<T>, nullProbability: Double = 0.1): Arb<T?> = { rng ->
    if (rng.nextDouble() < nullProbability) null else inner(rng)
}

fun <T> arbOneOf(vararg values: T): Arb<T> = { rng -> values.random(rng) }

// ---- Runners ----

/**
 * Assert that [property] holds for [iterations] random combinations of [a] and [b].
 * Reports the first failing case.
 */
fun <A, B> forAll(
    a: Arb<A>,
    b: Arb<B>,
    iterations: Int = 100,
    seed: Long = System.currentTimeMillis(),
    property: (A, B) -> Boolean,
) {
    val rng = Random(seed)
    repeat(iterations) { i ->
        val va = a(rng); val vb = b(rng)
        check(property(va, vb)) {
            "Property failed at iteration $i with a=$va, b=$vb (seed=$seed)"
        }
    }
}

fun <A> forAll(
    a: Arb<A>,
    iterations: Int = 100,
    seed: Long = System.currentTimeMillis(),
    property: (A) -> Boolean,
) {
    val rng = Random(seed)
    repeat(iterations) { i ->
        val va = a(rng)
        check(property(va)) { "Property failed at iteration $i with a=$va (seed=$seed)" }
    }
}

fun <A, B, C> forAll(
    a: Arb<A>,
    b: Arb<B>,
    c: Arb<C>,
    iterations: Int = 100,
    seed: Long = System.currentTimeMillis(),
    property: (A, B, C) -> Boolean,
) {
    val rng = Random(seed)
    repeat(iterations) { i ->
        val va = a(rng); val vb = b(rng); val vc = c(rng)
        check(property(va, vb, vc)) {
            "Property failed at iteration $i with a=$va, b=$vb, c=$vc (seed=$seed)"
        }
    }
}
