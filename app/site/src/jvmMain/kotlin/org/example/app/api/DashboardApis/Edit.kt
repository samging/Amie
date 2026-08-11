package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.Body
import com.varabyte.kobweb.api.http.text
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Api
suspend fun dashboarEdit(ctx: ApiContext) {
    try {
        val requestBody = ctx.req.body?.text() ?: ""
        
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/edit"))
            .header("Content-Type", "multipart/form-data")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        ctx.res.status = response.statusCode()
        ctx.res.body = Body.text(response.body())
        
    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.body = Body.text("Internal Server Error: ${e.message}")
    }
}
