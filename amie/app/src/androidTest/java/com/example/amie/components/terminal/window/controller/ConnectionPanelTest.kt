package com.example.amie.components.terminal.window.controller

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ConnectionPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectionPanel_rendersCorrectInformation() {
        val testName = "Terminal Alpha"
        val testEndpoint = 123
        val testStatus = true

        composeRule.setContent {
            ConnectionPanel(
                name = testName,
                deviceId = "test-device-id",
                endPoint = testEndpoint,
                status = testStatus,
                connectionRedirect = {}
            )
        }

        // Verify Endpoint text
        composeRule.onNodeWithText("Endpoint: $testEndpoint").assertIsDisplayed()
        
        // Verify Status text
        composeRule.onNodeWithText("Status: $testStatus").assertIsDisplayed()

        // Verify Disconnect button
        composeRule.onNodeWithText("Disconnect").assertIsDisplayed()

        // Verify TerminalWindow title (rendered as text inside TerminalWindow)
        composeRule.onNodeWithText(testName, substring = true).assertIsDisplayed()
    }

    @Test
    fun connectionPanel_settingsButtonTriggersRedirect() {
        var wasRedirectCalled = false

        composeRule.setContent {
            ConnectionPanel(
                name = "Test",
                deviceId = "test-device-id",
                status = false,
                connectionRedirect = { wasRedirectCalled = true }
            )
        }

        // Find and click the settings icon button
        composeRule.onNodeWithContentDescription("Manage settings").performClick()

        // Verify callback
        assert(wasRedirectCalled)
    }

    @Test
    fun connectionPanel_rendersNullEndpointCorrectly() {
        composeRule.setContent {
            ConnectionPanel(
                name = "Test",
                deviceId = "test-device-id",
                endPoint = null,
                status = false,
                connectionRedirect = {}
            )
        }

        // Verify Endpoint text with null
        composeRule.onNodeWithText("Endpoint: null").assertIsDisplayed()
    }
}
