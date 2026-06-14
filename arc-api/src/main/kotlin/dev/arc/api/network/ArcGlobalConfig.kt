package dev.arc.api.network

/**
 * Network-wide key/value config store backed by Redis hash arc:globalconfig.
 * All servers read from the same hash — changes propagate instantly.
 * Use for runtime toggles and cross-server settings that don't belong in yml files.
 */
object ArcGlobalConfig {

    private const val KEY = "arc:globalconfig"

    fun get(field: String): String? = ArcRelayClient.hget(KEY, field)

    fun set(field: String, value: String) = ArcRelayClient.hset(KEY, field, value)

    fun del(field: String) = ArcRelayClient.hdel(KEY, field)

    fun getAll(): Map<String, String> = ArcRelayClient.hgetAll(KEY)

    // ---- Typed helpers ----

    fun getInt(field: String, default: Int = 0): Int = get(field)?.toIntOrNull() ?: default

    fun getLong(field: String, default: Long = 0L): Long = get(field)?.toLongOrNull() ?: default

    fun getDouble(field: String, default: Double = 0.0): Double = get(field)?.toDoubleOrNull() ?: default

    fun getBoolean(field: String, default: Boolean = false): Boolean =
        when (get(field)?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> default
        }
}
