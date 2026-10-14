package org.example.amiepackagerepository.shared.transactionalMiddleware.integration.github.controller

import com.google.api.services.drive.Drive
import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.packages.JarInspector
import org.example.amiepackagerepository.shared.packages.JarMirrorService
import org.example.amiepackagerepository.shared.packages.TestJars
import org.example.amiepackagerepository.shared.packages.fixtures.GoodPackage
import org.example.amiepackagerepository.shared.packages.fixtures.NoClosePackage
import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.multipart.MultipartFile

/** `POST /upload` with a real [GithubController] in front of mocked services and a real [JarInspector]. */
@ExtendWith(MockitoExtension::class)
class GithubControllerUploadTest {
    @Mock private lateinit var driveService: Drive
    @Mock private lateinit var githubService: GithubService
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var deviceService: TransactionalStatusRepository
    @Mock private lateinit var jarMirror: JarMirrorService
    @Mock private lateinit var claims: Claims

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = GithubController(driveService, githubService, userService, deviceService, JarInspector(), jarMirror)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun signedInAs(username: String) {
        `when`(claims.subject).thenReturn(username)
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
    }

    @Test
    fun `a valid package jar is stored, mirrored to S3 and logged`() {
        signedInAs("sam")
        val jar = MockMultipartFile("file", "echo-1.0.jar", "application/java-archive", TestJars.jarOf(GoodPackage::class))

        mockMvc.perform(
            multipart("/upload").file(jar)
                .param("username", "sam").param("progLanguage", "jar")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully"))

        verify(githubService).uploadFile(eqv("sam"), eqv("jar"), anyOf(MultipartFile::class.java))
        verify(jarMirror).mirror(eqv("sam"), eqv("echo-1.0.jar"), anyOf(ByteArray::class.java))
        verify(userService).logActivity("sam", "UPLOAD_PACKAGE", "Uploaded package: echo-1.0.jar")
    }

    @Test
    fun `a jar without run and close is refused with the reason`() {
        signedInAs("sam")
        val jar = MockMultipartFile("file", "broken.jar", "application/java-archive", TestJars.jarOf(NoClosePackage::class))

        mockMvc.perform(
            multipart("/upload").file(jar)
                .param("username", "sam").param("progLanguage", "jar")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string("No class in the jar has both run(String): List and close() methods"))

        verify(githubService, never()).uploadFile(anyOf(String::class.java), anyOf(String::class.java), anyOf(MultipartFile::class.java))
        verify(jarMirror, never()).mirror(anyOf(String::class.java), anyOf(String::class.java), anyOf(ByteArray::class.java))
    }

    @Test
    fun `something that is not a jar but was sent as one is refused`() {
        signedInAs("sam")
        val script = MockMultipartFile("file", "script.py", "text/plain", "print('hi')".toByteArray())

        mockMvc.perform(
            multipart("/upload").file(script)
                .param("username", "sam").param("progLanguage", "jar")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string("The file must be a .jar archive; The file is not a valid jar archive"))

        verify(githubService, never()).uploadFile(anyOf(String::class.java), anyOf(String::class.java), anyOf(MultipartFile::class.java))
    }

    @Test
    fun `other clients can still upload non-jar files such as ONNX models`() {
        signedInAs("sam")
        val model = MockMultipartFile("file", "intents.onnx", "application/octet-stream", byteArrayOf(8, 1))

        mockMvc.perform(
            multipart("/upload").file(model)
                .param("username", "sam").param("progLanguage", "onnx")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isOk)

        verify(githubService).uploadFile(eqv("sam"), eqv("onnx"), anyOf(MultipartFile::class.java))
        verify(jarMirror, never()).mirror(anyOf(String::class.java), anyOf(String::class.java), anyOf(ByteArray::class.java))
    }

    @Test
    fun `an invalid token is 401 and a token for another user is 403`() {
        `when`(userService.validateToken("bad-token")).thenReturn(null)
        val jar = MockMultipartFile("file", "echo.jar", "application/java-archive", TestJars.jarOf(GoodPackage::class))

        mockMvc.perform(
            multipart("/upload").file(jar).param("username", "sam").header("Authorization", "Bearer bad-token")
        ).andExpect(status().isUnauthorized)

        signedInAs("tom")
        mockMvc.perform(
            multipart("/upload").file(jar).param("username", "sam").header("Authorization", "Bearer valid-token")
        ).andExpect(status().isForbidden)

        verify(githubService, never()).uploadFile(anyOf(String::class.java), anyOf(String::class.java), anyOf(MultipartFile::class.java))
    }

    // Mockito matchers return null, which Kotlin refuses for non-null parameters unless typed generically.
    private fun <T> anyOf(type: Class<T>): T = ArgumentMatchers.any(type)
    private fun <T> eqv(value: T): T = ArgumentMatchers.eq(value)
}
