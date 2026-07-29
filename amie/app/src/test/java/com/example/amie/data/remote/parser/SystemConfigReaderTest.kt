package com.example.amie.data.remote.parser

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File

import io.github.oshai.kotlinlogging.KotlinLogging

class SystemConfigReaderTest {
    @BeforeEach
    fun beforeEach() {
        logger.info { "beforeEach" }
    }
    @AfterEach
    fun afterEach() {
        logger.info { "after each" }
    }


    companion object {
        private val logger = KotlinLogging.logger {}

        @JvmStatic
        @AfterAll
        fun afterAll(): Unit {
            logger.info { "after all done?" }

        }
    }

    private val loader = DeviceManagerJson()

    @Test
    fun `loadConfiguration sets emptyMap when file does not exist`(@TempDir tempDir: File) {
        val nonExistentFile = File(tempDir, "missing.json")

        loader.load(configFile = nonExistentFile)

        assertTrue(loader.count() == 0)
    }

    @Test
    fun `loadConfiguration successfully parses valid JSON`(@TempDir tempDir: File) {
        val configFile = File(tempDir, "componentSettings.json").apply {
          //  writeText("""{"dev_1": {"name": "Device Alpha", "port": "24"}}""")
             writeText("""
                 {
                    "dev_1": {"name": "Device Alpha", "port": "24"},
                    "dev_2": {"name": "Device Alpha", "port": "24"}
                 }
                 """.trimIndent())
        }

        loader.load(configFile = configFile)

        assertEquals(2, loader.count())
        assertEquals(Device("Device Alpha", "24"), loader.getDevice("dev_1"))
    }

}