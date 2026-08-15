package com.example.amie.components.terminal.window.controller

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ConfigurationPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun configurationPanel_rendersWithEndpointTitle() {
        val endpoint = 42
        composeRule.setContent {
            ConfigurationPanel(
                deviceId = "test-device-id",
                endPointNumber = endpoint
            )
        }

        // Verify that the title with the endpoint number is displayed
        composeRule.onNodeWithText("Endpoint configuration $endpoint", substring = true).assertIsDisplayed()
    }

    @Test
    fun configurationPanel_rendersWithEmptyTitleWhenNoEndpoint() {
        composeRule.setContent {
            ConfigurationPanel(
                deviceId = "test-device-id",
                endPointNumber = null
            )
        }

        // The title should be empty, so we just check if the TerminalWindow is there.
        // Since the title is empty, we check that the specific endpoint text DOES NOT exist.
        composeRule.onNodeWithText("Endpoint configuration", substring = true).assertDoesNotExist()
    }

    @Test
    fun configurationPanel_forwardsAllowCmdToTerminalWindow() {
        composeRule.setContent {
            ConfigurationPanel(
                deviceId = "test-device-id",
                allowCmd = true
            )
        }

        // Verify that the command input field is displayed when allowCmd is true
        composeRule.onNodeWithText("Enter terminal command").assertIsDisplayed()
    }

    @Test
    fun configurationPanel_hidesCommandInputWhenAllowCmdIsFalse() {
        composeRule.setContent {
            ConfigurationPanel(
                deviceId = "test-device-id",
                allowCmd = false
            )
        }

        // Verify that the command input field is NOT displayed when allowCmd is false
        composeRule.onNodeWithText("Enter terminal command").assertDoesNotExist()
    }
}
