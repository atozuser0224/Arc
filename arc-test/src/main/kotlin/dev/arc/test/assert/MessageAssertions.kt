package dev.arc.test.assert

/** Common message assertion interface implemented by [VirtualPlayer][dev.arc.test.virtual.VirtualPlayer]. */
interface MessageAssertions {
    fun assertMessage(text: String): Any
    fun assertNoMessages(): Any
}
