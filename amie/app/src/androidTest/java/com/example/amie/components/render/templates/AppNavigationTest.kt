package com.example.amie.components.render.templates

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appNavigation_initialState_showsHome() {
        composeRule.setContent {
            AppNavigation()
        }

        // Verify WindowHeader is present by checking the content description of the Add button
        composeRule.onNodeWithContentDescription("Create component").assertIsDisplayed()
        
        // Root back button should be hidden (showOnBack = false)
        composeRule.onNodeWithContentDescription("Manage settings").assertDoesNotExist()
    }

    @Test
    fun appNavigation_addDevice_submitFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // Navigate to Add Device
        composeRule.onNodeWithContentDescription("Create component").performClick()

        // Fill in the fields
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("New Test Device")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("123")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("456")

        // Find and click the "Connect" button in SystemCommit
        composeRule.onNodeWithText("Connect").performClick()

        // Verify we returned to home screen and the device is shown (COM123)
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("COM123", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun appNavigation_manageDevice_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("ManageTestDevice")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("80")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("443")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Click Manage
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Manage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Manage").onLast().performClick()

        // 3. Verify ManageablePage (Terminal window)
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Terminal window:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        
        // 4. Navigate to scrollableDevName (Index 3 of "Manage settings" in ManageablePage)
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().size >= 4
        }
        composeRule.onAllNodesWithContentDescription("Manage settings")[3].performClick()
        
        // 5. Verify screen
        composeRule.onNode(hasText("Search for Device Name:", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Set").assertIsDisplayed()
    }

    @Test
    fun appNavigation_scrollableEndpoint_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("EndpointTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("80")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("443")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Go to Manage
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Manage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Manage").onLast().performClick()

        // 3. Navigate to scrollableEndpoint (Index 1 of "Manage settings")
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodesWithContentDescription("Manage settings")[1].performClick()

        // 4. Verify scrollableEndpoint screen
        composeRule.onNode(hasText("Search for Endpoint Device:", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Set").assertIsDisplayed()
    }

    @Test
    fun appNavigation_scrollablePort_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("PortTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("80")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("443")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Go to Manage
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Manage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Manage").onLast().performClick()

        // 3. Navigate to scrollablePort (Index 2 of "Manage settings")
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().size >= 3
        }
        composeRule.onAllNodesWithContentDescription("Manage settings")[2].performClick()

        // 4. Verify scrollablePort screen
        composeRule.onNode(hasText("Search for Serial Port:", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Set").assertIsDisplayed()
    }

    @Test
    fun appNavigation_scrollableNamePlugins_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("PluginsTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("80")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("443")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Go to Manage
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Manage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Manage").onLast().performClick()

        // 3. Navigate to scrollableNamePlugins (Index 4 of "Manage settings")
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().size >= 5
        }
        composeRule.onAllNodesWithContentDescription("Manage settings")[4].performClick()

        // 4. Verify scrollableNamePlugins screen
        composeRule.onNodeWithText("Plugins", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun appNavigation_configureDevice_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("ConfigureTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("123")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("456")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Click Configure
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Configure").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Configure").onLast().performClick()

        // 3. Verify Configuration screen
        composeRule.onNode(hasText("Android (ID:", substring = true)).assertIsDisplayed()
    }

    @Test
    fun appNavigation_connectDevice_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("ConnectPageTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("123")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("456")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Click Connect (in DevicePanel)
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Connect").fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodesWithText("Connect").onLast().performClick()

        // 3. Verify Connection screen
        composeRule.onNode(hasText("Android (ID:", substring = true)).assertIsDisplayed()
    }

    @Test
    fun appNavigation_changeEndpoint_navigationFlow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("ChangeTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("123")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("456")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Click Connect (in DevicePanel)
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Connect").fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodesWithText("Connect").onLast().performClick()

        // 3. Click Settings gear in ConnectionPanel to go to changeEndpoint
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Manage settings").onLast().performClick()

        // 4. Verify changeEndpoint screen
        composeRule.onNodeWithText("devices to toggle").assertIsDisplayed()
    }

    @Test
    fun appNavigation_fullNavigationDepth_test() {
        composeRule.setContent {
            AppNavigation()
        }

        // Home -> Add Device
        composeRule.onNodeWithContentDescription("Create component").performClick()

        // Add Device -> Back -> Home
        composeRule.onAllNodesWithContentDescription("Manage settings").onFirst().performClick()
        composeRule.onNodeWithContentDescription("Create component").assertIsDisplayed()

        // Create device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("DepthTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("77")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("88")
        composeRule.onNodeWithText("Connect").performClick()

        // Home -> Manage
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Manage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Manage").onLast().performClick()

        // Manage -> scrollablePort
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithContentDescription("Manage settings").fetchSemanticsNodes().size >= 3
        }
        composeRule.onAllNodesWithContentDescription("Manage settings")[2].performClick()

        // Verify scrollablePort
        composeRule.onNode(hasText("Search for Serial Port:", substring = true)).assertIsDisplayed()

        // scrollablePort -> Back -> Manage
        composeRule.onAllNodesWithContentDescription("Manage settings").onFirst().performClick()
        composeRule.onNodeWithText("Terminal window:", substring = true).assertIsDisplayed()

        // Manage -> Back -> Home
        composeRule.onAllNodesWithContentDescription("Manage settings").onFirst().performClick()
        composeRule.onNodeWithContentDescription("Create component").assertIsDisplayed()
    }

    @Test
    fun appNavigation_disconnectDevice_flow() {
        composeRule.setContent {
            AppNavigation()
        }

        // 1. Create a device
        composeRule.onNodeWithContentDescription("Create component").performClick()
        composeRule.onNode(hasText("Device Name") and hasSetTextAction()).performTextInput("DisconnectTest")
        composeRule.onNode(hasText("Device Port") and hasSetTextAction()).performTextInput("99")
        composeRule.onNode(hasText("Device Endpoint") and hasSetTextAction()).performTextInput("88")
        composeRule.onNodeWithText("Connect").performClick()

        // 2. Wait for it to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("COM99", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Click Disconnect
        composeRule.onAllNodesWithText("Disconnect").onLast().performClick()

        // 4. Verify it's gone
        composeRule.onNodeWithText("COM99", substring = true).assertDoesNotExist()
    }
}
