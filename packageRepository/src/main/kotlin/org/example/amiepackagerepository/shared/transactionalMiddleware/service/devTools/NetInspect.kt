package org.example.amiepackagerepository.shared.transactionalMiddleware.service.devTools

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.example.amiepackagerepository.shared.integration.github.dto.GithubContentResponseDto
import org.slf4j.LoggerFactory
import kotlin.jvm.java

/**
 * Utility class for inspecting network responses, specifically designed for debugging and verifying GitHub API interactions.
 *
 * This class provides a low-level diagnostic tool that uses `OkHttpClient` with an `HttpLoggingInterceptor`
 * to print detailed request and response information (including body content) to the console.
 * It is primarily intended for use during development to verify authentication headers, API versioning,
 * and the structure of JSON responses returned by GitHub.
 *
 * Features:
 * - Automatic injection of `GITHUB_TOKEN` from environment variables.
 * - Full HTTP request/response logging (Level.BODY).
 * - Basic JSON parsing to [GithubContentResponseDto] for structural validation.
 */
class NetInspect {
    companion object {
        private val logger = LoggerFactory.getLogger(NetInspect::class.java)
        private val netLogger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        private val githubToken = System.getenv("GITHUB_TOKEN")

        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(netLogger)
            .build()

        /**
         * Builds a pre-configured GitHub API request.
         * Sets mandatory headers including Authorization (Bearer token), Accept (vnd.github+json),
         * and the specific GitHub-Api-Version used by the application.
         *
         * @param url The full target GitHub API endpoint URL.
         * @return A [Request.Builder] populated with standard GitHub headers.
         */
        private fun builder(url: String): Request.Builder {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            return request.newBuilder()
        }

        /**
         * Executes a synchronous network call to the provided URL and logs the results.
         *
         * Side Effects:
         * - Prints the HTTP status code to stdout.
         * - If the request fails, prints the error response body to stdout.
         * - Logs exceptions to the class-level logger.
         *
         * @param url The full URL to inspect.
         */
        fun run(url: String) {
            try {
                val request = builder(url).build()
                val response = okHttpClient.newCall(request).execute()
                val responseBodyString = response.body?.string() ?: ""

                println("Status Code: ${response.code}")

                val githubContent = if (response.isSuccessful) {
                    ObjectMapper().readValue(responseBodyString, GithubContentResponseDto::class.java)
                } else {
                    println("Error Response Body: $responseBodyString")
                    null
                }
            } catch (e: Exception) {
                logger.error("Error: ${e.message}")
            }
        }
    }
}