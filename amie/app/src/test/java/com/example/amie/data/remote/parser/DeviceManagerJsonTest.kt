package com.example.amie.data.remote.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class DeviceManagerJsonTest {

    @TempDir
    lateinit var tempDir: File
    
    private lateinit var configFile: File
    private lateinit var deviceManager: DeviceManagerJson

    @BeforeEach
    fun setup() {
        configFile = File(tempDir, "test_config.json")
        deviceManager = DeviceManagerJson(configFile)
    }

    @Test
    fun `load should set empty map when file does not exist`() {
        assertFalse(configFile.exists())
        deviceManager.load(configFile)
        assertEquals(0, deviceManager.count())
    }

    @Test
    fun `load should parse valid JSON correctly`() {
        val devices = mapOf(
            "1" to Device("Device1", "COM1", "http://endpoint1"),
            "2" to Device("Device2", "COM2")
        )
        configFile.writeText(Json.encodeToString(devices))
        
        deviceManager.load(configFile)
        
        assertEquals(2, deviceManager.count())
        assertEquals("Device1", deviceManager.getDevice("1")?.name)
        assertEquals("COM2", deviceManager.getDevice("2")?.port)
        assertNull(deviceManager.getDevice("2")?.deviceEndpoint)
    }

    @Test
    fun `writeConfig should create file if it doesn't exist`() {
        assertFalse(configFile.exists())
        deviceManager.writeConfig("1", listOf("name", "port"), listOf("NewDevice", "COM3"))
        
        assertTrue(configFile.exists())
        val savedDevices: Map<String, Device> = Json.decodeFromString(configFile.readText())
        assertEquals(1, savedDevices.size)
        assertEquals("NewDevice", savedDevices["1"]?.name)
    }

    @Test
    fun `writeConfig should update existing device fields`() {
        val initialDevices = mapOf("1" to Device("OldName", "COM1"))
        configFile.writeText(Json.encodeToString(initialDevices))
        
        deviceManager.writeConfig("1", listOf("name", "deviceEndpoint"), listOf("UpdatedName", "ws://new"))
        
        val savedDevices: Map<String, Device> = Json.decodeFromString(configFile.readText())
        assertEquals("UpdatedName", savedDevices["1"]?.name)
        assertEquals("COM1", savedDevices["1"]?.port) // Port remains unchanged
        assertEquals("ws://new", savedDevices["1"]?.deviceEndpoint)
    }

    @Test
    fun `generateAddId should return 1 for empty or missing file`() {
        assertEquals("1", deviceManager.generateAddId())
        
        configFile.writeText("{}")
        assertEquals("1", deviceManager.generateAddId())
    }

    @Test
    fun `generateAddId should return max ID plus one`() {
        val devices = mapOf(
            "1" to Device("D1", "P1"),
            "5" to Device("D5", "P5"),
            "2" to Device("D2", "P2")
        )
        configFile.writeText(Json.encodeToString(devices))
        
        assertEquals("6", deviceManager.generateAddId())
    }

    @Test
    fun `deleteById should remove device and update file`() {
        val devices = mutableMapOf(
            "1" to Device("D1", "P1"),
            "2" to Device("D2", "P2")
        )
        configFile.writeText(Json.encodeToString(devices))
        
        deviceManager.deleteById("1")
        
        val savedDevices: Map<String, Device> = Json.decodeFromString(configFile.readText())
        assertFalse(savedDevices.containsKey("1"))
        assertTrue(savedDevices.containsKey("2"))
    }

    @Test
    fun `parseConfig should return list of requested fields`() {
        val devices = linkedMapOf( // Use linked map for deterministic order in test
            "1" to Device("Alpha", "P1"),
            "2" to Device("Beta", "P2")
        )
        configFile.writeText(Json.encodeToString(devices))
        
        val names = deviceManager.parseConfig("name")
        assertEquals(listOf("Alpha", "Beta"), names)
        
        val ports = deviceManager.parseConfig("port")
        assertEquals(listOf("P1", "P2"), ports)
    }

    @Test
    fun `parseConfigByTargetId should return single field list for target ID`() {
        val devices = mapOf("1" to Device("Target", "P10", "E100"))
        configFile.writeText(Json.encodeToString(devices))
        
        assertEquals(listOf("Target"), deviceManager.parseConfigByTargetId("name", "1"))
        assertEquals(listOf("P10"), deviceManager.parseConfigByTargetId("port", "1"))
        assertEquals(listOf("E100"), deviceManager.parseConfigByTargetId("deviceEndpoint", "1"))
    }

    @Test
    fun `parseConfigByTargetId should return error if ID not found`() {
        configFile.writeText("{}")
        val result = deviceManager.parseConfigByTargetId("name", "99")
        assertTrue(result[0].contains("not found"))
    }

    @Test
    fun `PhoneRendering data class test`() {
        val rendering = PhoneRendering(windowRendering = 5)
        assertEquals(5, rendering.windowRendering)
    }
}
