package dev.arc.api.ops.plugin

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `/arc plugin rollback <plugin> [--list] [--restore <id>]`
 *
 * Before reloading or enabling a plugin, captures its current JAR + config to
 * a backup directory. If the new version fails, the operator can roll back.
 *
 * Backup dir: `arc-ops/plugin-rollbacks/<plugin-name>/`
 * Each backup: `YYYYMMDD_HHmmss_<version>.jar` + `config.snapshot`
 */
object PluginRollbackManager {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    private fun rollbackDir() = File("arc-ops/plugin-rollbacks").also { it.mkdirs() }

    data class RollbackEntry(
        val id: String,
        val timestamp: Long,
        val version: String,
        val jarName: String,
    )

    /** Capture the current state of a plugin before a risky operation. */
    fun capture(plugin: Plugin): String? {
        val dir = File(rollbackDir(), plugin.name).also { it.mkdirs() }
        val id = tsFmt.format(Instant.now())
        val version = plugin.description.version ?: "unknown"

        // Copy JAR
        val jarFile = pluginFile(plugin) ?: return null
        val backupJar = File(dir, "${id}_${version}.jar")
        jarFile.copyTo(backupJar)

        // Save config snapshot
        val configDir = plugin.dataFolder
        if (configDir.isDirectory) {
            val snapshot = File(dir, "${id}_config.zip")
            zipDirectory(configDir, snapshot)
        }

        // Write metadata
        File(dir, "${id}.meta").writeText("""
            id=$id
            timestamp=${System.currentTimeMillis()}
            version=$version
            jar=${backupJar.name}
            originalJar=${jarFile.name}
        """.trimIndent())

        return id
    }

    /** List available rollbacks for a plugin. */
    fun list(pluginName: String): List<RollbackEntry> {
        val dir = File(rollbackDir(), pluginName)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "meta" }?.mapNotNull { f ->
            val lines = f.readLines().associate { it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }
            RollbackEntry(
                id = lines["id"] ?: f.nameWithoutExtension,
                timestamp = lines["timestamp"]?.toLongOrNull() ?: 0L,
                version = lines["version"] ?: "?",
                jarName = lines["jar"] ?: "?"
            )
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    /** Restore plugin to a specific rollback point. Restores JAR, keeps current config. */
    fun restore(pluginName: String, rollbackId: String): Boolean {
        val dir = File(rollbackDir(), pluginName)
        val meta = File(dir, "${rollbackId}.meta")
        if (!meta.isFile) return false

        val lines = meta.readLines().associate { it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }
        val jarName = lines["jar"] ?: return false
        val backupJar = File(dir, jarName)
        if (!backupJar.isFile) return false

        // Copy JAR back to plugins/
        val targetName = lines["originalJar"] ?: return false
        val targetJar = File("plugins", targetName)
        backupJar.copyTo(targetJar, overwrite = true)
        return true
    }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("rollback")
        3 -> when (args.getOrNull(1)?.lowercase()) {
            "rollback" -> Bukkit.getPluginManager().plugins.map { it.name }
            else -> emptyList()
        }
        4 -> when (args.getOrNull(1)?.lowercase()) {
            "rollback" -> listOf("--list", "--restore")
            else -> emptyList()
        }
        5 -> {
            val pluginName = args.getOrNull(2)
            if (pluginName == null || args.getOrNull(3) != "--restore") {
                emptyList()
            } else {
                list(pluginName).map { it.id }
            }
        }
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase()
        if (sub != "rollback") { return false }

        val pluginName = args.getOrNull(2)
        if (pluginName == null) { sender.sendMessage("/arc plugin rollback <plugin> [--list|--restore <id>]"); return true }
        val action = args.getOrNull(3)?.lowercase()

        when (action) {
            "--list" -> {
                val entries = list(pluginName)
                if (entries.isEmpty()) { sender.sendMessage("[Arc] no rollbacks for §e$pluginName"); return true }
                sender.sendMessage("[Arc] Rollbacks for §e$pluginName:")
                entries.forEach { e ->
                    val ts = tsFmt.format(Instant.ofEpochMilli(e.timestamp))
                    sender.sendMessage("  §e${e.id} §7$ts v${e.version}")
                }
            }
            "--restore" -> {
                val id = args.getOrNull(4) ?: run { sender.sendMessage("[Arc] specify a rollback id"); return true }
                val ok = restore(pluginName, id)
                if (!ok) {
                    sender.sendMessage("[Arc] §cRollback not found or corrupted: $id")
                    return true
                }
                sender.sendMessage("[Arc] §aJAR restored to $id. Attempting hot-reload…")
                val plugin = Bukkit.getPluginManager().getPlugin(pluginName)
                if (plugin != null) {
                    val result = PluginLifecycle.restart(plugin)
                    sender.sendMessage(result.render())
                } else {
                    sender.sendMessage("[Arc] §7Plugin not currently loaded — restart server to apply.")
                }
            }
            else -> {
                val plugin = Bukkit.getPluginManager().getPlugin(pluginName)
                if (plugin == null) { sender.sendMessage("[Arc] plugin not loaded: $pluginName"); return true }
                val id = capture(plugin)
                sender.sendMessage(if (id != null) "[Arc] §aSnapshot $id captured for ${plugin.name} before update."
                else "[Arc] §cFailed to capture snapshot")
            }
        }
        return true
    }

    private fun pluginFile(plugin: Plugin): File? = runCatching {
        val m = plugin.javaClass.getDeclaredMethod("getFile").apply { isAccessible = true }
        m.invoke(plugin) as? File
    }.getOrNull()

    private fun zipDirectory(source: File, target: File) {
        runCatching {
            java.util.zip.ZipOutputStream(target.outputStream()).use { zos ->
                source.walkTopDown().filter { it.isFile }.forEach { f ->
                    val entry = java.util.zip.ZipEntry(f.relativeTo(source).path)
                    zos.putNextEntry(entry)
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}
