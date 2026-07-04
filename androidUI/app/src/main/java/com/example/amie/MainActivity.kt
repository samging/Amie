package com.example.amie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.amie.ui.theme.AmieTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

///////////
//Local components:
import com.example.amie.components.buttonConfigure //deviceWindow.kt
import com.example.amie.components.headerWindow
import com.example.amie.components.managablePage
import com.example.amie.components.configurationPanel

//NavImports
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.amie.components.connectionPanel
import com.example.amie.components.scrollablePage
import kotlin.Int
import com.example.amie.components.DeviceManage
import com.example.amie.components.scrollableImutPage
import com.example.amie.components.searchField
import com.example.amie.testFiles.systemConfigReader
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.example.amie.components.connectButtonWriter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.amie.testFiles.readAndroidUsbPorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.compose.currentBackStackEntryAsState

enum class ScreenNavigation(val route: String) {
    Home("home1"),
    Manage("manage2"),
    Configure("configure3"),
    Connect("connectPage4"),
    ScrollableEndP("editWithCallback5"),
    ScrollablePort("editPort6"),
    ScrollableNameDevices("editNameDevice7"),
    ScrollableNamePlugins("editPlugins8"),
    ChangeEndpoint("changeEndpoint9"),

    AddDevice("changeEndpointConnectButton10")

}

/**
 * The host entry point Activity for the application. Sets up an edge-to-edge full-screen layout
 * and binds the root navigation framework within the custom theme wrapper [AmieTheme].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmieTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation()
                        //scrollablePage(name = "a", modifier = Modifier, activeFields = mapOf(1 to "a", 2 to "b", 3 to "c", 4 to "d", 5 to "e", 6 to "f"), currentlyActive = listOf(1,2,3))
                        //DeviceManage(name="And",modifier = Modifier) --konfiguragce ENDPOINT DEVICE MANAGE + Scrollable

                    }
                }
            }
        }
    }
}

@Serializable
data class Device(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null // Using safe optional matching
)


/**
 * The primary centralized Navigation Graph engine for the application.
 *
 * This component controls all screen states, coordinates multi-step transitions via a [NavHostController],
 * and handles disk/hardware side-effects asynchronously using Jetpack Compose lifecycles.
 *
 * ### Data Flow & State Lifecycles
 * * **Asynchronous Probing:** Triggers a one-shot [LaunchedEffect] on initial composition to read
 * persistent configuration profiles and query hardware USB port registers within `Dispatchers.IO`.
 * * **Inter-Screen Synchronization:** Observes a target boolean (`need_refresh`) inside the current route's
 * `savedStateHandle`. Screens that modify or delete configurations set this key to `true` before popping the
 * stack, prompting the [AppNavigation] layer to sync up latest database records immediately.
 *
 * ### Navigation Routing & API Warnings
 * > (e.g., `ScreenNavigation.Home.route`). However, the `NavHost` layout explicitly bypasses this enum
 * > for sub-screens, relying on hardcoded literal string signatures (such as `"manage/{deviceId}"` or `"addDevice/{idKey}"`).
 * > This splits your routing scheme across two paradigms and increases the risk of typo-driven crashes.
 * >
 * > `navController.navigate("addDevice/${null}")`. Because the route is explicitly registered as `"addDevice/{idKey}"`,
 * > passing a literal string token `"null"` maps it out to a text parameter value instead of an optional argument,
 * > which can disrupt downstream database indexing routines.
 * >
 * > are completely unmapped by the `NavHost` destinations, making them redundant code definitions.
 */
@Composable
fun AppNavigation() {
    var activePortsMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val context = LocalContext.current
    val configReader = remember { systemConfigReader() }

    LaunchedEffect(Unit) {
        configReader.loadConfiguration()
        withContext(Dispatchers.IO) {
            activePortsMap = readAndroidUsbPorts(context)
        }
    }


    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val needRefresh = navBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<Boolean>("need_refresh")
        ?.observeAsState()

    data class DeviceConnectionState(
        val name: String = "",
        val port: String = "",
        val deviceEndpoint: String = ""
    )

    LaunchedEffect(needRefresh?.value) {
        if (needRefresh?.value == true) {
            configReader.loadConfiguration()

            navBackStackEntry?.savedStateHandle?.set("need_refresh", false)
        }
    }


    NavHost(navController = navController, startDestination = ScreenNavigation.Home.route) {

        composable(route = ScreenNavigation.Home.route) {
            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(
                    name = "Android", endController = 3,
                    onBack = { /*root should have no pop stack*/ },
                    showOnBack = false,
                    addComponent = true,
                    addComponentNav = {
                        navController.navigate("addDevice/${null}")
                    })

                val configMap = configReader.configuredDevices

                for ((idKey, dev) in configMap) {
                    buttonConfigure(
                        name = dev.name,
                        deviceEdnpoint = dev.deviceEndpoint ?: "",
                        endPort = dev.port,
                        onManage = { navController.navigate("manage/$idKey") },
                        onConfigure = { navController.navigate("configure/$idKey") },
                        onConnectPage = { navController.navigate("connect/$idKey") },
                                onDisconnect = { navController.navigate("disconnect/$idKey") }
                    )
                }
            }
        }

        composable(route = "addDevice/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(
                    name = "Android",
                    endController = 3,
                    onBack = { navController.popBackStack() })

                data class DeviceConnectionState(
                    val name: String = "",
                    val port: String = "",
                    val deviceEndpoint: String = ""
                )

                var connectionState by remember { mutableStateOf(DeviceConnectionState()) }

                DeviceManage(
                    name = "Device Name",
                    valueOf = connectionState.name,
                    hideButton = true,
                    onValueChange = { connectionState = connectionState.copy(name = it)}
                )

                DeviceManage(
                    name = "Device Port",
                    valueOf = connectionState.port,
                    hideButton = true,
                    onValueChange = { connectionState = connectionState.copy(port = it) }
                )

                DeviceManage(
                    name = "Device Endpoint",
                    valueOf = connectionState.deviceEndpoint,
                    hideButton = true,
                    onValueChange = { connectionState = connectionState.copy(deviceEndpoint = it) }
                )

                connectButtonWriter(
                    indexDevice = configReader.generateAddId(),
                    modifier = Modifier,
                    keyValues = listOf("name", "port", "deviceEndpoint"),
                    valuesOf = listOf(
                        connectionState.name,
                        connectionState.port,
                        connectionState.deviceEndpoint
                    ),
                    redirectOnOk = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("need_refresh", true)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(route = "manage/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            val deviceNameList = configReader.parseConfigByTargetId("name", deviceId)
            val currentDeviceName = deviceNameList.firstOrNull() ?: "Unknown Device"


            val currentDevice = configReader.configuredDevices[deviceId]
            val manageContent = remember { mutableStateListOf("ID: $deviceId", "Name: $currentDeviceName", "output") }

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(
                    name = "Android back button",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )

                managablePage(
                    name = currentDevice?.name ?: "Unknown Device",
                    endController = 3,

                    deviceName = currentDevice?.name ?: "N/A",
                    portNumber = currentDevice?.port?.toIntOrNull() ?: 0,
                    endPoint = currentDevice?.deviceEndpoint?.toIntOrNull() ?: 0,

                    content = manageContent,
                    configureEndpoint = { navController.navigate("scrollableEndpoint/$deviceId") },
                    configurePort = { navController.navigate("scrollablePort/$deviceId") },
                    configureName = { navController.navigate("scrollableDevName/$deviceId") },
                    configurePlugins = { navController.navigate("scrollableNamePlugins/$deviceId") }
                )
            }
        }

        composable(route = "scrollableEndpoint/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
            headerWindow(
                name = "Android back button",
                endController = 3,
                onBack = { navController.popBackStack() }
            )
                val currentDevice = configReader.parseConfigByTargetId("deviceEndpoint",deviceId)

                DeviceManage(name="Endpoint Device", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "deviceEndpoint")
                Text(text="Current name: ${currentDevice.toString()}")
                val getAllNames = configReader.parseConfig("deviceEndpoint")
                val allNames: Map<Int, String> = getAllNames.withIndex().associate { it.index to it.value }

            scrollableImutPage(name = "a", modifier = Modifier, activeFields = allNames)
            }
        }

        composable(route = "disconnect/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            LaunchedEffect(deviceId) {
                withContext(Dispatchers.IO) {
                    configReader.deleteById(deviceId)
                }
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("need_refresh", true)
                navController.popBackStack()
            }
        }

        composable(route = "scrollablePort/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(name = "Android", endController = 3,onBack = { navController.popBackStack() })
                val currentPort = configReader.parseConfigByTargetId("port",deviceId)
                DeviceManage(name="Serial Port", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "port")
                Text(text="Current port: ${currentPort.toString()}")
                scrollableImutPage(name = "a", modifier = Modifier, activeFields = activePortsMap, currentlyActive = listOf(0))
            }
        }


        composable(route = "scrollableDevName/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(name = "Android", endController = 3,onBack = { navController.popBackStack() })
                DeviceManage(name="Device Name", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "name")
                val currentName = configReader.parseConfigByTargetId("name",deviceId)
                Text(text="Current name: ${currentName.toString()}")
                scrollableImutPage(name = "a", modifier = Modifier, activeFields = mapOf(1 to "Device 1", 2 to "Device 2", 3 to "Device 3"), currentlyActive = listOf(1,2,3))
            }
        }
        composable(route = "scrollableNamePlugins/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(name = "Android", endController = 3,onBack = { navController.popBackStack() })
                searchField(name="Plugins", modifier = Modifier, customText = "Search")
                scrollablePage(name = "a", modifier = Modifier, activeFields = mapOf(1 to "package1.bin", 2 to "package2.bin", 3 to "package3.bin", 4 to "package4.bin"), currentlyActive = listOf(1,2,3))
            }
        }

        composable(
            route = "configure/{deviceId}"
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(
                    name = "Android (ID: $deviceId)",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )
                configurationPanel(
                    name = "configure",
                    modifier = Modifier,
                    endPointNumber = 3,
                    allowCmd = true
                )
            }
        }

        composable(
            route = "connect/{deviceId}"
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(
                    name = "Android (ID: $deviceId)",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )
                connectionPanel(
                    name = "android",
                    endController = 20,
                    endPoint = 3,
                    status = true,
                    connectionRedirect = { navController.navigate("changeEndpoint/$deviceId") }
                )
            }
        }

        composable(route = "changeEndpoint/{deviceId}") { backStackEntry ->
            Column(modifier = Modifier.fillMaxSize()) {
                headerWindow(name = "Android", endController = 3,onBack = { navController.popBackStack() })
                DeviceManage(name="devices to toggle", modifier = Modifier)
            }
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Color.Black)
                .padding(top = 40.dp)
        ) { }

    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AmieTheme {
        Greeting("Android");
        buttonConfigure("Android", deviceEdnpoint = 3, onConfigure = {}, onManage = {}, onConnectPage = {});
    }
}