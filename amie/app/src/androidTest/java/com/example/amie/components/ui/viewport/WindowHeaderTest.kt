package com.example.amie.components.ui.viewport

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class WindowHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun windowHeader_rendersBackIconByDefault() {
        var backClicked = false
        composeRule.setContent {
            WindowHeader(
                name = "Test",
                onBack = { backClicked = true }
            )
        }

        // Verify back icon is displayed (found by its content description)
        composeRule.onNodeWithContentDescription("Go back").assertIsDisplayed()

        // Click back button
        composeRule.onNodeWithContentDescription("Go back").performClick()
        assert(backClicked)
    }

    @Test
    fun windowHeader_hidesBackIconWhenRequested() {
        composeRule.setContent {
            WindowHeader(
                name = "Test",
                showOnBack = false,
                onBack = {}
            )
        }

        // Verify back icon is NOT displayed
        composeRule.onNodeWithContentDescription("Go back").assertDoesNotExist()
    }

    @Test
    fun windowHeader_rendersAddIconWhenEnabled() {
        var addClicked = false
        composeRule.setContent {
            WindowHeader(
                name = "Test",
                onBack = {},
                addComponent = true,
                addComponentNav = { addClicked = true }
            )
        }

        // Verify add icon is displayed
        composeRule.onNodeWithContentDescription("Create component").assertIsDisplayed()

        // Click add button
        composeRule.onNodeWithContentDescription("Create component").performClick()
        assert(addClicked)
    }

    @Test
    fun windowHeader_hidesAddIconByDefault() {
        composeRule.setContent {
            WindowHeader(
                name = "Test",
                onBack = {}
            )
        }

        // Verify add icon is NOT displayed
        composeRule.onNodeWithContentDescription("Create component").assertDoesNotExist()
    }
}
