package com.example.amie.components.render.templates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException

enum class RestType {
    GET, POST, PUT
}

class Client {
    var isLoading by mutableStateOf(false)
    var validateResponse by mutableStateOf(false)
    var dialogResponse by mutableStateOf(false)
    var fatalDialogResponse by mutableStateOf(false)
    var lastErrorMessage by mutableStateOf<String?>(null)

    val client = HttpClient(Android) {
        install(HttpTimeout)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    suspend fun rest(
        vararg slug: String,
        restType: RestType,
        parameter: Any? = null
    ): Map<String, String>? {
        isLoading = true
        val hostIp = "192.168.1.103"
        val slugPath = slug.joinToString("/")

        println("DEBUG: Testing internet connectivity via google.com...")
        try {
            client.get("https://www.google.com")
            println("DEBUG: Internet check successful")
        } catch (e: Exception) {
            println("DEBUG: Internet check FAILED: ${e.message}")
        }

        try {
            val testResponse = client.get("http://$hostIp:8080/list-disk")
            println("DEBUG: Local server check successful: ${testResponse.status}")
        } catch (e: Exception) {
            println("DEBUG: Local server check FAILED: ${e.message}")
        }

        println("DEBUG: Starting request for slug: $slugPath at http://$hostIp:8080/")

        try {
            val response: HttpResponse = when (restType) {
                RestType.GET -> {
                    client.get("http://$hostIp:8080/$slugPath") {
                        timeout {
                            requestTimeoutMillis = 15_000L
                            connectTimeoutMillis = 15_000L
                            socketTimeoutMillis = 15_000L
                        }
                        contentType(ContentType.Application.Json)
                    }
                }

                RestType.POST -> {
                    client.post("http://$hostIp:8080/$slugPath") {
                        timeout {
                            requestTimeoutMillis = 15_000L
                            connectTimeoutMillis = 15_000L
                            socketTimeoutMillis = 15_000L
                        }
                        contentType(ContentType.Application.Json)
                        parameter?.let { setBody(it) }
                    }
                }

                RestType.PUT -> {
                    client.put("http://$hostIp:8080/$slugPath") {
                        timeout {
                            requestTimeoutMillis = 15_000L
                            connectTimeoutMillis = 15_000L
                            socketTimeoutMillis = 15_000L
                        }
                        contentType(ContentType.Application.Json)
                        parameter?.let { setBody(it) }
                    }
                }
            }

            println("DEBUG: Received response with status: ${response.status}")

            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.body<String>()
                } catch (e: Exception) {
                    null
                }
                val errorMessage = when (response.status.value) {
                    401 -> "Unauthorized: Please check your credentials."
                    404 -> "Not Found: The requested service could not be found."
                    500 -> "Server Error: Something went wrong on the server."
                    else -> errorBody ?: "Error code: ${response.status}"
                }
                println("DEBUG: Request failed: $errorMessage")
                handleLoginError(errorMessage)
                return emptyMap()
            } else {
                println("DEBUG: status ${response.status}")
                validateResponse = true
                return try {
                    response.body<Map<String, String>>()
                } catch (e: SerializationException) {
                    println("DEBUG: Serialization failed, but status was success")
                    mapOf("status" to "success")
                } catch (e: Exception) {
                    mapOf("status" to "success")
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            handleLoginError("Request timed out: The server is taking too long to respond.")
            return emptyMap()
        } catch (e: ConnectException) {
            handleLoginError("Connection refused: Ensure the server is running at http://$hostIp:8080")
            return emptyMap()
        } catch (e: IOException) {
            handleLoginError("Network error: Please check your internet connection.")
            return emptyMap()
        } catch (e: Exception) {
            println("DEBUG: Exception during request: ${e.message}")
            e.printStackTrace()
            handleLoginError("Unexpected error: ${e.message}")
            return emptyMap()
        } finally {
            isLoading = false
            println("DEBUG: Request finished")
        }
    }

    suspend fun handleLoginError(errorMessage: String?) {
        lastErrorMessage = errorMessage
        dialogResponse = true
        val hostIp = "192.168.1.103"

        try {
            val tweakResponse = client.post("http://$hostIp:8080/handle-login-error") {
                timeout {
                    requestTimeoutMillis = 9_000L
                    connectTimeoutMillis = 9_000L
                    socketTimeoutMillis = 9_000L
                }
                contentType(ContentType.Application.Json)
                setBody(mapOf("error" to (errorMessage ?: "Unknown"), "username" to "unknown_user"))
            }

            if (!tweakResponse.status.isSuccess()) {
                fatalDialogResponse = true
            }
        } catch (e: Exception) {
            println("Failed to log error to server: ${e.message}")
        }
    }
}
