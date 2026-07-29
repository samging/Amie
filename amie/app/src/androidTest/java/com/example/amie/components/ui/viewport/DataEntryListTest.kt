package com.example.amie.components.ui.viewport

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class DataEntryListTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataEntryList_rendersAllFields() {
        val testFields = mapOf(
            101 to "First Entry",
            202 to "Second Entry",
            303 to "Third Entry"
        )

        composeRule.setContent {
            DataEntryList(
                name = "Test List",
                modifier = Modifier,
                activeFields = testFields,
                currentlyActive = listOf(101)
            )
        }

        // Verify that all IDs and descriptions are displayed
        testFields.forEach { (id, description) ->
            composeRule.onNodeWithText("[$id]").assertIsDisplayed()
            composeRule.onNodeWithText(" $description").assertIsDisplayed()
        }
    }

    @Test
    fun dataEntryList_handlesEmptyFields() {
        composeRule.setContent {
            DataEntryList(
                name = "Empty List",
                modifier = Modifier,
                activeFields = emptyMap()
            )
        }

        // Verify that no list items are present (specifically the brackets)
        composeRule.onAllNodesWithText("[", substring = true).assertCountEquals(0)
    }

    @Test
    fun dataEntryList_rendersWithCustomModifier() {
        composeRule.setContent {
            DataEntryList(
                name = "Modifier Test",
                modifier = Modifier.padding(20.dp),
                activeFields = mapOf(1 to "Test"),
            )
        }

        // Verify rendering remains consistent
        composeRule.onNodeWithText("[1]").assertIsDisplayed()
        composeRule.onNodeWithText(" Test").assertIsDisplayed()
    }
}
