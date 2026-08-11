package com.example.amie.data.remote

import com.example.amie.data.remote.parser.Device
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeviceDto(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null
)

enum class DeviceActions {
    SET, GET
}

class DeviceRemoteService(private val client: HttpClient) {
    private val baseUrl = "http://10.0.2.2:8080" // 10.0.2.2 is localhost for Android Emulator

    suspend fun repositoryDeviceController(
        action: DeviceActions,
        username: String,
        deviceMap: Map<String, DeviceDto>
    ): Map<String, DeviceDto> {
        return try {
            val response: HttpResponse = client.post("$baseUrl/device-repository-controller") {
                parameter("action", action.name)
                parameter("username", username)
                contentType(ContentType.Application.Json)
                setBody(deviceMap)
            }
            
            if (response.status.isSuccess()) {
                if (action == DeviceActions.GET) {
                    Json.decodeFromString(response.bodyAsText())
                } else {
                    deviceMap
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Remote error: ${e.message}")
            emptyMap()
        }
    }
}
