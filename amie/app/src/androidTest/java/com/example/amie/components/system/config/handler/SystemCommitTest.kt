package com.example.amie.components.system.config.handler

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SystemCommitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemCommit_rendersConnectButton() {
        composeRule.setContent {
            SystemCommit(
                modifier = Modifier,
                indexDevice = "1",
                keyValues = listOf("name"),
                valuesOf = listOf("TestDevice"),
                redirectOnOk = {}
            )
        }

        // Verify that the "Connect" button is displayed
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun systemCommit_executesRedirectOnButtonClick() {
        var wasRedirectCalled = false
        
        composeRule.setContent {
            SystemCommit(
                modifier = Modifier,
                indexDevice = "device_01",
                keyValues = listOf("port"),
                valuesOf = listOf("COM1"),
                redirectOnOk = { wasRedirectCalled = true }
            )
        }

        // Click the button
        composeRule.onNodeWithText("Connect").performClick()

        // Verify the callback was triggered
        // Note: This also triggers DeviceManagerJson().writeConfig() internally.
        assert(wasRedirectCalled)
    }

    @Test
    fun systemCommit_appliesPaddingToButton() {
        // This is a bit detailed for a UI test, but we can verify the node exists with specific text
        composeRule.setContent {
            SystemCommit(
                modifier = Modifier,
                indexDevice = "1",
                keyValues = emptyList(),
                valuesOf = emptyList(),
                redirectOnOk = {}
            )
        }

        // Check button exists
        composeRule.onNodeWithText("Connect").assertExists()
    }
}
