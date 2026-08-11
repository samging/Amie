package com.example.amie.data.remote.parser

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import com.example.amie.data.remote.DeviceDto
import com.example.amie.data.remote.DeviceActions
import com.example.amie.data.remote.DeviceRemoteService
import com.example.amie.util.sharedHttpClient
import java.util.HashMap

/**
 * A dummy PostResponse to satisfy compilation if not defined elsewhere.
 */
@Serializable
data class PostResponse(val status: String)

/**
 * A direct subsystem storage management utility responsible for reading, updating, generating,
 * and querying JSON configuration profiles stored at `/data/local/tmp/componentSettings.json`.
 */
class DeviceManagerJson(
    private var configFile: File = File("/data/local/tmp/componentSettings.json"),
    private val deviceService: DeviceRemoteService = DeviceRemoteService(sharedHttpClient)
) : DeviceManager {

    private var currentUsername: String = ""
    private val scope = CoroutineScope(Dispatchers.IO)

    private var configuredDevices by mutableStateOf<Map<String, Device>>(emptyMap())

    private val _postResult = MutableStateFlow<Result<PostResponse>?>(null)
    val postResult: StateFlow<Result<PostResponse>?> = _postResult.asStateFlow()

    // State holder for your devices mapped across the class
    private val _deviceMapState = MutableStateFlow<Map<String, DeviceDto>>(emptyMap())
    val deviceMapState: StateFlow<Map<String, DeviceDto>> = _deviceMapState.asStateFlow()

    init {
        load()
        syncGet()
    }

    private fun syncGet() {
        val effectiveUsername = currentUsername.ifEmpty { "user1" }
        scope.launch {
            println("DEBUG: Fetching remote config for user: $effectiveUsername")
            try {
                val initialMap = deviceService.repositoryDeviceController(
                    action = DeviceActions.GET,
                    username = effectiveUsername,
                    deviceMap = emptyMap()
                )
                _deviceMapState.value = initialMap
            } catch (e: Exception) {
                println("DEBUG: Fetch error: ${e.message}")
            }
        }
    }

    override fun setSession(username: String) {
        this.currentUsername = username
        syncGet()
    }

    private inline fun <T> MutableStateFlow<T>.updateFlow(
        noinline function: (T) -> T = { it }
    ) {
        this.value = function(this.value)
        val currentValue = this.value

        if (currentValue is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val mapValue = currentValue as Map<String, DeviceDto>

            val effectiveUsername = currentUsername.ifEmpty { "user1" }
            
            println("DEBUG: [SYNC-START] Triggering remote sync for user: $effectiveUsername with ${mapValue.size} devices")
            scope.launch {
                try {
                    val result = deviceService.repositoryDeviceController(
                        action = DeviceActions.SET,
                        username = effectiveUsername,
                        deviceMap = mapValue
                    )
                    println("DEBUG: [SYNC-SUCCESS] Remote sync completed for user: $effectiveUsername. Backend returned ${result.size} devices.")
                    _postResult.value = Result.success(PostResponse("Success"))
                } catch (e: Exception) {
                    println("DEBUG: [SYNC-ERROR] Remote sync failed for user: $effectiveUsername. Reason: ${e.message}")
                    _postResult.value = Result.failure(e)
                }
            }
        }
    }

    override fun load(
        configFile: File
    ) {
        println("DEBUG: [LOAD] Loading configuration from: ${configFile.absolutePath}")
        this.configFile = configFile

        if (!this.configFile.exists()) {
            println("DEBUG: [LOAD] Configuration file does not exist. Initializing with empty map.")
            configuredDevices = emptyMap()
            return
        }

        try {
            val jsonContent = configFile.readText().trim()
            if (jsonContent.isEmpty()) {
                println("DEBUG: [LOAD] Configuration file is empty.")
                configuredDevices = emptyMap()
                return
            }

            configuredDevices = Json.decodeFromString<Map<String, Device>>(jsonContent)
            println("DEBUG: [LOAD] Successfully loaded ${configuredDevices.size} devices from disk.")

        } catch (e: Exception) {
            println("DEBUG: [LOAD-ERROR] Failed to read configuration: ${e.message}")
            configuredDevices = emptyMap()
        }
    }

    override fun load() {
        load(this.configFile)
    }

    fun writeConfig(indexDevice: String, keyValue: List<String>, valueOf: List<String>, configFile: File? = null): List<String>{
        val targetFile = configFile ?: this.configFile

        if (!targetFile.exists()) {
            targetFile.parentFile?.mkdirs()
            targetFile.createNewFile()
            targetFile.writeText("{}")
        }

        try {
            val jsonContent: String = targetFile.readText()

            val deviceMap: MutableMap<String, Device> = if (jsonContent.trim().isEmpty()) {
                mutableMapOf()
            } else {
                Json.decodeFromString<Map<String, Device>>(jsonContent).toMutableMap()
            }

            val existingDevice = deviceMap[indexDevice] ?: Device(name = "", port = "")
            var updatedName = existingDevice.name
            var updatedPort = existingDevice.port
            var updatedEndpoint = existingDevice.deviceEndpoint

            for ((index, keyItem) in keyValue.withIndex()) {
                when (keyItem) {
                    "name" -> updatedName = valueOf[index]
                    "port" -> updatedPort = valueOf[index]
                    "deviceEndpoint" -> updatedEndpoint = valueOf[index]
                }
            }

            deviceMap[indexDevice] = Device(name = updatedName, port = updatedPort, deviceEndpoint = updatedEndpoint)
            println("DEBUG: [WRITE] Updated device $indexDevice: name=$updatedName, port=$updatedPort, endpoint=$updatedEndpoint")

            _deviceMapState.updateFlow {
                deviceMap.mapValues { (_, device) ->
                    DeviceDto(device.name, device.port, device.deviceEndpoint)
                }
            }
            val updatedJsonContent = Json { prettyPrint = true }.encodeToString(deviceMap)

            targetFile.writeText(updatedJsonContent)
            configuredDevices = deviceMap
            return listOf("updated!")
        } catch (e: Exception) {
            println("Parser error: ${e.toString()}")
            return listOf("[Error]: writer/updater")
        }
    }

    override fun generateAddId(): String {
        if (!configFile.exists()) {
            return "1"
        }

        return try {
            val jsonContent: String = configFile.readText()

            if (jsonContent.trim().isEmpty()) {
                return "1"
            }

            val deviceMap: Map<String, Device> = Json.decodeFromString(jsonContent)
            val existingIds = deviceMap.keys.mapNotNull { it.toIntOrNull() }

            if (existingIds.isEmpty()) {
                "1"
            } else {
                (existingIds.maxOrNull()?.plus(1) ?: 1).toString()
            }
        } catch (e: Exception) {
            println("Error generating ID: ${e.message}")
            "[Error]: json reader"
        }
    }

    override fun getDevice(deviceKey: String): Device? {
        return configuredDevices[deviceKey]
    }

    override fun count(): Int {
        return configuredDevices.size
    }

    override fun deleteById(idLabel: String) {
        if (!configFile.exists()) {
            println("Configuration file not found at: ${configFile.absolutePath}")
            return
        }

        try {
            val jsonContent = configFile.readText()
            val deviceMap: MutableMap<String, Device> = Json.decodeFromString<Map<String, Device>>(jsonContent).toMutableMap()

            if (deviceMap.containsKey(idLabel)) {
                deviceMap.remove(idLabel)
                println("DEBUG: [DELETE] Removed device with ID: $idLabel from local memory.")

                val updatedJson = Json.encodeToString(deviceMap)

                configFile.writeText(updatedJson)
                configuredDevices = deviceMap
                println("Successfully deleted device with ID: $idLabel")
                
                _deviceMapState.updateFlow {
                    deviceMap.mapValues { (_, device) ->
                        DeviceDto(device.name, device.port, device.deviceEndpoint)
                    }
                }
            } else {
                println("Device ID '$idLabel' not found in configuration.")
            }

        } catch (e: Exception) {
            println("Failed to modify configuration: ${e.message}")
        }
    }

    override fun parseConfig(labelRead: String): List<String> {
        if (!configFile.exists()) {
            println("Configuration file not found at: ${configFile.absolutePath}")
            return listOf("cfgNf" + configFile.absolutePath)
        }

        try {
            val jsonContent: String = configFile.readText()

            if (jsonContent.trim().isEmpty()) {
                return listOf("[Error]: file empty or null")
            }

            val deviceMap: Map<String, Device> = Json.decodeFromString(jsonContent)
            val output = mutableListOf<String>()

            for ((_, device) in deviceMap) {
                when (labelRead) {
                    "name" -> output.add(device.name)
                    "port" -> output.add(device.port)
                    "deviceEndpoint" -> output.add(device.deviceEndpoint ?: "")
                }
            }

            return output.toList()

        } catch (e: Exception) {
            println("Failed to read or parse configuration layout: ${e.message}")
            return listOf("[Error]: json reader")
        }
    }

    override fun parseConfigByTargetId(labelRead: String, targetId: String): List<String> {
        if (!configFile.exists()) {
            return listOf("cfgNf: ${configFile.absolutePath}")
        }

        return try {
            val jsonContent = configFile.readText().trim()

            if (jsonContent.isEmpty()) {
                return listOf("[Error]: file empty or null")
            }

            val deviceMap: Map<String, Device> = Json.decodeFromString(jsonContent)

            val device = deviceMap[targetId]

            if (device != null) {
                when (labelRead) {
                    "name" -> listOf(device.name)
                    "port" -> listOf(device.port)
                    "deviceEndpoint" -> listOf(device.deviceEndpoint.toString())
                    else -> listOf("[Error]: Unknown label '$labelRead'")
                }
            } else {
                listOf("[Error]: Device with ID '$targetId' not found")
            }

        } catch (e: Exception) {
            listOf("[Error]: json reader -> ${e.localizedMessage}")
        }
    }

    override fun getDevices(): Map<String, Device> {
        return HashMap(configuredDevices)
    }
}
