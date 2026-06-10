@file:JvmName("Sql")

package dev.arc.api.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Minimal coroutine JDBC helpers: open a connection and run blocking SQL on [Dispatchers.IO] (never the tick
 * thread), with the connection auto-closed. No external pool dependency - bring your own driver on the
 * classpath.
 *
 * ```kotlin
 * plugin.launch {
 *     val rows = sqlConnection("jdbc:sqlite:data.db") { conn ->
 *         conn.query("SELECT name, coins FROM players WHERE uuid = ?", uuid.toString())
 *     }
 *     player.sendMessage("coins: ${rows.firstOrNull()?.get("coins")}")
 * }
 * ```
 */
public suspend fun <T> sqlConnection(
    url: String,
    user: String? = null,
    password: String? = null,
    block: (Connection) -> T,
): T = withContext(Dispatchers.IO) {
    val connection = if (user != null) DriverManager.getConnection(url, user, password) else DriverManager.getConnection(url)
    connection.use(block)
}

/** Run a parameterised query, returning rows as ordered column → value maps. */
public fun Connection.query(sql: String, vararg params: Any?): List<Map<String, Any?>> {
    prepareStatement(sql).use { statement ->
        params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { resultSet -> return resultSet.toRows() }
    }
}

/** Run a parameterised update/insert/DDL, returning the affected row count. */
public fun Connection.update(sql: String, vararg params: Any?): Int {
    prepareStatement(sql).use { statement ->
        params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        return statement.executeUpdate()
    }
}

private fun ResultSet.toRows(): List<Map<String, Any?>> {
    val meta = metaData
    val columns = meta.columnCount
    val rows = ArrayList<Map<String, Any?>>()
    while (next()) {
        val row = LinkedHashMap<String, Any?>(columns)
        for (i in 1..columns) row[meta.getColumnLabel(i)] = getObject(i)
        rows.add(row)
    }
    return rows
}
