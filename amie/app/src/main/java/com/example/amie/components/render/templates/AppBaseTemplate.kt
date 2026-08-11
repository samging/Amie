package com.example.amie.components.render.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/**
 * The primary centralized Navigation Graph engine for the application.
 *
 * This component controls all screen states, coordinates multi-step transitions via a [NavHostController],
 * and handles disk/hardware side-effects asynchronously using Jetpack Compose lifecycles.
 */
@Composable
fun AppNavigation() {
    var activePortsMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val context = LocalContext.current
    val configFile = remember { File(context.filesDir, "componentSettings.json") }
    val configReader: DeviceManager = remember { DeviceManagerFactory.create(configFile) }

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


    NavHost(navController = navController, startDestination = "home1/") {

        composable(route = "home1/") {
            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(
                    name = "Amie App", endController = 3,
                    onBack = { /*root should have no pop stack*/ },
                    showOnBack = false,
                    addComponent = true,
                    addComponentNav = {
                        navController.navigate("addDevice/${null}")
                    })

                val configMap = configReader.getDevices()

                for ((idKey, dev) in configMap) {
                    DevicePanel(
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
                WindowHeader(
                    name = "Amie App",
                    endController = 3,
                    onBack = { navController.popBackStack() })

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

                PeripheralPanel(
                    name = "Device Port",
                    valueOf = connectionState.port,
                    hideButton = true,
                    onValueChange = { connectionState = connectionState.copy(port = it) },
                    deviceManager = configReader
                )

                PeripheralPanel(
                    name = "Device Endpoint",
                    valueOf = connectionState.deviceEndpoint,
                    hideButton = true,
                    onValueChange = { connectionState = connectionState.copy(deviceEndpoint = it) },
                    deviceManager = configReader
                )

                SystemCommit(
                    indexDevice = configReader.generateAddId(),
                    modifier = Modifier,
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

        composable(route = "manage/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            val deviceNameList = configReader.parseConfigByTargetId("name", deviceId)
            val currentDeviceName = deviceNameList.firstOrNull() ?: "Unknown Device"


            val currentDevice = configReader.getDevice(deviceId)
            val manageContent = remember { mutableStateListOf("ID: $deviceId", "Name: $currentDeviceName", "output") }

            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(
                    name = "",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )

                ManageablePage(
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
                WindowHeader(
                    name = "",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )
                val currentDevice = configReader.parseConfigByTargetId("deviceEndpoint",deviceId)

                PeripheralPanel(name="Endpoint Device", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "deviceEndpoint", deviceManager = configReader)
                Text(text="Current name: ${currentDevice.toString()}")
                val getAllNames = configReader.parseConfig("deviceEndpoint")
                val allNames: Map<Int, String> = getAllNames.withIndex().associate { it.index to it.value }

                DataEntryList(name = "a", modifier = Modifier, activeFields = allNames)
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
                WindowHeader(name = "Amie App", endController = 3,onBack = { navController.popBackStack() })
                val currentPort = configReader.parseConfigByTargetId("port",deviceId)
                PeripheralPanel(name="Serial Port", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "port", deviceManager = configReader)
                Text(text="Current port: ${currentPort.toString()}")
                DataEntryList(name = "a", modifier = Modifier, activeFields = activePortsMap, currentlyActive = listOf(0))
            }
        }


        composable(route = "scrollableDevName/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(name = "Amie App", endController = 3,onBack = { navController.popBackStack() })
                PeripheralPanel(name="Device Name", modifier = Modifier, customText = "Set", writeId = deviceId, keyQuery = "name", deviceManager = configReader)
                val currentName = configReader.parseConfigByTargetId("name",deviceId)
                Text(text="Current name: ${currentName.toString()}")
                DataEntryList(name = "a", modifier = Modifier, activeFields = mapOf(1 to "Device 1", 2 to "Device 2", 3 to "Device 3"), currentlyActive = listOf(1,2,3))
            }
        }
        composable(route = "scrollableNamePlugins/{idKey}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("idKey") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(name = "Amie App", endController = 3,onBack = { navController.popBackStack() })
                RemotePackageConsole(name="Plugins", modifier = Modifier, customText = "Search")
                IndexedSelectionList(name = "a", modifier = Modifier, activeFields = mapOf(1 to "package1.bin", 2 to "package2.bin", 3 to "package3.bin", 4 to "package4.bin"), currentlyActive = listOf(1,2,3))
            }
        }

        composable(
            route = "configure/{deviceId}"
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""

            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(
                    name = "Android (ID: $deviceId)",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )
                ConfigurationPanel(
                    name = "configure",
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

            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(
                    name = "Android (ID: $deviceId)",
                    endController = 3,
                    onBack = { navController.popBackStack() }
                )
                ConnectionPanel(
                    name = "android",
                    endController = 20,
                    endPoint = 3,
                    status = true,
                    logFilePath = logFile.absolutePath,
                    connectionRedirect = { navController.navigate("changeEndpoint/$deviceId") }
                )
            }
        }

        composable(route = "changeEndpoint/{deviceId}") { backStackEntry ->
            Column(modifier = Modifier.fillMaxSize()) {
                WindowHeader(name = "Amie App", endController = 3,onBack = { navController.popBackStack() })
                PeripheralPanel(name="devices to toggle", modifier = Modifier, deviceManager = configReader)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppNavigationPreview() {
    AppNavigation()
}
