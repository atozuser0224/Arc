@file:JvmName("ConfigUtils")

package dev.arc.api.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Ergonomic helpers over Bukkit's YAML configuration.
 *
 * The standard flow (copy a default resource into the data folder, load it, read typed values with string
 * paths) is verbose and stringly-typed. These helpers reduce it to a one-liner load and Kotlin property
 * delegation, so config keys read and write like ordinary fields:
 *
 * ```kotlin
 * val cfg = plugin.loadYaml("settings.yml")
 * var spawnRadius: Int by cfg.delegate("world.spawn-radius", default = 128)
 * spawnRadius += 16            // mutates the in-memory config
 * cfg.saveTo(plugin, "settings.yml")
 * ```
 */

/**
 * Load (or create) [fileName] from the plugin's data folder. If the plugin bundles a resource of that name
 * it is copied out on first run; otherwise an empty file is created. Always returns a usable config.
 */
public fun Plugin.loadYaml(fileName: String): YamlConfiguration {
    val file = File(dataFolder, fileName)
    if (!file.exists()) {
        dataFolder.mkdirs()
        if (getResource(fileName) != null) {
            saveResource(fileName, false)
        } else {
            file.createNewFile()
        }
    }
    return YamlConfiguration.loadConfiguration(file)
}

/** Save this config back to [fileName] in the plugin's data folder. */
public fun YamlConfiguration.saveTo(plugin: Plugin, fileName: String) {
    save(File(plugin.dataFolder, fileName))
}

/**
 * A read/write property delegate bound to [path] in this section, falling back to [default] when the path is
 * absent or the stored value is not of the expected type. Writes update the in-memory config (call a save
 * helper to persist).
 */
public fun <T : Any> ConfigurationSection.delegate(path: String, default: T): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
            (this@delegate.get(path) as? T) ?: default

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this@delegate.set(path, value)
        }
    }

/** Get a typed value, or [default] if absent/mismatched. */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> ConfigurationSection.getOr(path: String, default: T): T =
    (get(path) as? T) ?: default
