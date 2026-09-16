package org.example.app.api.keycloak

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse


@Serializable
//do I really name this as an entity?
data class SsoRegistrationEntity(
    val url: String,
    val userName: String,
    val email: String,
    val paswd: String,
)

@Api("sso-post-register")
suspend fun postSsoRegistration(ctx: ApiContext) {
    try {
        val url = ctx.req.params.get("url") ?: throw IllegalArgumentException("Missing target URL")
        println("url: $url")
        val username = ctx.req.params.get("username") ?: ""
        val email = ctx.req.params.get("email") ?: ""
        val password = ctx.req.params.get("paswd") ?: ""

        val formData = mapOf(
            "username" to username,
            "email" to email,
            "password" to password,
            "password-confirm" to password
        ).map { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }.joinToString("&")

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build()

        // 4. Send server-to-server request
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(response.body())

    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Proxy Error: ${e.message}")
    }
}