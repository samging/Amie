package com.example.amie.components.render.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.amie.components.system.config.handler.IndexedSelectionList
import com.example.amie.components.system.config.handler.PeripheralPanel
import com.example.amie.components.system.config.handler.RemotePackageConsole
import com.example.amie.components.system.config.handler.SystemCommit
import com.example.amie.components.terminal.window.controller.ConfigurationPanel
import com.example.amie.components.terminal.window.controller.ConnectionPanel
import com.example.amie.components.terminal.window.controller.ManageablePage
import com.example.amie.components.ui.viewport.DataEntryList
import com.example.amie.components.ui.viewport.DevicePanel
import com.example.amie.components.ui.viewport.WindowHeader
import com.example.amie.data.remote.parser.DeviceManager
import com.example.amie.data.remote.parser.DeviceManagerFactory
import com.example.amie.util.readAndroidUsbPorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

/**
 * The primary centralized Navigation Graph engine for the application.
 *
 * This component controls all screen states, coordinates multi-step transitions via a [NavHostController],
 * and handles disk/hardware side-effects asynchronously using Jetpack Compose lifecycles.
 */
@Composable
fun AppNavigation() {
    var activePortsMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var currentUser by remember { mutableStateOf("") }
    val context = LocalContext.current
    val configFile = remember { File(context.filesDir, "componentSettings.json") }
    val configReader: DeviceManager = remember { DeviceManagerFactory.create(configFile) }
    val scope = rememberCoroutineScope()
    val logFile = remember { File(context.filesDir, "logs.txt") }
    if (!logFile.exists()) {
        logFile.writeText("System initialized\nWaiting for logs...")
    }

    LaunchedEffect(Unit) {
        configReader.load(configFile)
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
            configReader.load(configFile)
            navBackStackEntry?.savedStateHandle?.set("need_refresh", false)
        }
    }


    NavHost(navController = navController, startDestination = "welcome-page") {
        composable(route = "welcome-page") {
            WelcomePage(
                navController = navController
            )
        }

        composable(route = "login") {
            LoginPage(onLoginSuccess = { username ->
                currentUser = username
                navController.navigate("home1/") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }

        composable(route = "home1/") {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Dashboard",
                    showUser = true,
                    user = currentUser,
                    onBack = { /*root should have no pop stack*/ },
                    showOnBack = false,
                    addComponent = true,
                    addComponentNav = {
                        navController.navigate("addDevice/${null}")
                    })

                val configMap = configReader.getDevices()

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    for ((idKey, dev) in configMap) {
                        DevicePanel(
                            name = dev.name,
                            deviceEdnpoint = dev.deviceEndpoint ?: "",
                            endPort = dev.port,
                            onManage = { navController.navigate("configure/$idKey") },
                            onConfigure = { navController.navigate("manage/$idKey") },
                            onConnectPage = { navController.navigate("connect/$idKey") },
                            onDisconnect = { navController.navigate("disconnect/$idKey") }
                        )
                    }
                }
            }
        }

        composable(route = "addDevice/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Add Device",
                    onBack = { navController.popBackStack() })

                Column(modifier = Modifier.padding(16.dp)) {
                    data class DeviceConnectionState(
                        val name: String = "",
                        val port: String = "",
                        val deviceEndpoint: String = ""
                    )

                    var connectionState by remember { mutableStateOf(DeviceConnectionState()) }

                    PeripheralPanel(
                        name = "Device Name",
                        valueOf = connectionState.name,
                        hideButton = true,
                        onValueChange = { connectionState = connectionState.copy(name = it)},
                        deviceManager = configReader
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PeripheralPanel(
                        name = "Device Port",
                        valueOf = connectionState.port,
                        hideButton = true,
                        onValueChange = { connectionState = connectionState.copy(port = it) },
                        deviceManager = configReader
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PeripheralPanel(
                        name = "Device Endpoint",
                        valueOf = connectionState.deviceEndpoint,
                        hideButton = true,
                        onValueChange = { connectionState = connectionState.copy(deviceEndpoint = it) },
                        deviceManager = configReader
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    SystemCommit(
                        indexDevice = configReader.generateAddId(),
                        modifier = Modifier.fillMaxWidth(),
                        keyValues = listOf("name", "port", "deviceEndpoint"),
                        valuesOf = listOf(
                            connectionState.name,
                            connectionState.port,
                            connectionState.deviceEndpoint
                        ),
                        deviceManager = configReader,
                        redirectOnOk = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("need_refresh", true)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        composable(route = "manage/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            val deviceNameList = configReader.parseConfigByTargetId("name", deviceId)
            val currentDeviceName = deviceNameList.firstOrNull() ?: "Unknown Device"


            val currentDevice = configReader.getDevice(deviceId)
            val manageContent = remember { mutableStateListOf("ID: $deviceId", "Name: $currentDeviceName", "output") }

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Manage Device",
                    onBack = { navController.popBackStack() }
                )

                ManageablePage(
                    name = currentDevice?.name ?: "Unknown Device",
                    deviceId = deviceId,

                    deviceName = currentDevice?.name ?: "N/A",
                    portNumber = currentDevice?.port?.toIntOrNull() ?: 0,
                    endPoint = currentDevice?.deviceEndpoint?.toIntOrNull() ?: 0,

                    content = manageContent,
                    configureEndpoint = { navController.navigate("scrollableEndpoint/$deviceId/$currentUser") },
                    configurePort = { navController.navigate("scrollablePort/$deviceId") },
                    configureName = { navController.navigate("scrollableDevName/$deviceId") },
                    configurePlugins = { navController.navigate("scrollableNamePlugins/$deviceId") }
                )
            }
        }

        composable(route = "scrollableEndpoint/{idKey}/{username}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""
            val endpoint = backStackEntry.arguments?.getString("username") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Endpoint",
                    onBack = { navController.popBackStack() }
                )
                val currentDevice = configReader.parseConfigByTargetId("deviceEndpoint",deviceId)

                Column(modifier = Modifier.padding(16.dp)) {
                    PeripheralPanel(
                        name="Endpoint Device",
                        modifier = Modifier,
                        customText = "Set",
                        writeId = deviceId,
                        keyQuery = "deviceEndpoint",
                        deviceManager = configReader,
                        username = endpoint
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text="Current: ${endpoint}-${currentDevice}", color = Color(0xFF878e9c), fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val getAllNames = configReader.parseConfig("deviceEndpoint")
                    val allNames: Map<Int, String> = getAllNames.withIndex().associate { it.index to it.value }

                    DataEntryList(name = "a", modifier = Modifier, activeFields = allNames)
                }
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

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(name = "Serial Port", onBack = { navController.popBackStack() })
                
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentPort = configReader.parseConfigByTargetId("port",deviceId)
                    PeripheralPanel(name="Serial Port", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "port", deviceManager = configReader)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text="Current port: ${currentPort}", color = Color(0xFF878e9c), fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    DataEntryList(name = "a", modifier = Modifier, activeFields = activePortsMap, currentlyActive = listOf(0))
                }
            }
        }


        composable(route = "scrollableDevName/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(name = "Device Name", onBack = { navController.popBackStack() })
                Column(modifier = Modifier.padding(16.dp)) {
                    PeripheralPanel(name="Device Name", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "name", deviceManager = configReader)
                }
            }
        }
        composable(route = "scrollableNamePlugins/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(name = "Plugins", onBack = { navController.popBackStack() })
                Column(modifier = Modifier.padding(16.dp)) {
                    RemotePackageConsole(name="Plugins", modifier = Modifier, customText = "Search")
                    Spacer(modifier = Modifier.height(16.dp))
                    IndexedSelectionList(name = "a", modifier = Modifier, activeFields = mapOf(1 to "package1.bin", 2 to "package2.bin", 3 to "package3.bin", 4 to "package4.bin"), currentlyActive = listOf(1,2,3))
                }
            }
        }

        composable(
            route = "configure/{deviceId}"
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Configure (ID: $deviceId)",
                    onBack = { navController.popBackStack() }
                )
                ConfigurationPanel(
                    deviceId = deviceId,
                    modifier = Modifier,
                    endPointNumber = 3,
                    allowCmd = true,
                    logFilePath = logFile.absolutePath
                )
            }
        }

        composable(
            route = "connect/{deviceId}"
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(
                    name = "Connect (ID: $deviceId)",
                    onBack = { navController.popBackStack() }
                )
                ConnectionPanel(
                    name = "android",
                    deviceId = deviceId,
                    endPoint = 3,
                    status = true,
                    logFilePath = logFile.absolutePath,
                    connectionRedirect = { navController.navigate("changeEndpoint/$deviceId") }
                )
            }
        }

        composable(route = "changeEndpoint/{deviceId}") { backStackEntry ->
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101319))) {
                WindowHeader(name = "Select Endpoint", onBack = { navController.popBackStack() })
                Column(modifier = Modifier.padding(16.dp)) {
                    PeripheralPanel(name="devices to toggle", modifier = Modifier, deviceManager = configReader)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppNavigationPreview() {
    AppNavigation()
}
