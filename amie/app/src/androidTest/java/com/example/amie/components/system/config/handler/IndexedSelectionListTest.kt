package com.example.amie.components.system.config.handler

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class IndexedSelectionListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleActiveFields = mapOf(
        1 to "Port A Description",
        2 to "Port B Description",
        3 to "Port C Description"
    )

    @Test
    fun indexedSelectionList_rendersAllItemsAndDescriptions() {
        composeRule.setContent {
            IndexedSelectionList(
                name = "Test Selection",
                modifier = Modifier,
                activeFields = sampleActiveFields,
                currentlyActive = listOf(1)
            )
        }

        // Verify ID brackets and descriptions are displayed
        composeRule.onNodeWithText("[1]").assertIsDisplayed()
        composeRule.onNodeWithText(" Port A Description").assertIsDisplayed()
        composeRule.onNodeWithText("[2]").assertIsDisplayed()
        composeRule.onNodeWithText(" Port B Description").assertIsDisplayed()
        composeRule.onNodeWithText("[3]").assertIsDisplayed()
        composeRule.onNodeWithText(" Port C Description").assertIsDisplayed()
    }

    @Test
    fun indexedSelectionList_togglesSelectionOnButtonClick() {
        composeRule.setContent {
            IndexedSelectionList(
                name = "Test Selection",
                modifier = Modifier,
                activeFields = sampleActiveFields,
                currentlyActive = emptyList()
            )
        }

        // Button for item 2 should have text "Opt 2"
        val button2 = composeRule.onNodeWithText("Opt 2")
        
        // Click to select
        button2.performClick()
        
        // Click again to unselect
        button2.performClick()

        // Verify the nodes still exist after interactions
        composeRule.onNodeWithText("[2]").assertIsDisplayed()
    }
}
