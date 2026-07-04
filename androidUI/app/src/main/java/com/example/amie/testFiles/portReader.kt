package com.example.amie.testFiles

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber


/**
 * Probes the Android host subsystem to scan, identify, and map connected hardware USB serial devices.
 *
 * This function utilizes the `usb-serial-android` library's default prober to locate supported serial
 * drivers and extract low-level hardware identifiers (Vendor ID and Product ID).
 *
 * ### Architectural & Permission Warnings
 * > `index` from the driver probe cycle, not the physical Android USB bus port configuration index.
 * >
 * > Before reading or writing to any mapped port, your application must explicitly request runtime permission
 * > via [UsbManager.requestPermission]. Failure to do so will result in a hard security crash during connection attempts.
 * >
 * > UI components to parse error strings inside data maps. It is idiomatic in Kotlin to return an empty map
 * > (`emptyMap()`) on failure and delegate error messaging strictly to the presentation layer.
 *
 * @param context The Android [Context] instance required to resolve the system-level [Context.USB_SERVICE].
 * @return A map pairing sequential index markers to a formatted vendor/product display string signature.
 */
fun readAndroidUsbPorts(context: Context): Map<Int, String> {
    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

    if (availableDrivers.isEmpty()) {
        return mapOf(0 to "No USB Serial Ports Detected")
    }

    val portMap = mutableMapOf<Int, String>()

    for ((index, driver) in availableDrivers.withIndex()) {
        val device = driver.device
        val displayName = "USB Device [Vid: ${device.vendorId}, Pid: ${device.productId}]"
        portMap[index] = displayName
    }

    return portMap
}