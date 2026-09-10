package com.example.demo

import kotlinx.serialization.Serializable

@Serializable
data class GoogleCalendarDto(
    val summary: String = "default",
    val description: String = "default",
    val startIso: String = "default",
    val endIso: String = "default"
)
