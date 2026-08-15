package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.*
import java.util.Base64

@Api
suspend fun viewPackage(ctx: ApiContext) {
    val githubToken = System.getenv("GITHUB_TOKEN")
    val repoOwner = "samging"
    val repoName = "codeRepository"
    
    val path = ctx.req.params["package"] ?: ""
    val username = ctx.req.params["username"] ?: ""
    
    if (path.isEmpty() || username.isEmpty()) {
        ctx.res.status = 400
        ctx.res.setBodyText("Missing path or username")
        return
    }

    val encodedPath = path.split("/").joinToString("/") { 
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
    }
    val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"

    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $githubToken")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .GET()
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
            val json = Json.parseToJsonElement(response.body()).jsonObject
            val mutableJson = json.toMutableMap()
            
            // If content is null or missing (usually for files > 1MB), fetch from download_url
            if (json["content"] == null || json["content"]?.jsonPrimitive?.contentOrNull == null) {
                val downloadUrl = json["download_url"]?.jsonPrimitive?.contentOrNull
                if (downloadUrl != null) {
                    val rawRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("Authorization", "Bearer $githubToken")
                        .GET()
                        .build()
                    
                    val rawResponse = client.send(rawRequest, HttpResponse.BodyHandlers.ofByteArray())
                    if (rawResponse.statusCode() == 200) {
                        // Put the raw content into the 'content' field as Base64 to match frontend expectation
                        // or just send it as raw string if we update the frontend.
                        // Let's use Base64 to stay consistent with GitHub API structure.
                        val base64 = Base64.getEncoder().encodeToString(rawResponse.body())
                        mutableJson["content"] = JsonPrimitive(base64)
                        mutableJson["encoding"] = JsonPrimitive("base64")
                    }
                }
            }
            
            ctx.res.status = 200
            ctx.res.setBodyText(JsonObject(mutableJson).toString())
            ctx.res.contentType = "application/json"
        } else {
            ctx.res.status = response.statusCode()
            ctx.res.setBodyText(response.body())
            ctx.res.contentType = "application/json"
        }
    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("View Metadata Error: ${e.message}")
    }
}
