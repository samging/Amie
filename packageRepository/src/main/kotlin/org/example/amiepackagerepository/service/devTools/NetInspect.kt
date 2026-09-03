package org.example.amiepackagerepository.service.devTools

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.example.amiepackagerepository.controllers.integrations.github.dto.GithubContentResponseDto
import org.slf4j.LoggerFactory
import kotlin.jvm.java

class NetInspect {
    companion object {
        private val logger = LoggerFactory.getLogger(NetInspect::class.java)
        private val netLogger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        private val githubToken = System.getenv("GITHUB_TOKEN")

        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(netLogger)
            .build()

        private fun builder(url: String): Request.Builder {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            return request.newBuilder()
        }

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