package com.example.amie.components.ui.viewport

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class DevicePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun devicePanel_rendersCorrectInformation() {
        val endpoint = "192.168.1.1"
        val port = 8080

        composeRule.setContent {
            DevicePanel(
                name = "TestPanel",
                deviceEdnpoint = endpoint,
                endPort = port,
                onManage = {},
                onConfigure = {},
                onConnectPage = {},
                onDisconnect = {}
            )
        }

        // Verify endpoint display
        composeRule.onNodeWithText(endpoint).assertIsDisplayed()
        
        // Verify port display (prefixed with COM)
        composeRule.onNodeWithText("COM$port").assertIsDisplayed()
    }

    @Test
    fun devicePanel_buttonsTriggerCallbacks() {
        var manageClicked = false
        var configureClicked = false
        var connectClicked = false
        var disconnectClicked = false

        composeRule.setContent {
            DevicePanel(
                name = "TestPanel",
                deviceEdnpoint = "dev",
                endPort = 1,
                onManage = { manageClicked = true },
                onConfigure = { configureClicked = true },
                onConnectPage = { connectClicked = true },
                onDisconnect = { disconnectClicked = true }
            )
        }

        // Test Manage button
        composeRule.onNodeWithText("Manage").performClick()
        assert(manageClicked)

        // Test Connect button
        composeRule.onNodeWithText("Connect").performClick()
        assert(connectClicked)

        // Test Configure button
        composeRule.onNodeWithText("Configure").performClick()
        assert(configureClicked)

        // Test Disconnect button
        composeRule.onNodeWithText("Disconnect").performClick()
        assert(disconnectClicked)
    }

    @Test
    fun devicePanel_handlesUnexpectedTypes() {
        composeRule.setContent {
            DevicePanel(
                name = "TestPanel",
                deviceEdnpoint = null,
                endPort = 1.2f, // Float is unexpected
                onManage = {},
                onConfigure = {},
                onConnectPage = {},
                onDisconnect = {}
            )
        }

        // Should show "Unexpected Type!" for both or at least one
        composeRule.onAllNodesWithText("Unexpected Type!").assertCountEquals(2)
    }
}
