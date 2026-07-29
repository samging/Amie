package com.example.amie.components.system.config.handler

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class PeripheralPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun peripheralPanel_rendersInitialState() {
        val name = "Printer"
        composeRule.setContent {
            PeripheralPanel(name = name)
        }

        // Check if the search label is displayed
        composeRule.onNodeWithText("Search for $name:").assertIsDisplayed()
        // Check if the input field label is displayed
        composeRule.onNodeWithText(name).assertIsDisplayed()
        // Check if the default button text is displayed
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun peripheralPanel_showsInitialValue() {
        val initialValue = "LaserJet"
        composeRule.setContent {
            PeripheralPanel(name = "Printer", valueOf = initialValue)
        }

        // Check if the initial value is present in the text field
        composeRule.onNodeWithText(initialValue).assertIsDisplayed()
    }

    @Test
    fun peripheralPanel_updatesBufferOnTyping() {
        var updatedValue = ""
        composeRule.setContent {
            PeripheralPanel(
                name = "Printer",
                onValueChange = { updatedValue = it }
            )
        }

        val inputText = "MyDevice"
        // Find the text field by its label and input text
        composeRule.onNodeWithText("Printer").performTextInput(inputText)
        
        // Verify the callback was triggered
        assert(updatedValue == inputText)
    }

    @Test
    fun peripheralPanel_showsSuccessMessageOnConnect() {
        composeRule.setContent {
            PeripheralPanel(name = "Printer", valueOf = "Dev1")
        }

        // Click the connect button
        composeRule.onNodeWithText("Connect").performClick()

        // Check for the success message
        composeRule.onNodeWithText("Connected safely to: Dev1").assertIsDisplayed()
    }

    @Test
    fun peripheralPanel_showsErrorMessageOnEmptyConnect() {
        composeRule.setContent {
            PeripheralPanel(name = "Printer", valueOf = "")
        }

        // Click the connect button with empty input
        composeRule.onNodeWithText("Connect").performClick()

        // Check for the error message
        composeRule.onNodeWithText("Couldn't find device name").assertIsDisplayed()
    }

    @Test
    fun peripheralPanel_hidesButtonWhenRequested() {
        composeRule.setContent {
            PeripheralPanel(name = "Printer", hideButton = true)
        }

        // Verify the button is not present
        composeRule.onNodeWithText("Connect").assertDoesNotExist()
    }

    @Test
    fun peripheralPanel_usesCustomButtonText() {
        val custom = "Pair"
        composeRule.setContent {
            PeripheralPanel(name = "Printer", customText = custom)
        }

        // Verify the button has the custom text
        composeRule.onNodeWithText(custom).assertIsDisplayed()
    }
}
