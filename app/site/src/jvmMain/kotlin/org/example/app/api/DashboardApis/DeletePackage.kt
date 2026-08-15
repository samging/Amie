package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Api
suspend fun deletePackage(ctx: ApiContext) {
    val githubToken = System.getenv("GITHUB_TOKEN")
    val repoOwner = "samging"
    val repoName = "codeRepository"
    val path = ctx.req.params["package"] ?: ""
    val username = ctx.req.params["username"] ?: ""
    val token = ctx.req.params["token"] ?: ""
    
    if (path.isEmpty() || username.isEmpty() || token.isEmpty()) {
        ctx.res.status = 400
        ctx.res.setBodyText("Missing path, username, or token")
        return
    }

    // 0. Validate Session
    val authUrl = "http://localhost:8080/dashboard"
    val authClient = HttpClient.newHttpClient()
    val authRequest = HttpRequest.newBuilder()
        .uri(URI.create(authUrl))
        .header("Authorization", "Bearer $token")
        .GET()
        .build()
    
    val authResponse = authClient.send(authRequest, HttpResponse.BodyHandlers.ofString())
    if (authResponse.statusCode() != 200) {
        ctx.res.status = 401
        ctx.res.setBodyText("Unauthorized session")
        return
    }

    val loggedInUser = authResponse.body().substringAfter("dashboard, ").substringBefore(" (ID:")
    if (loggedInUser != username) {
        ctx.res.status = 403
        ctx.res.setBodyText("Forbidden: You do not own this repository")
        return
    }

    val encodedPath = path.split("/").joinToString("/") { 
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
    }
    val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"

    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    try {
        // 1. Get the metadata to find the SHA
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $githubToken")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build()

        val getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        if (getResponse.statusCode() != 200) {
            ctx.res.status = getResponse.statusCode()
            ctx.res.setBodyText("Error finding file: ${getResponse.body()}")
            return
        }

        val shaMatch = "\"sha\":\"([a-f0-9]+)\"".toRegex().find(getResponse.body())
        val sha = shaMatch?.groupValues?.get(1) ?: throw RuntimeException("Could not extract SHA from GitHub response")

        // 2. Perform the DELETE
        val deleteBody = """{"message":"Delete file via Amie","sha":"$sha"}"""

        val deleteRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $githubToken")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(deleteBody))
            .build()

        val deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = deleteResponse.statusCode()
        ctx.res.setBodyText(deleteResponse.body())

    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Delete Error: ${e.message}")
    }
}
