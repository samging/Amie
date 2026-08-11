package com.example.amie.data.remote.parser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * A CSV-based storage management utility that implements the [DeviceManager] interface.
 * Stores data in a flat "id,name,port,endpoint" format.
 */
class DeviceManagerCsv(private var configFile: File = File("/data/local/tmp/componentSettings.csv")) : DeviceManager {
    
    private var configuredDevices by mutableStateOf<Map<String, Device>>(emptyMap())

    override fun load(configFile: File) {
        this.configFile = configFile
        if (!configFile.exists()) {
            configuredDevices = emptyMap()
            return
        }

        try {
            val lines = configFile.readLines()
            val map = mutableMapOf<String, Device>()
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split(",")
                if (parts.size >= 3) {
                    val id = parts[0].trim()
                    val name = parts[1].trim()
                    val port = parts[2].trim()
                    val endpoint = if (parts.size > 3) {
                        parts[3].trim().takeIf { it.isNotEmpty() && it != "null" }
                    } else null
                    
                    map[id] = Device(name, port, endpoint)
                }
            }
            configuredDevices = map
        } catch (e: Exception) {
            println("CSV Parsing Error: ${e.message}")
            configuredDevices = emptyMap()
        }
    }

    override fun load() {
        load(this.configFile)
    }

    override fun count(): Int = configuredDevices.size

    override fun getDevice(deviceKey: String): Device? = configuredDevices[deviceKey]

    override fun getDevices(): Map<String, Device> = HashMap(configuredDevices)

    override fun generateAddId(): String {
        if (!configFile.exists() || configuredDevices.isEmpty()) return "1"
        val existingIds = configuredDevices.keys.mapNotNull { it.toIntOrNull() }
        return if (existingIds.isEmpty()) "1" else (existingIds.max() + 1).toString()
    }

    override fun parseConfig(labelRead: String): List<String> {
        return configuredDevices.values.map { device ->
            when (labelRead) {
                "name" -> device.name
                "port" -> device.port
                "deviceEndpoint" -> device.deviceEndpoint ?: ""
                else -> ""
            }
        }
    }

    override fun parseConfigByTargetId(labelRead: String, targetId: String): List<String> {
        val device = configuredDevices[targetId] ?: return listOf("[Error]: Device not found")
        return listOf(
            when (labelRead) {
                "name" -> device.name
                "port" -> device.port
                "deviceEndpoint" -> device.deviceEndpoint ?: ""
                else -> "[Error]: Unknown label"
            }
        )
    }

    override fun deleteById(idLabel: String) {
        val mutableMap = configuredDevices.toMutableMap()
        if (mutableMap.containsKey(idLabel)) {
            mutableMap.remove(idLabel)
            configuredDevices = mutableMap
            saveToDisk()
        }
    }

    override fun setSession(username: String) {
        // CSV is local-only, no network session needed for now
    }

    /**
     * Updates or creates a device entry and persists it to the CSV file.
     */
    fun writeConfig(indexDevice: String, keyValue: List<String>, valueOf: List<String>, configFile: File? = null): List<String> {
        val targetFile = configFile ?: this.configFile
        
        // Update local state first
        val mutableMap = configuredDevices.toMutableMap()
        val existingDevice = mutableMap[indexDevice] ?: Device("", "")
        
        var updatedName = existingDevice.name
        var updatedPort = existingDevice.port
        var updatedEndpoint = existingDevice.deviceEndpoint

        keyValue.forEachIndexed { index, key ->
            when (key) {
                "name" -> updatedName = valueOf[index]
                "port" -> updatedPort = valueOf[index]
                "deviceEndpoint" -> updatedEndpoint = valueOf[index]
            }
        }

        mutableMap[indexDevice] = Device(updatedName, updatedPort, updatedEndpoint)
        configuredDevices = mutableMap
        
        this.configFile = targetFile
        saveToDisk()
        
        return listOf("updated!")
    }

    private fun saveToDisk() {
        try {
            val csvContent = configuredDevices.map { (id, device) ->
                "$id,${device.name},${device.port},${device.deviceEndpoint ?: ""}"
            }.joinToString("\n")
            configFile.writeText(csvContent)
        } catch (e: Exception) {
            println("Failed to save CSV: ${e.message}")
        }
    }
}
