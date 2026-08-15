package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Api
suspend fun listGithub(ctx: ApiContext) {
    val githubToken = System.getenv("GITHUB_TOKEN")
    val repoOwner = "samging"
    val repoName = "codeRepository"
    val path = "uploads"
    val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

    val client = HttpClient.newHttpClient()
    val requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
    
    if (!githubToken.isNullOrBlank()) {
        requestBuilder.header("Authorization", "Bearer $githubToken")
    }

    try {
        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        if (response.statusCode() == 200) {
            ctx.res.setBodyText(response.body())
            ctx.res.contentType = "application/json"
        } else {
            ctx.res.setBodyText("GitHub API error: ${response.body()}")
        }
    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Internal Server Error: ${e.message}")
    }
}
