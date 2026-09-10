package com.example.demo

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller(private val googleIntegrations: GoogleIntegrations) {
    
    @PostMapping("/add-calendar-event")
    fun addCalendarEvent(@RequestBody dto: GoogleCalendarDto) {
        googleIntegrations.createEvent(
            summary = dto.summary,
            description = dto.description,
            startIso = dto.startIso,
            endIso = dto.endIso
        )
    }

    @PostMapping("/generate-token")
    fun generateToken(): Map<String, String> {
        val token = googleIntegrations.getAccessToken()
        return mapOf("accessToken" to (token ?: "Could not generate token"))
    }
}
