package dev.arc.api.ops.plugin

import java.io.File
import java.io.InputStream
import java.util.jar.JarFile

/**
 * Offline analysis of a plugin JAR BEFORE loading it.
 * `/arc sandbox check <jar-file>` — scans for risk factors without activating the plugin.
 */
object PluginSandboxCheck {

    data class SandboxReport(
        val pluginName: String,
        val version: String,
        val apiVersion: String?,
        val mainClass: String,
        val riskLevel: RiskLevel,
        val findings: List<Finding>,
    ) {
        fun render(): String = buildString {
            appendLine("===== Arc Sandbox: $pluginName v$version =====")
            appendLine("main class  : $mainClass")
            appendLine("api-version : ${apiVersion ?: "none (legacy)"}")
            appendLine("risk level  : $riskLevel")
            findings.groupBy { it.severity }.forEach { (sev, items) ->
                val tag = when (sev) { FindingSeverity.DANGER -> "§c[!]"; FindingSeverity.WARN -> "§e[~]"; FindingSeverity.INFO -> "§7[i]" }
                items.forEach { appendLine("$tag ${it.message}") }
            }
            if (findings.isEmpty()) appendLine("§aNo issues detected from static analysis")
        }
    }

    enum class RiskLevel { SAFE, CAUTION, DANGEROUS, UNKNOWN }
    enum class FindingSeverity { INFO, WARN, DANGER }

    data class Finding(val severity: FindingSeverity, val message: String)

    private data class MinimalDesc(
        val name: String,
        val version: String,
        val main: String,
        val apiVersion: String?,
        val depend: List<String>,
    )

    fun analyze(jarFile: File): SandboxReport {
        val findings = mutableListOf<Finding>()
        val jar = JarFile(jarFile)
        val pluginYml = jar.getJarEntry("plugin.yml") ?: jar.getJarEntry("paper-plugin.yml")
        val desc = if (pluginYml != null) parseDescription(jar.getInputStream(pluginYml)) else null

        if (desc == null) {
            return SandboxReport(jarFile.name, "?", null, "?", RiskLevel.UNKNOWN,
                listOf(Finding(FindingSeverity.DANGER, "No plugin.yml or paper-plugin.yml found")))
        }

        // 1. Check NMS/reflection usage
        val nmsRefs = scanForPatterns(jar, listOf(
            "net.minecraft" to "Direct NMS access",
            "org.bukkit.craftbukkit" to "CraftBukkit internals",
            "sun.misc.Unsafe" to "Unsafe usage",
        ))
        nmsRefs.forEach { (msg, found) ->
            if (found) findings += Finding(FindingSeverity.WARN, msg)
        }

        // 2. Check for native libraries
        if (jar.entries().asSequence().any { it.name.endsWith(".so") || it.name.endsWith(".dll") || it.name.endsWith(".dylib") }) {
            findings += Finding(FindingSeverity.DANGER, "Contains native libraries — reload unsafe")
        }

        // 3. Check api-version
        val apiVer = desc.apiVersion
        if (apiVer == null) {
            findings += Finding(FindingSeverity.WARN, "No api-version declared — may use legacy internals")
        } else if (apiVer < "1.20") {
            findings += Finding(FindingSeverity.INFO, "Old api-version ($apiVer) — some features may be deprecated")
        }

        // 4. Check for thread/executor usage
        val threadPatterns = listOf(
            "java/util/concurrent/ThreadPoolExecutor" to "Thread pool usage",
            "java/lang/Thread" to "Custom thread creation",
            "kotlinx/coroutines" to "Kotlin coroutines",
        )
        threadPatterns.forEach { (cls, msg) ->
            if (jar.getJarEntry(cls.replace('.', '/') + ".class") != null || hasClassReference(jar, cls)) {
                findings += Finding(FindingSeverity.WARN, "$msg detected — may not clean up on reload")
            }
        }

        // 5. Check dependencies in jar (shaded libs)
        val shadedLibs = jar.entries().asSequence()
            .filter { it.name.startsWith("META-INF/maven/") || it.name.startsWith("META-INF/versions/") }
            .count()
        if (shadedLibs > 10) findings += Finding(FindingSeverity.INFO, "Contains shaded libraries — may increase classloader complexity")

        // 6. Check for service registrations
        if (desc.depend.any { it.equals("Vault", true) }) {
            findings += Finding(FindingSeverity.INFO, "Depends on Vault — service provider risk on reload")
        }

        // Determine risk level
        val hasDanger = findings.any { it.severity == FindingSeverity.DANGER }
        val hasWarn = findings.any { it.severity == FindingSeverity.WARN }
        val risk = when {
            hasDanger -> RiskLevel.DANGEROUS
            hasWarn -> RiskLevel.CAUTION
            findings.isEmpty() -> RiskLevel.SAFE
            else -> RiskLevel.CAUTION
        }

        jar.close()
        return SandboxReport(desc.name, desc.version, apiVer, desc.main, risk, findings)
    }

    private fun scanForPatterns(jar: JarFile, patterns: List<Pair<String, String>>): List<Pair<String, Boolean>> {
        return patterns.map { (pkg, msg) ->
            val path = pkg.replace('.', '/')
            val found = jar.entries().asSequence().any { it.name.contains(path) }
            msg to found
        }
    }

    private fun hasClassReference(jar: JarFile, className: String): Boolean {
        val path = className.replace('.', '/')
        return jar.entries().asSequence().any { it.name.contains(path) }
    }

    private fun parseDescription(input: InputStream): MinimalDesc? {
        return runCatching {
            val yaml = org.yaml.snakeyaml.Yaml()
            val data = yaml.load<Map<String, Any>>(input)
            MinimalDesc(
                name = data["name"] as? String ?: "unknown",
                version = data["version"] as? String ?: "?",
                main = data["main"] as? String ?: "",
                apiVersion = data["api-version"] as? String,
                depend = (data["depend"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }.getOrNull()
    }

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        2 -> listOf("check")
        3 -> File("plugins").listFiles { f -> f.extension == "jar" }?.map { it.name }?.toList() ?: emptyList()
        else -> emptyList()
    }

    fun dispatch(sender: org.bukkit.command.CommandSender, args: Array<out String>): Boolean {
        val sub = args.getOrNull(1)?.lowercase()
        if (sub != "check") { sender.sendMessage("/arc sandbox check <jar-file>"); return true }
        val jarName = args.getOrNull(2)
        if (jarName == null) { sender.sendMessage("[Arc] specify a jar file name in plugins/"); return true }
        val jarFile = File("plugins", jarName)
        if (!jarFile.isFile) { sender.sendMessage("[Arc] file not found: plugins/$jarName"); return true }
        val report = analyze(jarFile)
        report.render().lineSequence().forEach { sender.sendMessage(it) }
        return true
    }
}
