@file:JvmName("Databases")

package dev.arc.api.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

// ---------------------------------------------------------------------------
//  Column definitions
// ---------------------------------------------------------------------------

enum class ColumnType { TEXT, INTEGER, REAL, BLOB }

class Column<T>(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = false,
    val defaultValue: T? = null,
) {
    internal var primaryKey: Boolean = false
    internal var unique: Boolean = false
    internal var autoIncrement: Boolean = false

    fun primaryKey(): Column<T>    = apply { primaryKey = true }
    fun unique(): Column<T>        = apply { unique = true }
    fun autoIncrement(): Column<T> = apply { autoIncrement = true }
}

// ---------------------------------------------------------------------------
//  Table definition
// ---------------------------------------------------------------------------

/**
 * Define a table by extending this class and declaring columns.
 *
 * ```kotlin
 * object PlayersTable : Table("players") {
 *     val uuid   = text("uuid").primaryKey()
 *     val name   = text("name")
 *     val coins  = integer("coins").default(0)
 *     val level  = integer("level").default(1)
 * }
 * ```
 */
abstract class Table(val tableName: String) {
    internal val columns = mutableListOf<Column<*>>()

    protected fun text(name: String, nullable: Boolean = false): Column<String> =
        Column<String>(name, ColumnType.TEXT, nullable).also { columns += it }

    protected fun integer(name: String, nullable: Boolean = false): Column<Int> =
        Column<Int>(name, ColumnType.INTEGER, nullable).also { columns += it }

    protected fun long(name: String, nullable: Boolean = false): Column<Long> =
        Column<Long>(name, ColumnType.INTEGER, nullable).also { columns += it }

    protected fun real(name: String, nullable: Boolean = false): Column<Double> =
        Column<Double>(name, ColumnType.REAL, nullable).also { columns += it }

    protected fun bool(name: String, nullable: Boolean = false): Column<Boolean> =
        Column<Boolean>(name, ColumnType.INTEGER, nullable).also { columns += it }

    fun <T> Column<T>.default(v: T): Column<T> {
        @Suppress("UNCHECKED_CAST")
        val c = Column<T>(name, type, nullable, v).also {
            it.primaryKey = primaryKey; it.unique = unique; it.autoIncrement = autoIncrement
        }
        columns[columns.indexOf(this)] = c
        return c
    }

    internal fun createDdl(): String = buildString {
        append("CREATE TABLE IF NOT EXISTS $tableName (")
        columns.joinTo(this) { col ->
            buildString {
                append("${col.name} ${col.type.name}")
                if (col.primaryKey)    append(" PRIMARY KEY")
                if (col.autoIncrement) append(" AUTOINCREMENT")
                if (!col.nullable)     append(" NOT NULL")
                if (col.unique && !col.primaryKey) append(" UNIQUE")
                col.defaultValue?.let { append(" DEFAULT $it") }
            }
        }
        append(")")
    }
}

// ---------------------------------------------------------------------------
//  Row builder (INSERT / UPDATE)
// ---------------------------------------------------------------------------

class RowBuilder {
    internal val values = LinkedHashMap<String, Any?>()
    operator fun <T> set(col: Column<T>, value: T) { values[col.name] = value }
}

// ---------------------------------------------------------------------------
//  WHERE predicate
// ---------------------------------------------------------------------------

class WhereScope {
    internal val clauses = mutableListOf<Pair<String, Any?>>()
    infix fun <T> Column<T>.eq(value: T):    WhereScope { clauses += ("${this.name} = ?") to value;        return this@WhereScope }
    infix fun <T> Column<T>.neq(value: T):   WhereScope { clauses += ("${this.name} != ?") to value;       return this@WhereScope }
    infix fun <T> Column<T>.gt(value: T):    WhereScope { clauses += ("${this.name} > ?") to value;        return this@WhereScope }
    infix fun <T> Column<T>.lt(value: T):    WhereScope { clauses += ("${this.name} < ?") to value;        return this@WhereScope }
    fun <T> Column<T>.isNull():              WhereScope { clauses += ("${this.name} IS NULL") to null;     return this@WhereScope }
    fun <T> Column<T>.isNotNull():           WhereScope { clauses += ("${this.name} IS NOT NULL") to null; return this@WhereScope }
}

// ---------------------------------------------------------------------------
//  Query builders
// ---------------------------------------------------------------------------

class SelectQuery<T : Table>(private val db: ArcDatabase, val table: T) {
    private var where: WhereScope? = null
    private var limit: Int? = null
    private var orderBy: String? = null

    fun where(block: WhereScope.(T) -> Unit): SelectQuery<T> =
        apply { where = WhereScope().also { it.block(table) } }

    fun limit(n: Int): SelectQuery<T> = apply { limit = n }
    fun orderBy(col: Column<*>, desc: Boolean = false): SelectQuery<T> =
        apply { orderBy = "${col.name}${if (desc) " DESC" else ""}" }

    suspend fun list(): List<ResultRow<T>> = db.runQuery(table, where, limit, orderBy)
    suspend fun firstOrNull(): ResultRow<T>? = limit(1).list().firstOrNull()
    suspend fun count(): Long = db.runCount(table, where)
}

class UpdateQuery<T : Table>(private val db: ArcDatabase, val table: T) {
    private var where: WhereScope? = null
    private var setter: RowBuilder? = null

    fun where(block: WhereScope.(T) -> Unit): UpdateQuery<T> =
        apply { where = WhereScope().also { it.block(table) } }

    fun set(block: RowBuilder.(T) -> Unit): UpdateQuery<T> =
        apply { setter = RowBuilder().also { it.block(table) } }

    suspend fun execute(): Int = db.runUpdate(table, setter ?: RowBuilder(), where)
}

class DeleteQuery<T : Table>(private val db: ArcDatabase, val table: T) {
    private var where: WhereScope? = null

    fun where(block: WhereScope.(T) -> Unit): DeleteQuery<T> =
        apply { where = WhereScope().also { it.block(table) } }

    suspend fun execute(): Int = db.runDelete(table, where)
}

// ---------------------------------------------------------------------------
//  Result row
// ---------------------------------------------------------------------------

class ResultRow<T : Table>(private val data: Map<String, Any?>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <V> get(col: Column<V>): V? = data[col.name] as? V
}

// ---------------------------------------------------------------------------
//  Database
// ---------------------------------------------------------------------------

/**
 * Lightweight coroutine-friendly database wrapper.
 *
 * ```kotlin
 * val db = ArcDatabase.connect("jdbc:sqlite:plugins/myplugin/data.db")
 *
 * plugin.launch {
 *     db.createTable(PlayersTable)
 *
 *     db.insert(PlayersTable) {
 *         it[uuid]  = player.uniqueId.toString()
 *         it[coins] = 100
 *     }
 *
 *     val row = db.select(PlayersTable)
 *         .where { it[uuid] eq player.uniqueId.toString() }
 *         .firstOrNull()
 *     val coins = row?.get(PlayersTable.coins) ?: 0
 *
 *     db.update(PlayersTable)
 *         .where { it[uuid] eq player.uniqueId.toString() }
 *         .set   { it[coins] = coins + 10 }
 *         .execute()
 * }
 * ```
 */
class ArcDatabase private constructor(
    private val url: String,
    private val user: String?,
    private val password: String?,
) {
    companion object {
        fun connect(url: String, user: String? = null, password: String? = null): ArcDatabase =
            ArcDatabase(url, user, password)
    }

    private fun open(): Connection = if (user != null)
        DriverManager.getConnection(url, user, password)
    else
        DriverManager.getConnection(url)

    private suspend fun <T> io(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        open().use { block(it) }
    }

    // ---- DDL ----

    suspend fun createTable(table: Table) = io { conn ->
        conn.createStatement().execute(table.createDdl())
    }

    suspend fun createTables(vararg tables: Table) = tables.forEach { createTable(it) }

    // ---- INSERT ----

    suspend fun <T : Table> insert(table: T, block: RowBuilder.(T) -> Unit): Long = io { conn ->
        val row = RowBuilder().also { it.block(table) }
        val cols = row.values.keys.joinToString()
        val placeholders = row.values.keys.map { "?" }.joinToString()
        val stmt = conn.prepareStatement(
            "INSERT INTO ${table.tableName} ($cols) VALUES ($placeholders)",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        )
        row.values.values.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        stmt.executeUpdate()
        stmt.generatedKeys.use { it.takeIf { r -> r.next() }?.getLong(1) ?: -1L }
    }

    // ---- SELECT ----

    fun <T : Table> select(table: T): SelectQuery<T> = SelectQuery(this, table)

    internal suspend fun <T : Table> runQuery(
        table: T,
        where: WhereScope?,
        limit: Int?,
        orderBy: String?,
    ): List<ResultRow<T>> = io { conn ->
        val (whereClause, params) = buildWhere(where)
        val order = orderBy?.let { " ORDER BY $it" } ?: ""
        val lim   = limit?.let { " LIMIT $it" } ?: ""
        val sql = "SELECT * FROM ${table.tableName}$whereClause$order$lim"
        val stmt = conn.prepareStatement(sql)
        params.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        stmt.executeQuery().use { rs -> rs.toResultRows(table) }
    }

    internal suspend fun <T : Table> runCount(table: T, where: WhereScope?): Long = io { conn ->
        val (whereClause, params) = buildWhere(where)
        val stmt = conn.prepareStatement("SELECT COUNT(*) FROM ${table.tableName}$whereClause")
        params.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }

    // ---- UPDATE ----

    fun <T : Table> update(table: T): UpdateQuery<T> = UpdateQuery(this, table)

    internal suspend fun <T : Table> runUpdate(table: T, setter: RowBuilder, where: WhereScope?): Int = io { conn ->
        val setClauses = setter.values.keys.joinToString { "${it} = ?" }
        val (whereClause, whereParams) = buildWhere(where)
        val stmt = conn.prepareStatement("UPDATE ${table.tableName} SET $setClauses$whereClause")
        val setParams = setter.values.values.toList()
        (setParams + whereParams).forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        stmt.executeUpdate()
    }

    // ---- DELETE ----

    fun <T : Table> delete(table: T): DeleteQuery<T> = DeleteQuery(this, table)

    internal suspend fun <T : Table> runDelete(table: T, where: WhereScope?): Int = io { conn ->
        val (whereClause, params) = buildWhere(where)
        val stmt = conn.prepareStatement("DELETE FROM ${table.tableName}$whereClause")
        params.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        stmt.executeUpdate()
    }

    // ---- Raw ----

    suspend fun execute(sql: String, vararg params: Any?): Int = io { conn ->
        conn.prepareStatement(sql).also { stmt ->
            params.forEachIndexed { i, v -> stmt.setObject(i + 1, v) }
        }.executeUpdate()
    }

    // ---- Helpers ----

    private fun buildWhere(where: WhereScope?): Pair<String, List<Any?>> {
        if (where == null || where.clauses.isEmpty()) return "" to emptyList()
        val sql = " WHERE " + where.clauses.joinToString(" AND ") { it.first }
        val params = where.clauses.mapNotNull { it.second }
        return sql to params
    }

    private fun <T : Table> ResultSet.toResultRows(table: T): List<ResultRow<T>> {
        val meta = metaData
        val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }
        val rows = mutableListOf<ResultRow<T>>()
        while (next()) {
            val data = cols.associateWith { getObject(it) }
            rows += ResultRow(data)
        }
        return rows
    }
}
