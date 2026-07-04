package com.example.amie.testFiles

import android.provider.ContactsContract
import androidx.compose.runtime.mutableStateOf
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.encodeToString

/**
 * Represents a peripheral device's persistent configuration profile.
 *
 * @property name The human-readable label identifying the target device profile.
 * @property port The communication channel index or virtual address mapping (e.g., a serial port index).
 * @property deviceEndpoint The optional network URI or destination boundary identifier for data routing.
 */
@Serializable
data class Device(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null
)

/**
 * An immutable rendering snapshot state tracking layered application windows.
 *
 * @property windowRendering An index counter denoting the current stacked display layer tracking state.
 */
data class PhoneRendering(
    val windowRendering: Int
)

/**
 * A direct subsystem storage management utility responsible for reading, updating, generating,
 * and querying JSON configuration profiles stored at `/data/local/tmp/componentSettings.json`.
 * This class exposes dynamic UI tracking fields through Compose backing properties to push real-time
 * structural mutations straight to active composable components.
 *
 * * ### Architectural & Performance Warnings
 * > `writeConfig`, `deleteById`, etc.) performs synchronous disk execution loops directly from the calling thread.
 * > **Never call these from a Composable layout container.** Move these operations into a `ViewModel` or
 * > execute them safely within `Dispatchers.IO` coroutine scopes.
 * > uses a forced non-null assertion (`!!`). If the profile map is completely blank or contains keys that aren't numeric
 * > integers, this call **will crash the application instantly** with a `NullPointerException`.
 * > duplicate states loading from the exact same JSON asset path. Combine their logic into a single source of truth.
 * > word `"undefined"` to disk. This corrupts the optional field structure, turning a valid `null` value into a populated String.
 */
class systemConfigReader {
    /**
     * Read-only public state tracking window layer presentation details.
     * Updatable internally via [setPhoneLayers].
     */
    var phoneLayers by mutableStateOf(PhoneRendering(windowRendering = 0))
        private set

    /** Updates the composite presentation layer configurations. */
    fun setPhoneLayers(newNumber: Int) {
        phoneLayers = phoneLayers.copy(windowRendering = newNumber)
    }

    /**
     * Active configuration state map used to sync state changes downstream into the Compose runtime layer.
     */
    var configuredDevices by mutableStateOf<Map<String, Device>>(emptyMap())
        private set

    /**
     * Reads the configuration manifest directly from local disk and updates the reactive [configuredDevices] state cache.
     * Drops back to an empty collection state gracefully if files are missing or unparseable.
     */
    fun loadConfiguration() {
        val file = File("/data/local/tmp/componentSettings.json")
        if (!file.exists()) {
            configuredDevices = emptyMap()
            return
        }

        try {
            val jsonContent = file.readText().trim()
            if (jsonContent.isEmpty()) {
                configuredDevices = emptyMap()
                return
            }

            configuredDevices = Json.decodeFromString<Map<String, Device>>(jsonContent)

        } catch (e: Exception) {
            println("Failed to read configuration: ${e.message}")
            configuredDevices = emptyMap()
        }
    }

    /**
     * Modifies or appends fields on a specified device record and saves the serialized results to disk.
     *
     * @param indexDevice The distinct primary key tag indexing the targeted device record inside the root map layout.
     * @param keyValue A sequential sequence array specifying the precise property keys to override (`"name"`, `"port"`, etc.).
     * @param valueOf A sequential string map value payload correlating precisely by list offset index to parameters in [keyValue].
     * @return A status collection containing an operation log statement or a tagged `[Error]` notice log.
     */
    fun writeConfig(indexDevice: String, keyValue: List<String>, valueOf: List<String>): List<String>{
        val file = File("/data/local/tmp/componentSettings.json")

        if (!file.exists()) {
            println("Configuration file not found at: ${file.absolutePath}")
            return listOf("[Error]: file does not exist")
        }

        try {
            val jsonContent: String = file.readText()

            val deviceMap: MutableMap<String, Device> = if (jsonContent.trim().isEmpty()) {
                mutableMapOf()
            } else {
                Json.decodeFromString<Map<String, Device>>(jsonContent).toMutableMap()
            }

            val existingDevice = deviceMap[indexDevice] ?: Device(name = "", port = "")
            var updatedName = existingDevice.name
            var updatedPort = existingDevice.port
            var updatedEndpoint = existingDevice.deviceEndpoint

                for ((index, keyItem) in keyValue.withIndex()) { //List of keys ("port", "name", ...)
                    when (keyItem) {
                        "name" -> updatedName = valueOf[index]
                        "port" -> updatedPort = valueOf[index]
                        "deviceEndpoint" -> updatedEndpoint = valueOf[index]
                    }
                }

            deviceMap[indexDevice] = Device(name = updatedName ?: "undefined", port = updatedPort ?: "undefined", deviceEndpoint = updatedEndpoint ?: "undefined")

            val updatedJsonContent = Json { prettyPrint = true }.encodeToString(deviceMap)

            file.writeText(updatedJsonContent)
            return listOf("updated!")
        } catch (e: Exception) {
            println("Parser error: ${e.toString()}")
            return listOf("[Error]: writer/updater")

        }
    }

    /**
     * Scans the underlying file system configuration keys to automatically generate a non-clashing numeric identity key string.
     * * @return The calculated next index string token increments, or an operation error message.
     */
    fun generateAddId(): String {
        val file = File("/data/local/tmp/componentSettings.json")

        if (!file.exists()) {
            println("Configuration file not found at: ${file.absolutePath}")
            println("Storage Directories available to this app: " + System.getenv("ANDROID_DATA"))
        }

        try {
            val jsonContent: String = file.readText()

            if (jsonContent.trim().isEmpty()) {
                return StringBuilder("[Error]: file empty or null").toString()
            }

            val deviceMap: Map<String, Device> = Json.decodeFromString(jsonContent)
            val existingIds = deviceMap.keys.mapNotNull { it.toIntOrNull() }

            return (existingIds.takeLast(1).maxOrNull()!! + 1).toString()

        } catch (e: Exception) {
            return StringBuilder("[Error]: json reader").toString()
        }
    }
    var configuredDevicez: Map<String, Device> = emptyMap()
        private set

    fun loadFromDisk() {
        val file = File("/data/local/tmp/componentSettings.json")
        if (file.exists()) {
            try {
                val jsonContent = file.readText()
                configuredDevicez = Json.decodeFromString(jsonContent)
            } catch (e: Exception) {
                println("Failed to read config: ${e.message}")
            }
        }
    }

    /**
     * Completely purges a targeted device configuration profile structure from the file storage state mapping matching the [idLabel].
     *
     * @param idLabel The specific target dictionary text ID requested for total structural deletion.
     */
    fun deleteById(idLabel: String) {
        val file = File("/data/local/tmp/componentSettings.json")

        if (!file.exists()) {
            println("Configuration file not found at: ${file.absolutePath}")
            return
        }

        try {
            val jsonContent = file.readText()
            val deviceMap: MutableMap<String, Device> = Json.decodeFromString(jsonContent)

            if (deviceMap.containsKey(idLabel)) {
                deviceMap.remove(idLabel)

                val updatedJson = Json.encodeToString(deviceMap)

                file.writeText(updatedJson)
                println("Successfully deleted device with ID: $idLabel")
            } else {
                println("Device ID '$idLabel' not found in configuration.")
            }

        } catch (e: Exception) {
            println("Failed to modify configuration: ${e.message}")
        }
    }

    /**
     * Filters and collects atomic device parameters matching the given [labelRead] from all registered configurations.
     *
     * @param labelRead The configuration map structural variable key name to scrap (`"name"` or `"port"`).
     * @return A compiled sequence tracking all matched property strings parsed from the dictionary pool.
     */
    fun parseConfig(labelRead: String): List<String> {
        val file = File("/data/local/tmp/componentSettings.json")
        if (!file.exists()) {
            println("Configuration file not found at: ${file.absolutePath}")
            println("Storage Directories available to this app: " + System.getenv("ANDROID_DATA"))
            return listOf("cfgNf" + file.absolutePath)
        }

        try {
            val jsonContent: String = file.readText()

            if (jsonContent.trim().isEmpty()) {
                return listOf("[Error]: file empty or null")
            }

            val deviceMap: Map<String, Device> = Json.decodeFromString(jsonContent)
            val output = mutableListOf<String>()

            for ((_, device) in deviceMap) {
                when (labelRead) {
                    "name" -> output.add(device.name)
                    "port" -> output.add(device.port)
                }
            }

            return output.toList()

        } catch (e: Exception) {
            println("Failed to read or parse configuration layout: ${e.message}")
            return listOf("[Error]: json reader")
        }
    }

    /**
     * Isolates a unique device matching a specified ID, extracting a single parameter value string.
     *
     * @param labelRead The exact property column parameter token to isolate (`"name"`, `"port"`, or `"deviceEndpoint"`).
     * @param targetId The distinct device identity index identifier key matching records on the storage system map.
     * @return A list containing the specific isolated parameter data string, or standard exception error diagnostics.
     */
    fun parseConfigByTargetId(labelRead: String, targetId: String): List<String> {
        val file = File("/data/local/tmp/componentSettings.json")
        if (!file.exists()) {
            return listOf("cfgNf: ${file.absolutePath}")
        }

        return try {
            val jsonContent = file.readText().trim()

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
}