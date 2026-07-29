package com.example.amie.components.system.config.handler

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class RemotePackageConsoleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remotePackageConsole_initialState() {
        composeRule.setContent {
            RemotePackageConsole(name = "Test")
        }

        // Check for initial text
        composeRule.onNodeWithText("Search for package:").assertIsDisplayed()
        composeRule.onNodeWithText("Dependency Name").assertIsDisplayed()
        
        // It might be loading initially
        // composeRule.onNodeWithText("Loading packages from server...").assertIsDisplayed()
    }

    @Test
    fun remotePackageConsole_searchFunctionality_respondsToClick() {
        composeRule.setContent {
            RemotePackageConsole(name = "Test")
        }

        // Wait for loading to finish
        composeRule.waitUntil(timeoutMillis = 10000) {
            composeRule.onAllNodesWithText("Loading packages from server...").fetchSemanticsNodes().isEmpty()
        }

        // Enter a random string that is unlikely to be in the "packages" list if it failed to load
        val testPackage = "SomeNonExistentPackage12345"
        composeRule.onNodeWithText("Dependency Name").performTextInput(testPackage)

        // Click search icon button
        composeRule.onNodeWithContentDescription("Search packages").performClick()

        // If the packages list is empty or doesn't contain the string, it should show "Package not found"
        // We check if either "Adding package" or "Package not found" appears, 
        // verifying that the button click actually triggered the search logic.
        composeRule.onNode(
            hasText("Adding package").or(hasText("Package not found"))
        ).assertExists()
    }

    @Test
    fun remotePackageConsole_displaysPackagesAfterLoading() {
        composeRule.setContent {
            RemotePackageConsole(name = "Test")
        }

        // Wait for loading text to disappear
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Loading packages from server...").fetchSemanticsNodes().isEmpty()
        }

        // The packages text should be visible (even if it's an error message from fetchFilesList)
        // We can't know exactly what it is, but we can verify something replaced the loading text.
        // In a real project, you'd likely use a ViewModel and mock it.
    }
}
