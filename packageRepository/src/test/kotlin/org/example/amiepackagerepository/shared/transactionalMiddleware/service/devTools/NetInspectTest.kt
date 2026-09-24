package org.example.amiepackagerepository.shared.transactionalMiddleware.service.devTools

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@ExtendWith(MockitoExtension::class)
class NetInspectTest {
    private lateinit var mockWebServer: MockWebServer
    private val originalOut = System.out
    private lateinit var outContent: ByteArrayOutputStream

    @BeforeEach
    fun setUp(){
        mockWebServer = MockWebServer()
        mockWebServer.start()

        outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        System.setOut(originalOut)
    }

    @Test
    fun `run - should execute request with correct headers and parse successful JSON response`() {
        // Given
        val dummyDto = GithubContentResponseDto(name = "test-file.json", path = "uploads/test-file.json")
        val jsonPayload = ObjectMapper().writeValueAsString(dummyDto)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPayload)
        )

        val targetUrl = mockWebServer.url("/test-endpoint").toString()

        // When
        NetInspect.run(targetUrl)

        // Then
        // 1. Verify standard output prints the status code
        val printedOutput = outContent.toString()
        assert(printedOutput.contains("Status Code: 200"))

        // 2. Verify exact request headers sent to the server
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("application/vnd.github+json", recordedRequest.getHeader("Accept"))
        assertEquals("2022-11-28", recordedRequest.getHeader("X-GitHub-Api-Version"))
        assertNotNull(recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `run - should handle HTTP error status and print error response body`() {
        // Given
        val errorResponseBody = """{"message": "Not Found", "documentation_url": "https://docs.github.com"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorResponseBody)
        )

        val targetUrl = mockWebServer.url("/not-found").toString()

        // When
        NetInspect.run(targetUrl)

        // Then
        val printedOutput = outContent.toString()
        assert(printedOutput.contains("Status Code: 404"))
        assert(printedOutput.contains("Error Response Body: $errorResponseBody"))
    }

    @Test
    fun `run - should gracefully catch connection/network exceptions without throwing`() {
        // Given
        // Shut down server early to trigger a real network IOException
        mockWebServer.shutdown()
        val invalidUrl = "http://localhost:9999/invalid"

        // When & Then
        // Should catch exception internal to NetInspect and log it without crashing test
        NetInspect.run(invalidUrl)
    }
}