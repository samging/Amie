package com.example.amie.util

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class DownloadServiceTest {

    @Test
    fun `fetchFilesList should return body on success`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = "file1.txt,file2.txt",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(mockEngine)

        val result = fetchFilesList(client)

        assertEquals("file1.txt,file2.txt", result)
    }

    @Test
    fun `fetchFilesList should return error on server error`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val client = HttpClient(mockEngine)

        val result = fetchFilesList(client)

        assertEquals("Server Error: Received status code 500", result)
    }

    @Test
    fun `fetchFilesList should handle generic exception`() = runBlocking {
        val mockEngine = MockEngine { _ ->
            throw Exception("Random failure")
        }
        val client = HttpClient(mockEngine)

        val result = fetchFilesList(client)

        assertEquals("Unexpected Error: Random failure", result)
    }

    @Test
    fun `fetchFilesList should handle IO exception`() = runBlocking {
        val mockEngine = MockEngine { _ ->
            throw IOException("Disk error")
        }
        val client = HttpClient(mockEngine)

        val result = fetchFilesList(client)

        assertEquals("Network Failure: Check your connection or server status. Details: Disk error", result)
    }

    @Test
    fun `fetchFilesList should handle ConnectTimeoutException`() = runBlocking {
        val mockEngine = MockEngine { _ ->
            throw ConnectTimeoutException("timeout", null)
        }
        val client = HttpClient(mockEngine)

        val result = fetchFilesList(client)

        assertEquals("Connection Timeout: Could not connect to the server at 192.168.1.116.", result)
    }
}
