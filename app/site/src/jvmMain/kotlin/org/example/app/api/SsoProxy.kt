package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Api("sso-proxy")
suspend fun getSsoRegistrationHtml(ctx: ApiContext) {
    try {
        val hostname = "192.168.1.114"
        val registrationUrl = "http://$hostname:8080/realms/master/protocol/openid-connect/registrations" +
                "?client_id=amie" +
                "&response_type=code" +
                "&scope=openid" +
                "&redirect_uri=http://$hostname:8081/oauthpage"

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(registrationUrl))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        ctx.res.status = response.statusCode()
        ctx.res.setBodyText(response.body())
        ctx.res.contentType = "text/html"

    } catch (e: Exception) {
        ctx.res.status = 500
        ctx.res.setBodyText("Proxy Error: ${e.message}")
    }
}
