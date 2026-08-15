package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Api("get-device-status")
suspend fun getDeviceStatus(ctx: ApiContext) {
    val username = ctx.req.params["username"] ?: ""
    if (username.isEmpty()) {
        ctx.res.status = 400
        ctx.res.setBodyText("Missing 'username' parameter")
        return
    }

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/device-status?username=$username"))
        .GET()
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(response.body())
        ctx.res.contentType = "application/json"
    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Proxy Error: ${e.message}")
    }
}

@Api("save-device-status")
suspend fun saveDeviceStatus(ctx: ApiContext) {
    val username = ctx.req.params["username"] ?: ""
    val bodyText = ctx.req.body?.decodeToString() ?: "{}"

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/device-status?username=$username"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(bodyText))
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(response.body())
    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Proxy Error: ${e.message}")
    }
}
