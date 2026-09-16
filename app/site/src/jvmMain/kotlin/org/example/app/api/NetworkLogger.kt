package org.example.app.api

import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Utility tool providing a logged Ktor HttpClient to pinpoint failures.
 */
object NetworkLogger {
    val client = HttpClient {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    /**
     * Prints a curl equivalent and executes the request.
     */
    suspend fun curl(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): String {
        var curlCmd = "curl -X $method '$url'"
        headers.forEach { (k, v) -> curlCmd += " -H '$k: $v'" }
        if (body != null) {
            // Escape single quotes for the shell command log
            val escapedBody = body.replace("'", "'\\''")
            curlCmd += " -d '$escapedBody'"
        }

        println("\n=== [CURL LOG] ===")
        println(curlCmd)
        println("==================\n")

        val response = client.request(url) {
            this.method = HttpMethod.parse(method)
            headers.forEach { (k, v) -> this.headers.append(k, v) }
            if (body != null) {
                setBody(body)
                if (!headers.containsKey("Content-Type")) {
                    contentType(ContentType.Application.Json)
                }
            }
        }
        return response.bodyAsText()
    }
}
