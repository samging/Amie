package com.example.amie.components.terminal.window.controller

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class TerminalWindowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun terminalWindow_rendersTitleCorrectly() {
        val testTitle = "System Logs"
        composeRule.setContent {
            TerminalWindow(title = testTitle, deviceId = "test-device-id")
        }

        // Verify the title is displayed with the prefix
        composeRule.onNodeWithText("Terminal window: $testTitle").assertIsDisplayed()
    }

    @Test
    fun terminalWindow_showsCommandInputWhenAllowed() {
        composeRule.setContent {
            TerminalWindow(title = "Test", deviceId = "test-device-id", allowCmd = true)
        }

        // Verify the text field label exists
        composeRule.onNodeWithText("Enter terminal command").assertIsDisplayed()
    }

    @Test
    fun terminalWindow_hidesCommandInputWhenNotAllowed() {
        composeRule.setContent {
            TerminalWindow(title = "Test", deviceId = "test-device-id", allowCmd = false)
        }

        // Verify the text field does not exist
        composeRule.onNodeWithText("Enter terminal command").assertDoesNotExist()
    }

    @Test
    fun terminalWindow_sendIconAppearsOnlyWhenTextEntered() {
        composeRule.setContent {
            TerminalWindow(title = "Test", deviceId = "test-device-id", allowCmd = true)
        }

        // Initially, the send icon shouldn't be visible because input is empty
        composeRule.onNodeWithContentDescription("Send command").assertDoesNotExist()

        // Enter some text
        composeRule.onNodeWithText("Enter terminal command").performTextInput("ls -la")

        // Now the send icon should be visible
        composeRule.onNodeWithContentDescription("Send command").assertIsDisplayed()
        
        // Click the send icon to clear text (as per implementation)
        composeRule.onNodeWithContentDescription("Send command").performClick()
        
        // Icon should disappear again
        composeRule.onNodeWithContentDescription("Send command").assertDoesNotExist()
    }

    @Test
    fun terminalWindow_addsLogOnButtonClick() {
        val testLogs = mutableStateListOf<String>()
        composeRule.setContent {
            TerminalWindow(title = "Test", deviceId = "test-device-id", content = testLogs)
        }

        // Find the button (it's the only one with a click action at the bottom)
        composeRule.onNode(hasClickAction()).performClick()

        // After clicking, "someting1" should be added to logs and displayed
        composeRule.onNodeWithText("someting1").assertIsDisplayed()
    }
}
