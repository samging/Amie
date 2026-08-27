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
    println("--- [API: viewPackage] START ---")
    try {
        val githubToken = (System.getenv("GITHUB_TOKEN") ?: System.getProperty("GITHUB_TOKEN"))?.trim()
        val repoOwner = "samging"
        val repoName = "codeRepository"
        
        val path = ctx.req.params["package"] ?: ""
        val username = ctx.req.params["username"] ?: ""
        
        println("DEBUG: Incoming Params - path: '$path', username: '$username'")
        println("DEBUG: GITHUB_TOKEN presence: ${!githubToken.isNullOrBlank()}")

        if (path.isEmpty()) {
            println("ERROR: Missing path parameter")
            ctx.res.status = 400
            ctx.res.setBodyText("{\"message\": \"Missing path\"}")
            return
        }

        // Properly encode each path segment to handle special characters
        val encodedPath = path.split("/").joinToString("/") { 
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
        }
        
        val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"
        println("DEBUG: Calling GitHub API URL: $url")

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()

        if (!githubToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $githubToken")
        }

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        println("DEBUG: GitHub API Status Code: ${response.statusCode()}")
        
        if (response.statusCode() == 200) {
            val responseBody = response.body()
            println("DEBUG: GitHub API response received (Length: ${responseBody.length})")
            val jsonElement = Json.parseToJsonElement(responseBody)
            
            if (jsonElement is JsonArray) {
                println("DEBUG: Response is a directory listing")
                ctx.res.status = 200
                ctx.res.setBodyText(responseBody)
                ctx.res.contentType = "application/json"
                return
            }

            val json = jsonElement.jsonObject
            val mutableJson = json.toMutableMap()
            
            // If content is null or missing (usually for files > 1MB), fetch from download_url
            val contentValue = json["content"]?.jsonPrimitive?.contentOrNull
            if (contentValue.isNullOrBlank()) {
                println("DEBUG: Content field is null/blank, checking download_url")
                val downloadUrl = json["download_url"]?.jsonPrimitive?.contentOrNull
                if (downloadUrl != null) {
                    println("DEBUG: Content missing from metadata, fetching from downloadUrl: $downloadUrl")
                    val rawRequestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                    
                    if (!githubToken.isNullOrBlank()) {
                        rawRequestBuilder.header("Authorization", "Bearer $githubToken")
                    }
                    
                    val rawResponse = client.send(rawRequestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
                    println("DEBUG: download_url Fetch Status: ${rawResponse.statusCode()}")
                    if (rawResponse.statusCode() == 200) {
                        val base64 = Base64.getEncoder().encodeToString(rawResponse.body())
                        mutableJson["content"] = JsonPrimitive(base64)
                        mutableJson["encoding"] = JsonPrimitive("base64")
                        println("DEBUG: Successfully fetched raw content and encoded as Base64 (Length: ${base64.length})")
                    } else {
                        println("ERROR: Failed to fetch raw content. Status: ${rawResponse.statusCode()}")
                    }
                } else {
                    println("DEBUG: No download_url found in metadata")
                }
            } else {
                println("DEBUG: Content already present in metadata (Length: ${contentValue.length})")
            }
            
            ctx.res.status = 200
            ctx.res.setBodyText(JsonObject(mutableJson).toString())
            ctx.res.contentType = "application/json"
        } else {
            println("ERROR: GitHub API returned status ${response.statusCode()}")
            ctx.res.status = response.statusCode()
            val responseBody = response.body()
            val errorBody = if (response.statusCode() == 404 && githubToken.isNullOrBlank()) {
                println("INFO: 404 error detected and GITHUB_TOKEN is missing")
                "{\"message\": \"File not found. Note: GITHUB_TOKEN is missing on server.\", \"github_response\": $responseBody}"
            } else {
                responseBody
            }
            ctx.res.setBodyText(errorBody)
            ctx.res.contentType = "application/json"
        }
    } catch (e: Throwable) {
        println("CRITICAL ERROR in viewPackage: ${e.message}")
        e.printStackTrace()
        ctx.res.status = 500
        ctx.res.setBodyText("{\"message\": \"Internal Server Error: ${e.message}\"}")
        ctx.res.contentType = "application/json"
    } finally {
        println("--- [API: viewPackage] END ---")
    }
}
