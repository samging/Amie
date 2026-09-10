package com.example.demo

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.client.util.DateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream

@Service
class GoogleIntegrations(
    // Update this to your personal email address
    private val calendarId: String = "samfuxm@gmail.com"
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val credentialsPath = "/Users/samuel/Downloads/amieIAM.json"

    private val credentials by lazy {
        val credentialsFile = File(credentialsPath)
        if (credentialsFile.exists()) {
            GoogleCredentials.fromStream(FileInputStream(credentialsFile))
                .createScoped(listOf("https://www.googleapis.com/auth/calendar"))
        } else {
            null
        }
    }

    private val service: Calendar? by lazy {
        val creds = credentials ?: return@lazy null
        Calendar.Builder(
            httpTransport,
            jsonFactory,
            HttpCredentialsAdapter(creds)
        ).setApplicationName("Amie Packages").build()
    }

    fun getAccessToken(): String? {
        val creds = credentials ?: return null
        creds.refreshIfExpired()
        return creds.accessToken?.tokenValue
    }

    fun createEvent(
        summary: String = "default",
        description: String = "default",
        startIso: String = "2024-05-01T10:00:00Z",
        endIso: String = "2024-05-01T11:00:00Z"
    ): Any? {
        val event = Event().apply {
            this.summary = summary
            this.description = description
            this.start = EventDateTime().setDateTime(DateTime(startIso))
            this.end = EventDateTime().setDateTime(DateTime(endIso))
        }

        val result = service?.events()?.insert(calendarId, event)?.execute()
        println("Created event successfully!")
        println("Event Summary: ${result?.summary}")
        println("Event ID: ${result?.id}")
        println("Event HTML Link: ${result?.htmlLink}")
        println("Calendar ID used: $calendarId")
        
        return result
    }
}
