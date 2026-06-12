@file:JvmName("ConfigWatchers")

package dev.arc.api.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches a config file on disk and calls [onReload] every time it changes.
 * The watcher runs on a dedicated daemon thread — [onReload] fires on the
 * Bukkit main thread via [org.bukkit.scheduler.BukkitScheduler.runTask].
 *
 * Obtain via [Plugin.watchConfig]; stop via [ConfigWatcher.stop].
 *
 * ```kotlin
 * var maxPlayers: Int = config.getInt("max-players", 20)
 *
 * val watcher = plugin.watchConfig("settings.yml") { newConfig ->
 *     maxPlayers = newConfig.getInt("max-players", 20)
 *     logger.info("Config reloaded — maxPlayers=$maxPlayers")
 * }
 *
 * // On disable:
 * watcher.stop()
 * ```
 */
class ConfigWatcher internal constructor(
    private val plugin: Plugin,
    private val file: File,
    private val onReload: (YamlConfiguration) -> Unit,
) {
    private val running = AtomicBoolean(true)
    private val thread: Thread

    init {
        val watchService = FileSystems.getDefault().newWatchService()
        val dir: Path = file.parentFile.toPath()
        dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

        thread = Thread({
            while (running.get()) {
                val key = try {
                    watchService.take()
                } catch (_: InterruptedException) { break }

                val changed = key.pollEvents().any { ev ->
                    ev.kind() != StandardWatchEventKinds.OVERFLOW &&
                        (ev.context() as? Path)?.fileName?.toString() == file.name
                }
                if (changed && running.get()) {
                    val cfg = YamlConfiguration.loadConfiguration(file)
                    plugin.server.scheduler.runTask(plugin, Runnable { onReload(cfg) })
                }
                if (!key.reset()) break
            }
            runCatching { watchService.close() }
        }, "arc-config-watcher-${file.name}")
        thread.isDaemon = true
        thread.start()
    }

    /** Stop watching and release the OS file-watch handle. */
    fun stop() {
        running.set(false)
        thread.interrupt()
    }
}

/**
 * Watch [fileName] in the plugin's data folder and invoke [onReload] on the main thread whenever
 * the file is saved/modified.
 *
 * The initial load is NOT triggered — call [Plugin.loadYaml] separately.
 *
 * ```kotlin
 * plugin.loadYaml("settings.yml").also { cfg ->
 *     applyConfig(cfg)
 *     plugin.watchConfig("settings.yml") { applyConfig(it) }
 * }
 * ```
 */
fun Plugin.watchConfig(
    fileName: String,
    onReload: (YamlConfiguration) -> Unit,
): ConfigWatcher {
    val file = File(dataFolder, fileName)
    if (!file.exists()) {
        dataFolder.mkdirs()
        if (getResource(fileName) != null) saveResource(fileName, false)
        else file.createNewFile()
    }
    return ConfigWatcher(this, file, onReload)
}
