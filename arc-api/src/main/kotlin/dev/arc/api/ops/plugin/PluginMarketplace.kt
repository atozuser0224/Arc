package dev.arc.api.ops.plugin

import dev.arc.api.ops.audit.AuditLog
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * `/arc plugin marketplace <search|install|info> [args]`
 *
 * Fetches plugins from Modrinth, runs a sandbox check before installing,
 * requires confirm token for CAUTION risk, blocks DANGEROUS installs outright.
 */
object PluginMarketplace {

    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private const val MODRINTH_API = "https://api.modrinth.com/v2"
    private const val USER_AGENT = "ArcMC/1.0 (arc-plugin-marketplace)"

    data class SearchHit(val slug: String, val title: String, val description: String, val downloads: Long, val latestVersion: String)
    data class VersionInfo(val versionNumber: String, val downloadUrl: String, val filename: String)

    fun complete(args: Array<out String>): List<String> = when (args.size) {
        3 -> listOf("search", "install", "info")
        else -> emptyList()
    }

    fun dispatch(sender: CommandSender, args: Array<out String>): Boolean {
        val action = args.getOrNull(2)?.lowercase()
        val query = args.getOrNull(3)
        return when (action) {
            "search" -> {
                if (query == null) { sender.sendMessage("/arc plugin marketplace search <query>"); return true }
                sender.sendMessage("[Arc] §7Searching Modrinth for \"$query\"…")
                dev.arc.api.ops.async.ArcAsync.runBlockingIO { search(query) }
                    .thenSync { hits ->
                        if (hits.isEmpty()) { sender.sendMessage("[Arc] §7No results for \"$query\""); return@thenSync }
                        sender.sendMessage("[Arc] §aSearch results (Modrinth):")
                        hits.forEach { h ->
                            sender.sendMessage("  §e${h.title} §7(${h.slug}) §a${h.latestVersion} §7— ${h.downloads} downloads")
                            sender.sendMessage("    §7${h.description.take(80)}")
                        }
                    }
                    .exceptionallySync { e -> sender.sendMessage("[Arc] §cSearch failed: ${e.message}") }
                true
            }
            "install" -> {
                if (query == null) { sender.sendMessage("/arc plugin marketplace install <slug>"); return true }
                sender.sendMessage("[Arc] §7Fetching $query from Modrinth…")
                dev.arc.api.ops.async.ArcAsync.runBlockingIO {
                    val version = fetchLatestVersion(query) ?: return@runBlockingIO null
                    val tempJar = File.createTempFile("arc-marketplace-", ".jar")
                    if (!downloadFile(version.downloadUrl, tempJar)) { tempJar.delete(); return@runBlockingIO null }
                    val report = PluginSandboxCheck.analyze(tempJar)
                    Triple(report, tempJar, version)
                }.thenSync { result ->
                    if (result == null) { sender.sendMessage("[Arc] §cFailed to download $query from Modrinth"); return@thenSync }
                    val (report, tempJar, version) = result
                    report.render().lineSequence().forEach { sender.sendMessage(it) }
                    when (report.riskLevel) {
                        PluginSandboxCheck.RiskLevel.DANGEROUS -> {
                            tempJar.delete()
                            sender.sendMessage("[Arc] §cInstallation blocked — DANGEROUS risk level.")
                        }
                        PluginSandboxCheck.RiskLevel.CAUTION, PluginSandboxCheck.RiskLevel.UNKNOWN -> {
                            val targetJar = File("plugins", version.filename)
                            val token = ConfirmManager.stage(sender, "install ${report.pluginName} v${version.versionNumber} (${report.riskLevel})") {
                                performInstall(sender, tempJar, targetJar, report.pluginName, version.versionNumber)
                            }
                            sender.sendMessage("§eType /arc plugin confirm $token to install anyway (${report.riskLevel})")
                        }
                        else -> {
                            val targetJar = File("plugins", version.filename)
                            performInstall(sender, tempJar, targetJar, report.pluginName, version.versionNumber)
                        }
                    }
                }.exceptionallySync { e -> sender.sendMessage("[Arc] §cInstall failed: ${e.message}") }
                true
            }
            "info" -> {
                if (query == null) { sender.sendMessage("/arc plugin marketplace info <slug>"); return true }
                sender.sendMessage("[Arc] §7Fetching info for $query…")
                dev.arc.api.ops.async.ArcAsync.runBlockingIO { fetchProjectInfo(query) }
                    .thenSync { info ->
                        if (info == null) { sender.sendMessage("[Arc] §cPlugin not found: $query"); return@thenSync }
                        sender.sendMessage("[Arc] §a${info["title"]} §7(${info["slug"]})")
                        sender.sendMessage("  §7${info["description"]}")
                        sender.sendMessage("  Downloads: §e${info["downloads"]}  §7License: ${info["license"]}")
                        sender.sendMessage("  Latest: §e${info["latestVersion"]}")
                    }
                    .exceptionallySync { e -> sender.sendMessage("[Arc] §cInfo failed: ${e.message}") }
                true
            }
            else -> { sender.sendMessage("/arc plugin marketplace <search|install|info> [query]"); true }
        }
    }

    private fun performInstall(sender: CommandSender, tempJar: File, targetJar: File, pluginName: String, version: String) {
        val existing = Bukkit.getPluginManager().getPlugin(pluginName)
        if (existing != null) PluginRollbackManager.capture(existing)

        tempJar.copyTo(targetJar, overwrite = true)
        tempJar.delete()

        if (existing != null) {
            val result = PluginLifecycle.restart(existing)
            sender.sendMessage(result.render())
        } else {
            val loaded = runCatching { Bukkit.getPluginManager().loadPlugin(targetJar) }.getOrNull()
            if (loaded != null) {
                Bukkit.getPluginManager().enablePlugin(loaded)
                sender.sendMessage("[Arc] §a${loaded.name} v${loaded.description.version} installed and enabled.")
            } else {
                sender.sendMessage("[Arc] §eJAR placed in plugins/${targetJar.name} — restart to load.")
            }
        }
        AuditLog.log(sender, "marketplace.install", "$pluginName v$version")
    }

    // ---- Modrinth API ----

    private fun search(query: String): List<SearchHit> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val facets = java.net.URLEncoder.encode("[[\"project_type:plugin\"]]", "UTF-8")
        val body = get("$MODRINTH_API/search?query=$encodedQuery&facets=$facets&limit=8") ?: return emptyList()
        return parseHits(body)
    }

    private fun fetchLatestVersion(slug: String): VersionInfo? {
        val loaders = java.net.URLEncoder.encode("""["paper","bukkit","spigot"]""", "UTF-8")
        val body = get("$MODRINTH_API/project/$slug/version?loaders=$loaders&limit=1") ?: return null
        return parseFirstVersion(body)
    }

    private fun fetchProjectInfo(slug: String): Map<String, String>? {
        val body = get("$MODRINTH_API/project/$slug") ?: return null
        return mapOf(
            "slug" to (jsonStr(body, "slug") ?: slug),
            "title" to (jsonStr(body, "title") ?: slug),
            "description" to (jsonStr(body, "description") ?: ""),
            "downloads" to (jsonNum(body, "downloads") ?: "?"),
            "license" to (jsonStr(body, "license") ?: "?"),
            "latestVersion" to (jsonStr(body, "latest_version") ?: "?"),
        )
    }

    private fun parseHits(json: String): List<SearchHit> {
        val hitsBlock = "\"hits\":\\s*\\[(.+?)\\]\\s*,\\s*\"offset\"".toRegex(RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1) ?: return emptyList()
        val objectPattern = "\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
        return objectPattern.findAll(hitsBlock).map { m ->
            val obj = m.value
            SearchHit(
                slug = jsonStr(obj, "slug") ?: return@map null,
                title = jsonStr(obj, "title") ?: jsonStr(obj, "slug") ?: "",
                description = (jsonStr(obj, "description") ?: "").take(80),
                downloads = jsonNum(obj, "downloads")?.toLongOrNull() ?: 0L,
                latestVersion = jsonStr(obj, "latest_version") ?: "?",
            )
        }.filterNotNull().toList()
    }

    private fun parseFirstVersion(json: String): VersionInfo? {
        // Response is a JSON array; grab first object
        val obj = "\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
            .find(json.removePrefix("["))?.value ?: return null
        val versionNumber = jsonStr(obj, "version_number") ?: return null
        // Find primary file or first file
        val filesBlock = "\"files\":\\s*\\[(.+?)\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
            .find(obj)?.groupValues?.get(1) ?: return null
        val firstFile = "\\{[^{}]+\\}".toRegex(RegexOption.DOT_MATCHES_ALL).find(filesBlock)?.value ?: return null
        val url = jsonStr(firstFile, "url") ?: return null
        val filename = jsonStr(firstFile, "filename") ?: url.substringAfterLast("/")
        return VersionInfo(versionNumber, url, filename)
    }

    private fun get(url: String): String? = runCatching {
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() in 200..299) resp.body() else null
    }.getOrNull()

    private fun downloadFile(url: String, target: File): Boolean = runCatching {
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .header("User-Agent", USER_AGENT)
            .GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofFile(target.toPath()))
        resp.statusCode() in 200..299
    }.getOrElse { false }

    private fun jsonStr(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex().find(json)?.groupValues?.get(1)

    private fun jsonNum(json: String, key: String): String? =
        "\"$key\"\\s*:\\s*([0-9]+)".toRegex().find(json)?.groupValues?.get(1)
}
