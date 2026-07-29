package com.example.amie.components.terminal.window.controller

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ManageablePageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manageablePage_rendersAllFieldsCorrectly() {
        val testName = "Main Terminal"
        val testEndpoint = 10
        val testPort = 8080
        val testDevice = "AmieNode-01"
        val testPlugins = mutableStateListOf<String?>("PluginA", "PluginB")
        val testLogs = mutableStateListOf("Log 1", "Log 2")

        composeRule.setContent {
            ManageablePage(
                name = testName,
                endController = 1,
                endPoint = testEndpoint,
                portNumber = testPort,
                deviceName = testDevice,
                codePlugin = testPlugins,
                content = testLogs,
                configureEndpoint = {},
                configurePort = {},
                configureName = {},
                configurePlugins = {}
            )
        }

        // Verify all information rows
        composeRule.onNodeWithText("Endpoint: $testEndpoint").assertIsDisplayed()
        composeRule.onNodeWithText("Port: $testPort").assertIsDisplayed()
        composeRule.onNodeWithText("Device Name: $testDevice").assertIsDisplayed()
        composeRule.onNodeWithText("Code Plugins (${testPlugins.size}): $testPlugins").assertIsDisplayed()

        // Verify TerminalWindow title and content
        composeRule.onNodeWithText(testName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Log 1").assertIsDisplayed()
        composeRule.onNodeWithText("Log 2").assertIsDisplayed()
    }

    @Test
    fun manageablePage_configureButtonsTriggerCorrectCallbacks() {
        var endpointCalled = false
        var portCalled = false
        var nameCalled = false
        var pluginsCalled = false

        composeRule.setContent {
            ManageablePage(
                name = "Test",
                endController = 1,
                content = remember { mutableStateListOf<String>() },
                configureEndpoint = { endpointCalled = true },
                configurePort = { portCalled = true },
                configureName = { nameCalled = true },
                configurePlugins = { pluginsCalled = true }
            )
        }

        // Since multiple buttons have the same content description, 
        // we use their position/index to click them.
        val settingsButtons = composeRule.onAllNodesWithContentDescription("Manage settings")
        
        // 1. Configure Endpoint
        settingsButtons[0].performClick()
        assert(endpointCalled)

        // 2. Configure Port
        settingsButtons[1].performClick()
        assert(portCalled)

        // 3. Configure Name
        settingsButtons[2].performClick()
        assert(nameCalled)

        // 4. Configure Plugins
        settingsButtons[3].performClick()
        assert(pluginsCalled)
    }
}
