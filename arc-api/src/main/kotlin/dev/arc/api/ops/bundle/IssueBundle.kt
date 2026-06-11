package dev.arc.api.ops.bundle

import dev.arc.api.ops.doctor.ServerDoctor
import dev.arc.api.ops.lagspike.LagSpikeMonitor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `/arc issue-bundle` — gathers diagnostic data into a zip file for bug reports.
 *
 * Includes:
 * - Doctor report
 * - arc-config.yml, arc-ops.yml (masked)
 * - Latest crash report
 * - Latest lag spike report
 * - Plugin list with versions
 * - server.properties (masked)
 * - Recent log tail (masked)
 * - JVM flags
 *
 * Output: `arc-ops/issue-bundles/bundle_YYYYMMDD_HHmmss.zip`
 */
object IssueBundle {

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMDD_HHmmss").withZone(ZoneId.systemDefault())
    private fun bundleDir() = File("arc-ops/issue-bundles").also { it.mkdirs() }

    fun create(monitor: LagSpikeMonitor?): File {
        val id = tsFmt.format(Instant.now())
        val zipFile = File(bundleDir(), "bundle_$id.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->

            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }

            fun addFile(name: String, file: File) {
                if (!file.isFile) return
                zos.putNextEntry(ZipEntry(name))
                zos.write(maskSensitive(file.readText()).toByteArray())
                zos.closeEntry()
            }

            // Doctor report
            addEntry("doctor-report.txt", ServerDoctor.report(worldFilter = null, monitor = monitor))

            // Configs
            addFile("arc-config.yml", File("arc-config.yml"))
            addFile("arc-ops.yml", File("arc-ops/arc-ops.yml"))
            addFile("server.properties", File("server.properties"))
            addFile("bukkit.yml", File("bukkit.yml"))
            addFile("spigot.yml", File("spigot.yml"))

            // Plugin list
            addEntry("plugins.txt", buildString {
                appendLine("Plugins (${Bukkit.getPluginManager().plugins.size}):")
                Bukkit.getPluginManager().plugins.sortedBy { it.name }.forEach { p ->
                    appendLine("  ${p.name} v${p.description.version} ${if(p.isEnabled) "ENABLED" else "DISABLED"} api=${p.description.apiVersion ?: "legacy"}")
                }
            })

            // Crash reports
            val crashDir = File(Bukkit.getWorldContainer(), "crash-reports")
            if (crashDir.isDirectory) {
                crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull()?.let {
                    addFile("crash-report.txt", it)
                }
            }

            // Lag spike
            val lagSpikeDir = File("arc-ops/lag-spikes")
            if (lagSpikeDir.isDirectory) {
                lagSpikeDir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull()?.let {
                    addFile("latest-lagspike.txt", it)
                }
            }

            // Recent log
            val logFile = File(Bukkit.getWorldContainer(), "logs/latest.log")
            if (logFile.isFile) {
                val lines = logFile.readLines().takeLast(500)
                addEntry("recent-log.txt", maskSensitive(lines.joinToString("\n")))
            }

            // JVM info
            addEntry("jvm-info.txt", buildString {
                appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
                appendLine("Arch: ${System.getProperty("os.arch")}")
                appendLine("CPUs: ${Runtime.getRuntime().availableProcessors()}")
                val rt = Runtime.getRuntime()
                appendLine("Max Memory: ${rt.maxMemory() / (1024*1024)} MB")
                val flags = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
                    .filter { it.startsWith("-X") || it.startsWith("-XX") }
                appendLine("JVM Flags: ${flags.joinToString(" ")}")
            })

            // Arc version
            addEntry("arc-version.txt", buildString {
                val pkg = dev.arc.api.Arc::class.java.`package`
                appendLine("Arc: ${pkg?.implementationVersion ?: "dev"}")
                appendLine("MC: ${Bukkit.getMinecraftVersion()}")
                appendLine("Bukkit: ${Bukkit.getBukkitVersion()}")
            })
        }

        return zipFile
    }

    private fun maskSensitive(text: String): String {
        return text
            .replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "***.***.***.***")
            .replace(Regex("(?i)(password|secret|token|key|webhook)[:=]\\s*\\S+"), "$1=***MASKED***")
            .replace(Regex("(?i)rcon\\.password=\\S+"), "rcon.password=***MASKED***")
            .replace(Regex("jdbc:[a-z]+://[^\\s]+"), "jdbc:***MASKED***")
    }

    fun dispatch(sender: CommandSender, args: Array<out String>, monitor: LagSpikeMonitor?): Boolean {
        sender.sendMessage("[Arc] Gathering issue bundle…")
        runCatching {
            val file = create(monitor)
            sender.sendMessage("[Arc] §aBundle saved: ${file.absolutePath}")
            sender.sendMessage("[Arc] §7Size: ${file.length() / 1024} KB")
            sender.sendMessage("[Arc] §7Send this file with your bug report (sensitive data is masked)")
        }.onFailure {
            sender.sendMessage("[Arc] §cFailed to create bundle: ${it.message}")
        }
        return true
    }
}
