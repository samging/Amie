package org.example.amiepackagerepository.shared.integration.github.controller

import io.jsonwebtoken.Claims
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull.content
import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.integration.github.controller.GithubController
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcBuilder
import kotlin.test.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.system.SystemProperties.get
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.request
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.PageAttributes
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import java.util.Base64


@ExtendWith(MockitoExtension::class)
class GithubControllerTest {
    //springMock,
    @Mock
    private lateinit var controller: GithubController

    @Mock
    private lateinit var mockMvc: MockMvc

    @Mock
    private lateinit var simpleService: GithubService

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var deviceService: TransactionalStatusRepository

    @BeforeEach
    open fun setUp(){
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
        `when`(simpleService.listFilesGithub()).thenReturn(emptyList())

        mockMvc.perform(get("/list-github").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))

        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `getRepositoryMetadata - GET list-github-metadata - maps github items to indexed metadata map`() {
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

        `when`(simpleService.listFilesGithub()).thenReturn(listOf(item1, item2))

        //[Human note]
        //I think this might be broken
        mockMvc.perform(get("/list-github-metadata").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.['0'].name").value("main.c"))
            .andExpect(jsonPath("$.['0'].downloadUrl").value("https://raw.github.com/main.c"))
            .andExpect(jsonPath("$.['0'].id").value("sha-001"))
            .andExpect(jsonPath("$.['0'].type").value("file"))
            .andExpect(jsonPath("$.['0'].endComp").value("true"))
            .andExpect(jsonPath("$.['1'].name").value("helper.kt"))
            .andExpect(jsonPath("$.['1'].downloadUrl").value("https://raw.github.com/helper.kt"))
            .andExpect(jsonPath("$.['1'].id").value("sha-002"))
            .andExpect(jsonPath("$.['1'].type").value("file"))
            .andExpect(jsonPath("$.['1'].endComp").value("false"))

        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `getRepositoryMetadata - GET list-github-metadata - returns empty map when no items exist`() {
        `when`(simpleService.listFilesGithub()).thenReturn(emptyList())

        mockMvc.perform(get("/list-github-metadata")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isEmpty)
        verify(simpleService).listFilesGithub()
    }

    @Test
    fun `uploadCodeSnippet - happy path - uploads file and logs user activity on valid token`() {
        val username = "john"
        val progLanguage = "kotlin"
        val token = "valid-jwt-token"
        val mockFile =
            MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(username)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

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
        val username = "john"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(username)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

        mockMvc.perform(
            multipart("/upload")
                .file(mockFile)
                .param("username", username)
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully"))

        verify(simpleService).uploadFile(eq(username),
            eq("unknown"),
            eq(mockFile))
    }

    @Test
    fun `uploadCodeSnippet - invalid token - returns 401 Unauthorized when validateToken returns null`() {
        val token = "invalid-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        `when`(userService.validateToken(token)).thenReturn(null)

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
        val requestUsername = "john"
        val tokenSubject = "alice"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(tokenSubject)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

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
        val username = "john"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val errorMessage = "Storage service connection timeout"

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(username)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

        `when`(simpleService.uploadFile(eq(username), any(), any()))
            .thenThrow(RuntimeException(errorMessage))

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

        `when`(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.SET),
                eq(username),
                eq(deviceMap)
            )
        ).thenReturn(deferredResponse)

        val mvcResult = mockMvc.perform(
            post("/update-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)

        verify(deviceService).repositoryDeviceController(
            eq(DeviceActions.SET),
            eq(username),
            eq(deviceMap)
        )
        verify(userService).logActivity(username, "UPDATE_DEVICE_STATUS", "Updated device statuses")
    }

    @Test
    fun `postDeviceStatusDto - service returns non-200 status - logs error and skips activity logging`() = runTest {
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

        `when`(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.SET),
                eq(username),
                eq(deviceMap)
            )
        ).thenReturn(deferredResponse)

        val mvcResult = mockMvc.perform(
            post("/update-device-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)

        verify(deviceService).repositoryDeviceController(
            eq(DeviceActions.SET),
            eq(username),
            eq(deviceMap)
        )

        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `fetchDeviceStatuses - happy path with base64 content - decodes base64 and returns device map`() = runTest {
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""

        val rawDeviceJson = """{"device01":{"id":"device01","status":"ONLINE"}}"""
        val base64Content = Base64.getEncoder().encodeToString(rawDeviceJson.toByteArray())

        val githubResponseBody = """{"content": "$base64Content\n"}"""
        val okResponse = ResponseEntity.ok(githubResponseBody)
        val deferredResponse = CompletableDeferred(okResponse)

        `when`(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

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
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""
        val rawDeviceJson = """{"device02":{"id":"device02","status":"OFFLINE"}}"""

        val okResponse = ResponseEntity.ok(rawDeviceJson)
        val deferredResponse = CompletableDeferred(okResponse)

        `when`(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

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
        val username = "john"
        val requestJson = """{"username": "$username", "deviceMap": {}}"""

        val errorResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).body("User status file not found")
        val deferredResponse = CompletableDeferred(errorResponse)

        `when`(
            deviceService.repositoryDeviceController(
                eq(DeviceActions.GET),
                eq(username),
                anyOrNull()
            )
        ).thenReturn(deferredResponse)

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
        val username = "john"
        val fileName = "script.kt"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", fileName, "text/plain", "println(\"Updated\")".toByteArray())

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(username)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

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
        val token = "invalid-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        `when`(userService.validateToken(token)).thenReturn(null)

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
        val requestUsername = "john"
        val tokenSubject = "alice"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(tokenSubject)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

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
        val username = "john"
        val fileName = "script.kt"
        val token = "valid-jwt-token"
        val mockFile = MockMultipartFile("file", fileName, "text/plain", "println()".toByteArray())
        val errorMessage = "File not found for update: uploads/john/script.kt"

        val mockClaims = mock<Claims>()
        `when`(mockClaims.subject).thenReturn(username)
        `when`(userService.validateToken(token)).thenReturn(mockClaims)

        `when`(simpleService.sendEdit(eq(username), eq(fileName), eq(mockFile)))
            .thenThrow(RuntimeException(errorMessage))

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