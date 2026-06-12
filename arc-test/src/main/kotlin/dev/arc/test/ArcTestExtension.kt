package dev.arc.test

import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

private val NAMESPACE = ExtensionContext.Namespace.create("dev.arc.test")
internal const val KEY_SERVER = "server"

/**
 * JUnit5 extension wired in by [@ArcTest][dev.arc.test.annotation.ArcTest].
 * Bootstraps MockBukkit before each test and cleans up after.
 */
class ArcTestExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val server: ServerMock = MockBukkit.mock()
        context.getStore(NAMESPACE).put(KEY_SERVER, server)
    }

    override fun afterEach(context: ExtensionContext) {
        runCatching { MockBukkit.unmock() }
    }
}
