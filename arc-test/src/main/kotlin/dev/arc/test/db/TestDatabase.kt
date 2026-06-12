package dev.arc.test.db

import dev.arc.api.db.ArcDatabase
import dev.arc.api.db.Table

/**
 * Factory for in-memory SQLite databases suitable for isolated unit tests.
 *
 * Each call to [create] returns a fresh database — no state is shared between tests.
 *
 * ```kotlin
 * @ArcTest
 * class ShopDaoTest {
 *     @InjectDb
 *     lateinit var db: ArcDatabase
 *
 *     @Test
 *     fun `insert and query`() = arcTest {
 *         db.createTable(PlayersTable)
 *         db.insert(PlayersTable) { it[uuid] = "x"; it[coins] = 100 }
 *         val row = db.select(PlayersTable).firstOrNull()
 *         assertNotNull(row)
 *     }
 * }
 * ```
 */
object TestDatabase {
    /** Create a fresh anonymous in-memory SQLite database. */
    fun create(): ArcDatabase = ArcDatabase.connect("jdbc:sqlite::memory:")

    /**
     * Create a database, create the given tables, and return it.
     * Convenience for tests that need a ready-to-use schema.
     */
    suspend fun withTables(vararg tables: Table): ArcDatabase =
        create().also { db -> db.createTables(*tables) }
}

/**
 * JUnit5 parameter annotation — injects a fresh [ArcDatabase] per test method.
 *
 * Wired automatically when the test class is annotated with
 * [@ArcTest][dev.arc.test.annotation.ArcTest].
 *
 * The injected database uses `jdbc:sqlite::memory:` and is closed after each test.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectDb
