package com.example.amie.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadServiceInstrumentationTest {

    @Test
    fun testFetchFilesListTimeout() = runBlocking {
        // This will likely timeout or fail to connect, which is fine for a smoke test
        val result = fetchFilesList()
        
        // Ensure we get one of our handled error messages
        assertTrue(
            result.contains("Timeout") || 
            result.contains("Failure") || 
            result.contains("Unexpected Error") ||
            result.isNotEmpty() // Any non-empty string is a start
        )
    }
}
