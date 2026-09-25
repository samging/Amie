package org.example.amiepackagerepository.shared.integration.github.controller
import com.google.api.services.drive.Drive
import org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive.DriveController
import org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive.DriveService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.io.IOException


@ExtendWith(MockitoExtension::class)
class DriveControllerTest {
    @Mock
    private lateinit var driveService: Drive

    @Mock
    private lateinit var simpleService: DriveService

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var deviceService: TransactionalStatusRepository

    @InjectMocks
    private lateinit var driveController: DriveController

    private lateinit var mockMvc: MockMvc

    private val testDownloadPath = "/Downloads/"

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(driveController, "downloadPath", testDownloadPath)
        mockMvc = MockMvcBuilders.standaloneSetup(driveController).build()
    }

    @Test
    fun `listFiles - GET list-disk - returns file list string from DriveService`() {
        val expectedListString = "Available Files: \ndocument.pdf\nscript.kt\n"
        `when`(simpleService.listFiles(driveService)).thenReturn(expectedListString)

        mockMvc.perform(get("/list-disk"))
            .andExpect(status().isOk)
            .andExpect(content().string(expectedListString))

        verify(simpleService).listFiles(driveService)
    }

    @Test
    fun `listFiles - GET list-disk - returns fallback message when drive is empty`() {
        val emptyMessage = "Is null or empty."
        `when`(simpleService.listFiles(driveService)).thenReturn(emptyMessage)

        mockMvc.perform(get("/list-disk"))
            .andExpect(status().isOk)
            .andExpect(content().string(emptyMessage))

        verify(simpleService).listFiles(driveService)
    }

    @Test
    fun `downloadFile - GET download - uses default filename 'welcome-message' when param omitted`() {
        val defaultFileName = "welcome-message"
        val userHome = System.getProperty("user.home")
        val expectedDestination = File(userHome, "$testDownloadPath$defaultFileName")

        doNothing().`when`(simpleService).downloadFile(eq(driveService), eq(defaultFileName), any())

        mockMvc.perform(get("/download"))
            .andExpect(status().isOk)
            .andExpect(content().string("Success! File downloaded to ${expectedDestination.absolutePath}"))

        verify(simpleService).downloadFile(eq(driveService), eq(defaultFileName), argThat {
            absolutePath == expectedDestination.absolutePath
        })
    }

    @Test
    fun `downloadFile - GET download - downloads specified file successfully`() {
        val fileName = "custom-report.pdf"
        val userHome = System.getProperty("user.home")
        val expectedDestination = File(userHome, "$testDownloadPath$fileName")

        doNothing().`when`(simpleService).downloadFile(eq(driveService), eq(fileName), any())

        mockMvc.perform(get("/download").param("fileName", fileName))
            .andExpect(status().isOk)
            .andExpect(content().string("Success! File downloaded to ${expectedDestination.absolutePath}"))

        verify(simpleService).downloadFile(eq(driveService), eq(fileName), argThat {
            absolutePath == expectedDestination.absolutePath
        })
    }

    @Test
    fun `downloadFile - GET download - catches exception and returns failure message`() {
        val fileName = "missing-file.txt"
        val errorMessage = "File not found on Google Drive: $fileName"

        `when`(simpleService.downloadFile(eq(driveService), eq(fileName), any()))
            .doThrow(IOException(errorMessage))

        mockMvc.perform(get("/download").param("fileName", fileName))
            .andExpect(status().isOk)
            .andExpect(content().string("Failed to download file: $errorMessage"))

        verify(simpleService).downloadFile(eq(driveService), eq(fileName), any())
    }

}