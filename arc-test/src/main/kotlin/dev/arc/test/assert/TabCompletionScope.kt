package dev.arc.test.assert

/**
 * Assertion scope returned by [dev.arc.test.virtual.VirtualPlayer.tabComplete].
 *
 * ```kotlin
 * player.tabComplete("shop ").assertContains("buy", "sell")
 * player.tabComplete("shop b").assertExact("buy")
 * player.tabComplete("shop ").assertNotEmpty()
 * ```
 */
class TabCompletionScope(val completions: List<String>) {

    /** Assert completions contain ALL of the given values. */
    fun assertContains(vararg expected: String): TabCompletionScope = apply {
        val missing = expected.filterNot { it in completions }
        check(missing.isEmpty()) {
            "Tab complete missing: $missing\nFull list: $completions"
        }
    }

    /** Assert completions equal exactly [expected] (order-insensitive). */
    fun assertExact(vararg expected: String): TabCompletionScope = apply {
        val exp = expected.toSortedSet()
        val act = completions.toSortedSet()
        check(exp == act) {
            "Tab complete mismatch.\n  expected: $exp\n  actual:   $act"
        }
    }

    /** Assert none of [excluded] appear in completions. */
    fun assertNotContains(vararg excluded: String): TabCompletionScope = apply {
        val found = excluded.filter { it in completions }
        check(found.isEmpty()) {
            "Tab complete unexpectedly contained: $found"
        }
    }

    /** Assert completions list is non-empty. */
    fun assertNotEmpty(): TabCompletionScope = apply {
        check(completions.isNotEmpty()) { "Expected tab completions but got none" }
    }

    /** Assert completions list is empty. */
    fun assertEmpty(): TabCompletionScope = apply {
        check(completions.isEmpty()) { "Expected no tab completions but got: $completions" }
    }

    /** Assert completions count equals [n]. */
    fun assertSize(n: Int): TabCompletionScope = apply {
        check(completions.size == n) {
            "Expected $n tab completion(s) but got ${completions.size}: $completions"
        }
    }
}
