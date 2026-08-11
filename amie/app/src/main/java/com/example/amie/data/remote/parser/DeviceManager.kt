package com.example.amie.data.remote.parser

import java.io.File

interface DeviceManager {
    fun load(configFile: File)
    fun load()
    fun count(): Int
    fun getDevice(deviceKey: String): Device?
    fun getDevices(): Map<String, Device>


    fun generateAddId(): String
    fun parseConfig(labelRead: String): List<String>
    fun parseConfigByTargetId(labelRead: String, targetId: String): List<String>
    fun deleteById(idLabel: String)
    fun setSession(username: String)
}
