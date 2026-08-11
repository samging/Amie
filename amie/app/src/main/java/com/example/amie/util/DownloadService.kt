package com.example.amie.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val sharedHttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
        connectTimeoutMillis = 60000
        socketTimeoutMillis = 60000
    }
    install(ContentNegotiation) {
        json(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        })
    }
}

suspend fun fetchFilesList(client: HttpClient = sharedHttpClient): String {
    return try {
        val response: HttpResponse = client.get("http://192.168.1.116:8080/list-files")

        if (response.status.value in 200..299) {
            response.bodyAsText()
        } else {
            "Server Error: Received status code ${response.status.value}"
        }
    } catch (e: HttpRequestTimeoutException) {
        "Network Timeout: The server took too long to respond."
    } catch (e: ConnectTimeoutException) {
        "Connection Timeout: Could not connect to the server at 192.168.1.116."
    } catch (e: IOException) {
        "Network Failure: Check your connection or server status. Details: ${e.localizedMessage}"
    } catch (e: Exception) {
        "Unexpected Error: ${e.localizedMessage}"
    }
}
