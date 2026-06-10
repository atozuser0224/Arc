@file:JvmName("Http")

package dev.arc.api.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val client: HttpClient by lazy {
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
}

/** HTTP response with status code and body string. */
public data class HttpResult(val status: Int, val body: String) {
    public val isSuccess: Boolean get() = status in 200..299
}

/** Perform an async GET request. Runs on [Dispatchers.IO]. */
public suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
    withContext(Dispatchers.IO) {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        HttpResult(resp.statusCode(), resp.body())
    }

/** Perform an async POST request with a JSON body. Runs on [Dispatchers.IO]. */
public suspend fun httpPost(
    url: String,
    body: String,
    contentType: String = "application/json",
    headers: Map<String, String> = emptyMap(),
): HttpResult = withContext(Dispatchers.IO) {
    val builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(body))
    headers.forEach { (k, v) -> builder.header(k, v) }
    val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    HttpResult(resp.statusCode(), resp.body())
}

/** Send a Discord webhook message asynchronously. */
public suspend fun discordWebhook(url: String, content: String): HttpResult =
    httpPost(url, """{"content":${content.asJsonString()}}""")

private fun String.asJsonString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
