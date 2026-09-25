package org.example.amiepackagerepository.shared.transactionalMiddleware.service.devTools

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.example.amiepackagerepository.shared.integration.github.dto.GithubContentResponseDto
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
        val dummyDto = GithubContentResponseDto(
            name = "test-file.json",
            path = "uploads/test-file.json",
            sha = "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
            size = 128,
            url = "https://api.github.com/repos/owner/repository/contents/uploads/test-file.json?ref=main",
            htmlUrl = "https://github.com/owner/repository/blob/main/uploads/test-file.json",
            downloadUrl = "https://raw.githubusercontent.com/owner/repository/main/uploads/test-file.json",
            type = "file"
        )

        val jsonPayload = ObjectMapper().writeValueAsString(dummyDto)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPayload)
        )

        val targetUrl = mockWebServer.url("/test-endpoint").toString()

        NetInspect.run(targetUrl)

        val printedOutput = outContent.toString()
        assert(printedOutput.contains("Status Code: 200"))

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("application/vnd.github+json", recordedRequest.getHeader("Accept"))
        assertEquals("2022-11-28", recordedRequest.getHeader("X-GitHub-Api-Version"))
        assertNotNull(recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `run - should handle HTTP error status and print error response body`() {
        val errorResponseBody = """{"message": "Not Found", "documentation_url": "https://docs.github.com"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorResponseBody)
        )

        val targetUrl = mockWebServer.url("/not-found").toString()

        NetInspect.run(targetUrl)

        val printedOutput = outContent.toString()
        assert(printedOutput.contains("Status Code: 404"))
        assert(printedOutput.contains("Error Response Body: $errorResponseBody"))
    }

    @Test
    fun `run - should gracefully catch connection/network exceptions without throwing`() {
        mockWebServer.shutdown()

        val invalidUrl = "http://localhost:9999/invalid"

        NetInspect.run(invalidUrl)
    }
}