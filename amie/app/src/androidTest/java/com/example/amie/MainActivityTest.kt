package com.example.amie

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_launchesAndShowsHome() {
        // Since MainActivity just hosts AppNavigation, 
        // we check for a key element in AppNavigation (the Add button)
        composeRule.onNodeWithContentDescription("Create component").assertIsDisplayed()
    }
}
