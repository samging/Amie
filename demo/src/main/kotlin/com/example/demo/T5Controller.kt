package com.example.demo

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@RestController
class T5Controller(
    private val bertNerService: BertNerService,
    private val googleIntegrations: GoogleIntegrations
) {

    @PostMapping("/process-t5")
    suspend fun processT5(
        @RequestParam(required = false) input: String?,
        @RequestBody(required = false) body: String?
    ): String {
        val rawText = input ?: body ?: throw IllegalArgumentException("Input text must be provided via query param 'input' or request body")
        
        println("Incoming Message: $rawText")

        // 1. Extract the "Body" parameter from the raw Twilio payload
        val twilioBody = rawText.split("&")
            .find { it.startsWith("Body=") }
            ?.substringAfter("Body=")
            ?: rawText // Fallback to raw text if "Body=" not found

        // 2. Decode the extracted body (handles '+' and URL encoding)
        val textToProcess = URLDecoder.decode(twilioBody, StandardCharsets.UTF_8.name())

        val entities = bertNerService.extractEntities(textToProcess)

        val scenario = when {
            textToProcess.contains("cancel", ignoreCase = true) || textToProcess.contains("remove", ignoreCase = true) -> "want to remove appointment"
            textToProcess.contains("appoint", ignoreCase = true) || textToProcess.contains("schedule", ignoreCase = true) -> "want to appoint"
            else -> "not listed"
        }

        if (scenario == "not listed") return "not listed"

        val responseMessage = mutableListOf<String>()

        val requiredMetrics = mapOf(
            "name:" to "PER"
        )

        var extractedName = ""
        for ((label, entityType) in requiredMetrics) {
            val found = entities[entityType] ?: emptyList()
            if (found.isNotEmpty()) {
                val cleaned = found.flatMap { it.split(" ") }
                    .filter { it.length > 1 && !it.equals("Im", ignoreCase = true) }
                    .distinct()
                
                if (cleaned.isNotEmpty()) {
                    extractedName = cleaned.joinToString(" ")
                    responseMessage.add("$label $extractedName")
                } else {
                    responseMessage.add("$label --add more descriptiveness")
                }
            } else {
                responseMessage.add("$label --add more descriptiveness")
            }
        }

        val dateTimePattern = Regex("\\d{1,2}\\.\\d{1,2}(?:\\s+(?:at\\s+)?\\d{1,2}:\\d{2})?")
        val dates = dateTimePattern.findAll(textToProcess).map { it.value }.toList()
        
        val dateFrom = if (dates.isNotEmpty()) dates[0] else ""
        val dateTo = if (dates.size >= 2) dates[1] else ""

        if (dateFrom.isNotEmpty()) responseMessage.add("date from: $dateFrom")
        else responseMessage.add("date from: --add more descriptiveness")
        
        if (dateTo.isNotEmpty()) responseMessage.add("date to: $dateTo")
        else responseMessage.add("date to: --add more descriptiveness")

        // 4. If everything is OK and intent is "appoint", create the event
        if (scenario == "want to appoint" && extractedName.isNotEmpty() && dateFrom.isNotEmpty()) {
            try {
                // Conversion of "8.9 at 16:30" to UTC ISO Instant
                val startIso = formatToIso(dateFrom)
                val endIso = formatToIso(dateTo)

                googleIntegrations.createEvent(
                    summary = "Appointment for $extractedName",
                    description = "Created via Twilio/WhatsApp",
                    startIso = startIso,
                    endIso = endIso
                )
                println("Successfully created calendar event for $extractedName")
            } catch (e: Exception) {
                println("Failed to create calendar event: ${e.message}")
            }
        }

        return "Decision: $scenario | Info: ${responseMessage.joinToString(", ")}"
    }

    private fun formatToIso(input: String, plusHours: Long = 0): String {
        if (input.isBlank()) return ""
        
        // Use standard java.time libraries for server-grade reliability
        val formatter = DateTimeFormatterBuilder()
            .appendPattern("[d.M][dd.MM]")
            .optionalStart()
            .appendPattern("[ 'at' ][ ]")
            .appendPattern("[H:mm][HH:mm]")
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 12)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.YEAR, 2026)
            .toFormatter()

        // Parse input as local Prague time (CEST / GMT+2)
        val pragueZone = ZoneId.of("Europe/Prague")
        val dateTime = LocalDateTime.parse(input.trim(), formatter)
            .plusHours(plusHours)
        
        // Convert local Prague time to UTC for the Google API
        return dateTime.atZone(pragueZone)
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT)
    }
}
