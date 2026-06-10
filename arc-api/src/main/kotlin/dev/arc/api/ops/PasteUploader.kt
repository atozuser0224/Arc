package dev.arc.api.ops

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Uploads a report to a public paste service for easy sharing (`--paste`).
 *
 * Uses mclo.gs (the standard Minecraft log paste service). This performs an
 * OUTBOUND network request and publishes the report publicly — only ever
 * triggered by an explicit `--paste` flag, never automatically. On any failure
 * it falls back to writing a local file and returns that path, so the operator
 * always gets *something* shareable.
 *
 * Must be called off the main thread (it blocks on HTTP).
 */
object PasteUploader {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    }

    /** Returns a public URL on success, or a `file://` path on fallback. */
    fun upload(content: String, fallbackDir: File): String {
        return runCatching { uploadToMclogs(content) }.getOrElse { saveLocal(content, fallbackDir) }
    }

    private fun uploadToMclogs(content: String): String {
        val form = "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mclo.gs/1/log"))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
        val body = resp.body()
        // crude JSON field extraction to avoid a json dependency
        val url = Regex(""""url"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1)
        return url?.replace("\\/", "/") ?: error("paste service returned no url: $body")
    }

    private fun saveLocal(content: String, dir: File): String {
        dir.mkdirs()
        val file = File(dir, "report-${System.currentTimeMillis()}.txt")
        file.writeText(content)
        return file.toURI().toString()
    }
}
