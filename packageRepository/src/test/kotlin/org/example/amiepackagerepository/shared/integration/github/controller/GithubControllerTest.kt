package org.example.amiepackagerepository.shared.integration.github.controller

import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcBuilder
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class GithubControllerTest {
    //springMock,
    @Mock
    private lateinit var controller: GitHubController // Replace with your actual controller class name

    @Mock
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    private fun setUp(){
        mockMvc = MockMvcBuilder.standaloneSetup(controller).build()
    }

    @Test
    fun `getRepositoryFiles - GET list-github - returns 200OK status and files`() {
        val item1 = GithubItemDto(name = "file1.kt", path = "uploads/file1.kt", type = "file")
        val item2 = GithubItemDto(name = "file2.kt", path = "uploads/file2.kt", type = "file")
        val files = listOf(item1, item2)

        //I assume this is what stubbing for controllers looks like..
        mockMvc.perform(get("/list-github").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("script.kt"))
            .andExpect(jsonPath("$[0].path").value("uploads/john/script.kt"))
            .andExpect(jsonPath("$[0].type").value("file"))
            .andExpect(jsonPath("$[1].name").value("config.json"))
    }

    @Test
    fun `getRepositoryFiles - GET list-github - returns 200 OK and empty array when no files exist`() {
        // Arrange
        `when`(simpleService.listFilesGithub()).thenReturn(emptyList())

        // Act & Assert
        mockMvc.perform(get("/list-github").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))

        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `getRepositoryMetadata - GET list-github-metadata - maps github items to indexed metadata map`() {
        // Arrange
        val item1 = GithubItemDto(
            name = "main.c",
            downloadUrl = "https://raw.github.com/main.c",
            id = "sha-001",
            type = "file"
        )
        val item2 = GithubItemDto(
            name = "helper.kt",
            downloadUrl = "https://raw.github.com/helper.kt",
            id = "sha-002",
            type = "file"
        )

        whenever(simpleService.listFilesGithub()).thenReturn(listOf(item1, item2))

        // Act & Assert
        mockMvc.perform(get("/list-github-metadata").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            // Index "0" assertions (C source file, endComp = "true")
            .andExpect(jsonPath("$.['0'].name").value("main.c"))
            .andExpect(jsonPath("$.['0'].downloadUrl").value("https://raw.github.com/main.c"))
            .andExpect(jsonPath("$.['0'].id").value("sha-001"))
            .andExpect(jsonPath("$.['0'].type").value("file"))
            .andExpect(jsonPath("$.['0'].endComp").value("true"))
            // Index "1" assertions (Kotlin file, endComp = "false")
            .andExpect(jsonPath("$.['1'].name").value("helper.kt"))
            .andExpect(jsonPath("$.['1'].downloadUrl").value("https://raw.github.com/helper.kt"))
            .andExpect(jsonPath("$.['1'].id").value("sha-002"))
            .andExpect(jsonPath("$.['1'].type").value("file"))
            .andExpect(jsonPath("$.['1'].endComp").value("false"))

        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `getRepositoryMetadata - GET list-github-metadata - returns empty map when no items exist`() {
        // Arrange
        whenever(simpleService.listFilesGithub()).thenReturn(emptyList())

        // Act & Assert
        mockMvc.perform(get("/list-github-metadata").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isEmpty)

        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `uploadCodeSnippet - happy path - uploads file and logs user activity on valid token`() {
        // Arrange
        val username = "john"
        val progLanguage = "kotlin"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        // Act & Assert
        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", username)
                .param("progLanguage", progLanguage)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully"))

        verify(userService).validateToken(token)
        verify(simpleService).uploadFile(eq(username), eq(progLanguage), eq(mockFile))
        verify(userService).logActivity(username, "UPLOAD_PACKAGE", "Uploaded package: script.kt")
    }

    @Test
    fun `uploadCodeSnippet - default progLanguage - uses 'unknown' when progLanguage is omitted`() {
        // Arrange
        val username = "john"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        // Act & Assert
        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", username)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully"))

        // Assert fallback defaultValue = "unknown"
        verify(simpleService).uploadFile(eq(username), eq("unknown"), eq(mockFile))
    }

    @Test
    fun `uploadCodeSnippet - invalid token - returns 401 Unauthorized when validateToken returns null`() {
        // Arrange
        val token = "invalid-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        whenever(userService.validateToken(token)).thenReturn(null)

        // Act & Assert
        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", "john")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isUnauthorized)

        verify(simpleService, never()).uploadFile(any(), any(), any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `uploadCodeSnippet - forbidden user - returns 403 Forbidden when token subject does not match request username`() {
        // Arrange
        val requestUsername = "john"
        val tokenSubject = "alice"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(tokenSubject)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        // Act & Assert
        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", requestUsername)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)

        verify(simpleService, never()).uploadFile(any(), any(), any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `uploadCodeSnippet - unexpected exception - catches Exception and returns formatted error message`() {
        // Arrange
        val username = "john"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val errorMessage = "Storage service connection timeout"

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        whenever(simpleService.uploadFile(eq(username), any(), any()))
            .thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", username)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("Error uploading file: $errorMessage"))

        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `postDeviceStatusDto - happy path - updates device status and logs user activity on HTTP 200 OK`() = runTest {
        // Arrange
        val username = "john"
        val deviceMap = mapOf("deviceA" to "ONLINE", "deviceB" to "READY")
        val jsonPayload = """
            {
                "username": "$username",
                "deviceMap": {
                    "deviceA": "ONLINE",
                    "deviceB": "READY"
                }
            }
        """.trimIndent()

        val successResponse = ResponseEntity.ok("Status Updated")
        val deferredResponse = CompletableDeferred(successResponse)

        whenever(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.SET),
                eq(username),
                eq(deviceMap)
            )
        ).thenReturn(deferredResponse)

        // Act & Assert (Handling Coroutine/Async Controller Dispatching in MockMvc)
        val mvcResult = mockMvc.perform(
            post("/update-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)

        // Verify interactions
        verify(deviceService).repositoryDeviceController(
            eq(DeviceActions.SET),
            eq(username),
            eq(deviceMap)
        )
        verify(userService).logActivity(username, "UPDATE_DEVICE_STATUS", "Updated device statuses")
    }

    @Test
    fun `postDeviceStatusDto - service returns non-200 status - logs error and skips activity logging`() = runTest {
        // Arrange
        val username = "john"
        val deviceMap = mapOf("deviceA" to "OFFLINE")
        val jsonPayload = """
            {
                "username": "$username",
                "deviceMap": {
                    "deviceA": "OFFLINE"
                }
            }
        """.trimIndent()

        val errorResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to sync status")
        val deferredResponse = CompletableDeferred(errorResponse)

        whenever(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.SET),
                eq(username),
                eq(deviceMap)
            )
        ).thenReturn(deferredResponse)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/update-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk) // Controller returns Unit (void 200 HTTP response)

        // Verify device status update was triggered but user activity was NOT logged
        verify(deviceService).repositoryDeviceController(
            eq(DeviceActions.SET),
            eq(username),
            eq(deviceMap)
        )
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `fetchDeviceStatuses - happy path with base64 content - decodes base64 and returns device map`() = runTest {
        // Arrange
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""

        // Inner JSON string representing Map<String, DeviceDto>
        val rawDeviceJson = """{"device01":{"id":"device01","status":"ONLINE"}}"""
        val base64Content = Base64.getEncoder().encodeToString(rawDeviceJson.toByteArray())

        // Simulated GitHub API response containing base64 content field
        val githubResponseBody = """{"content": "$base64Content\n"}"""
        val okResponse = ResponseEntity.ok(githubResponseBody)
        val deferredResponse = CompletableDeferred(okResponse)

        whenever(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/get-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.device01.id").value("device01"))
            .andExpect(jsonPath("$.device01.status").value("ONLINE"))

        verify(deviceService).repositoryDeviceController(eq(DeviceActions.GET), eq(username), anyOrNull())
    }

    @Test
    fun `fetchDeviceStatuses - raw json body - parses json directly when base64 content is missing`() = runTest {
        // Arrange
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""
        val rawDeviceJson = """{"device02":{"id":"device02","status":"OFFLINE"}}"""

        val okResponse = ResponseEntity.ok(rawDeviceJson)
        val deferredResponse = CompletableDeferred(okResponse)

        whenever(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/get-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.device02.id").value("device02"))
            .andExpect(jsonPath("$.device02.status").value("OFFLINE"))
    }

    @Test
    fun `fetchDeviceStatuses - failure status code - returns empty map when response is not 200 OK`() = runTest {
        // Arrange
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""

        val errorResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).body("User status file not found")
        val deferredResponse = CompletableDeferred(errorResponse)

        whenever(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/get-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(content().string("{}"))
    }

    @Test
    fun `updateCodeSnippet - happy path - edits file and logs user activity on valid token`() {
        // Arrange
        val username = "john"
        val fileName = "script.kt"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", fileName, "text/plain", "println(\"Updated\")".toByteArray())

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        // Act & Assert
        mockMvc.perform(
            multipart("/edit")
                .file(mockFile)
                .param("filename", fileName)
                .param("username", username)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File updated successfully"))

        verify(userService).validateToken(token)
        verify(simpleService).sendEdit(eq(username), eq(fileName), eq(mockFile))
        verify(userService).logActivity(username, "EDIT_PACKAGE", "Edited package: $fileName")
    }

    @Test
    fun `updateCodeSnippet - invalid token - returns 401 Unauthorized when validateToken returns null`() {
        // Arrange
        val token = "invalid-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        whenever(userService.validateToken(token)).thenReturn(null)

        // Act & Assert
        mockMvc.perform(
            multipart("/edit")
                .file(mockFile)
                .param("filename", "script.kt")
                .param("username", "john")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isUnauthorized)

        verify(simpleService, never()).sendEdit(any(), any(), any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `updateCodeSnippet - forbidden user - returns 403 Forbidden when token subject does not match request username`() {
        // Arrange
        val requestUsername = "john"
        val tokenSubject = "alice"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(tokenSubject)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        // Act & Assert
        mockMvc.perform(
            multipart("/edit")
                .file(mockFile)
                .param("filename", "script.kt")
                .param("username", requestUsername)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)

        verify(simpleService, never()).sendEdit(any(), any(), any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `updateCodeSnippet - edit exception - catches RuntimeException and returns formatted error string`() {
        // Arrange
        val username = "john"
        val fileName = "script.kt"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", fileName, "text/plain", "println()".toByteArray())
        val errorMessage = "File not found for update: uploads/john/script.kt"

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(token)).thenReturn(mockClaims)

        whenever(simpleService.sendEdit(eq(username), fileName, mockFile))
            .thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        mockMvc.perform(
            multipart("/edit")
                .file(mockFile)
                .param("filename", fileName)
                .param("username", username)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("Error updating file: $errorMessage"))

        verify(userService, never()).logActivity(any(), any(), any())
    }
}