package dev.arc.test.annotation

import dev.arc.test.ArcTestExtension
import org.junit.jupiter.api.extension.ExtendWith

/** DB backend for @ArcTest */
enum class DbMode { MEMORY, FILE }

/**
 * Marks a JUnit5 test class as an Arc plugin test.
 * Sets up a MockBukkit server + arc-api internals before each test
 * and tears everything down after.
 *
 * ```kotlin
 * @ArcTest
 * class MyPluginTest {
 *     @Test
 *     fun `coins deducted on purchase`() = arcTest {
 *         val player = withPlayer("Steve") { coins = 500 }
 *         player.performCommand("shop buy sword")
 *         tick(1)
 *         player.assertCoins(200)
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(ArcTestExtension::class)
annotation class ArcTest(
    val db: DbMode = DbMode.MEMORY,
    /** Start a real Redis container via Testcontainers for cross-server message bus tests. */
    val redis: Boolean = false,
)
