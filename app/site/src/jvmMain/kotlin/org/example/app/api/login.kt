package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


@Api
suspend fun login(ctx: ApiContext) {
    try {
        val requestBody = ctx.req.body?.decodeToString() ?: "{}"

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        // This extracts everything including headers, status code, and body text via toString()
        val fullResponseDetails = """
            {
              "statusCode": ${response.statusCode()},
              "headers": "${response.headers().map()}",
              "body": ${response.body()}
              "requestBody": $requestBody
            }
        """.trimIndent()

        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(fullResponseDetails)
        ctx.res.contentType = "application/json"

    } catch (e: Exception) {
        ctx.logger.error("Failed to proxy login request")
        ctx.res.status = 500
        ctx.res.setBodyText("Server Error: ${e.javaClass.simpleName} - ${e.message}")
    }
}