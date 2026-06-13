package dev.arc.api.control

import dev.arc.api.Arc
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

/**
 * File-backed YAML config for Arc. Backed by a `arc-config.yml` in the server
 * working directory. Changes are written back on [save] and read back on [reload].
 *
 * Changes applied through [Arc.settings] or [Arc.features] are flushed to the
 * file automatically when [save] is called (e.g. on `/arc reload`).
 *
 * ```
 * Arc.config.reload()                          // re-read from disk
 * Arc.config.save()                            // flush in-memory state back to disk
 * ```
 */
class ArcConfig internal constructor(private val file: File) {

    private var yaml: YamlConfiguration = YamlConfiguration()

    // ---- lifecycle ----------------------------------------------------------

    /**
     * Load (or create) the config file and apply all values to [Arc.settings]
     * and [Arc.features]. Safe to call multiple times — idempotent.
     */
    fun reload(log: Logger? = null) {
        if (!file.exists()) {
            writeDefaults()
            log?.info("[Arc] Generated default config at ${file.path}")
        }
        yaml = YamlConfiguration.loadConfiguration(file)
        applyToArc(log)
    }

    /** Flush the current in-memory Arc state back to the config file. */
    fun save() {
        syncFromArc()
        yaml.save(file)
    }

    /** Save values edited through [set] without overwriting them from runtime state first. */
    fun saveRaw() {
        yaml.save(file)
    }

    // ---- read helpers -------------------------------------------------------

    fun getString(path: String, default: String? = null): String? =
        yaml.getString(path, default)

    fun getInt(path: String, default: Int = 0): Int =
        yaml.getInt(path, default)

    fun getLong(path: String, default: Long = 0): Long =
        yaml.getLong(path, default)

    fun getBoolean(path: String, default: Boolean = true): Boolean =
        yaml.getBoolean(path, default)

    fun getDouble(path: String, default: Double = 0.0): Double =
        yaml.getDouble(path, default)

    fun getStringList(path: String): List<String> =
        yaml.getStringList(path)

    fun get(path: String): Any? =
        yaml.get(path)

    fun contains(path: String): Boolean =
        yaml.contains(path)

    fun defaultValue(path: String): Any? = when (path) {
        "settings.tick-budget-millis" -> 2L
        "settings.default-ttl-millis" -> 1000L
        "settings.default-pool-size" -> 64
        "settings.region-batch-concurrency" -> 2
        "settings.dispatcher-max-pending" -> 10_000
        "settings.nms-thread-policy" -> "STRICT"
        else -> if (path.startsWith("features.")) true else null
    }

    fun set(path: String, value: Any?) {
        yaml.set(path, value)
    }

    // ---- internals ----------------------------------------------------------

    private fun writeDefaults() {
        file.parentFile?.mkdirs()
        val cfg = YamlConfiguration()

        // Header comment written as a first entry
        cfg.options().setHeader(listOf(
            "Arc configuration  (auto-generated)",
            "Reload live with:  /arc reload",
            "Arc GitHub: https://github.com/atozuser0224/Arc",
        ))

        // settings
        cfg.set("settings.tick-budget-millis", 2L)
        cfg.set("settings.default-ttl-millis", 1000L)
        cfg.set("settings.default-pool-size", 64)
        cfg.set("settings.region-batch-concurrency", 2)
        cfg.set("settings.dispatcher-max-pending", 10_000)
        cfg.set("settings.nms-thread-policy", "STRICT")  // STRICT | WARN | UNSAFE

        // perf thresholds (ms per tick)
        cfg.set("perf.high-mspt-threshold", 45.0)
        cfg.set("perf.critical-mspt-threshold", 50.0)
        cfg.set("perf.low-mspt-threshold", 25.0)

        // features
        ArcFeatures.BUILTINS.forEach { id ->
            val key = "features.${id.replace(':', '.').replace('/', '-')}"
            cfg.set(key, true)
        }

        cfg.save(file)
    }

    private fun applyToArc(log: Logger?) {
        val s = Arc.settings

        yaml.getLong("settings.tick-budget-millis", -1).takeIf { it > 0 }?.let {
            s.tickBudgetMillis = it
        }
        yaml.getLong("settings.default-ttl-millis", -1).takeIf { it >= 0 }?.let {
            s.defaultTtlMillis = it
        }
        yaml.getInt("settings.default-pool-size", -1).takeIf { it > 0 }?.let {
            s.defaultPoolSize = it
        }
        yaml.getInt("settings.region-batch-concurrency", -1).takeIf { it > 0 }?.let {
            s.regionBatchConcurrency = it
        }
        yaml.getInt("settings.dispatcher-max-pending", -1).takeIf { it > 0 }?.let {
            s.dispatcherMaxPending = it
        }
        yaml.getString("settings.nms-thread-policy")?.let { raw ->
            runCatching { NmsThreadPolicy.valueOf(raw.uppercase()) }.getOrNull()?.let {
                s.nmsThreadPolicy = it
                log?.info("[Arc] nms-thread-policy = $it")
            } ?: log?.warning("[Arc] Unknown nms-thread-policy value '$raw', kept ${s.nmsThreadPolicy}")
        }

        // features
        val f = Arc.features
        ArcFeatures.BUILTINS.forEach { id ->
            val key = "features.${id.replace(':', '.').replace('/', '-')}"
            if (yaml.contains(key)) {
                val enabled = yaml.getBoolean(key, true)
                f.setEnabled(id, enabled)
            }
        }
    }

    private fun syncFromArc() {
        val s = Arc.settings
        yaml.set("settings.tick-budget-millis", s.tickBudgetMillis)
        yaml.set("settings.default-ttl-millis", s.defaultTtlMillis)
        yaml.set("settings.default-pool-size", s.defaultPoolSize)
        yaml.set("settings.region-batch-concurrency", s.regionBatchConcurrency)
        yaml.set("settings.dispatcher-max-pending", s.dispatcherMaxPending)
        yaml.set("settings.nms-thread-policy", s.nmsThreadPolicy.name)

        val f = Arc.features
        ArcFeatures.BUILTINS.forEach { id ->
            val key = "features.${id.replace(':', '.').replace('/', '-')}"
            yaml.set(key, f.isEnabled(id))
        }
    }
}
