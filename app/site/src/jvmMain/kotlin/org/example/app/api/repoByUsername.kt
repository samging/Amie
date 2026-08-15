package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.File

@Api
suspend fun repoByUsername(ctx: ApiContext) {
    val username = ctx.req.params["username"] ?: ""
    if (username.isEmpty()) {
        ctx.res.status = 400
        ctx.res.setBodyText("Missing 'username' parameter")
        return
    }

    // Call Spring Boot backend for recursive listing
    val url = "http://localhost:8080/user-packages?username=$username"
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(response.body() ?: "[]")
        ctx.res.contentType = "application/json"
    } catch (e: Exception) {
        println("ERROR proxying user-packages: ${e.message}")
        ctx.res.status = 500
        ctx.res.setBodyText("[]")
        ctx.res.contentType = "application/json"
    }
}
