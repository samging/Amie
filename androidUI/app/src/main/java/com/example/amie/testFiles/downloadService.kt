package com.example.amie.testFiles

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import io.ktor.client.network.sockets.ConnectTimeoutException
val sharedHttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 15000
        socketTimeoutMillis = 15000
    }
}

/**
 * Fetches the raw file manifest string from the local network asset server.
 *
 * @return The server body string on success, or a descriptive error message on failure.
 */
suspend fun fetchFilesList(): String {
    return try {
        val response: HttpResponse = sharedHttpClient.get("http://192.168.1.116:8080/list-files")

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