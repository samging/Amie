package org.example.amiepackagerepository.shared.packagestore

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.packages.JarInspector
import org.example.amiepackagerepository.shared.packages.TestJars
import org.example.amiepackagerepository.shared.packages.fixtures.GoodPackage
import org.example.amiepackagerepository.shared.packages.fixtures.NoClosePackage
import org.example.amiepackagerepository.shared.packagestore.dto.PackageFileDto
import org.example.amiepackagerepository.shared.packagestore.dto.PackageItemDto
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObject
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObjectMeta
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Base64
import java.util.concurrent.CompletableFuture

/** The package routes with a real [PackageController] and [JarInspector] in front of a mocked store. */
@ExtendWith(MockitoExtension::class)
class PackageControllerTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var deviceService: TransactionalStatusRepository
    @Mock private lateinit var claims: Claims
    @Mock private lateinit var notes: org.example.amiepackagerepository.shared.notes.NoteService
    @Mock private lateinit var statsService: org.example.amiepackagerepository.shared.stats.StatsService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = PackageController(store, userService, deviceService, JarInspector(), notes, statsService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun signedInAs(username: String) {
        `when`(claims.subject).thenReturn(username)
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
    }

    private fun MvcResult.dispatched() = mockMvc.perform(asyncDispatch(this))

    // ---- listings ----------------------------------------------------------------------------------

    @Test
    fun `packages and its legacy alias list what is below uploads`() {
        `when`(store.listRoot()).thenReturn(
            listOf(
                PackageItemDto("sam", null, null, "dir", "uploads/sam/"),
                PackageItemDto("tool.c", "http://s3/tool.c", "\"e\"", "file", "uploads/tool.c", 12)
            )
        )

        for (route in listOf("/packages", "/list-github")) {
            mockMvc.perform(get(route))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].name").value("sam"))
                .andExpect(jsonPath("$[0].type").value("dir"))
                .andExpect(jsonPath("$[1].downloadUrl").value("http://s3/tool.c"))
                .andExpect(jsonPath("$[1].id").value("\"e\""))
        }
        mockMvc.perform(get("/list-github-metadata"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.1.endComp").value("true"))
    }

    @Test
    fun `a small file comes back with its content, a large one without, a missing one is 404`() {
        `when`(store.head("uploads/sam/py/hello.py")).thenReturn(StoredObjectMeta("hello.py", "uploads/sam/py/hello.py", 11, "\"h\"", "text/plain", "http://s3/hello.py"))
        `when`(store.get("uploads/sam/py/hello.py")).thenReturn(StoredObject("uploads/sam/py/hello.py", "print('hi')".toByteArray(), "text/plain", "\"h\""))
        `when`(store.viewUrl("uploads/sam/py/hello.py")).thenReturn("http://site/view?package=x")
        `when`(store.head("uploads/sam/jar/big.jar")).thenReturn(StoredObjectMeta("big.jar", "uploads/sam/jar/big.jar", 5L * 1024 * 1024, "\"b\"", null, "http://s3/big.jar"))
        `when`(store.head("uploads/sam/none")).thenReturn(null)

        mockMvc.perform(get("/package").param("path", "uploads/sam/py/hello.py"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("hello.py"))
            .andExpect(jsonPath("$.size").value(11))
            .andExpect(jsonPath("$.sha").value("\"h\""))
            .andExpect(jsonPath("$.download_url").value("http://s3/hello.py"))
            .andExpect(jsonPath("$.html_url").value("http://site/view?package=x"))
            .andExpect(jsonPath("$.encoding").value("base64"))
            .andExpect(jsonPath("$.content").value(Base64.getEncoder().encodeToString("print('hi')".toByteArray())))

        mockMvc.perform(get("/package").param("path", "uploads/sam/jar/big.jar"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").doesNotExist())
        verify(store, never()).get("uploads/sam/jar/big.jar")

        mockMvc.perform(get("/package").param("path", "uploads/sam/none")).andExpect(status().isNotFound)
        mockMvc.perform(get("/package").param("path", "releases/amie.dmg")).andExpect(status().isBadRequest)
    }

    // ---- uploads -----------------------------------------------------------------------------------

    @Test
    fun `a valid package jar is stored under its language and logged`() {
        signedInAs("sam")
        val jar = MockMultipartFile("file", "echo-1.0.jar", "application/java-archive", TestJars.jarOf(GoodPackage::class))

        mockMvc.perform(
            multipart("/upload").file(jar)
                .param("username", "sam").param("progLanguage", "jar")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully"))

        verify(store).upload(eqv("sam"), eqv("jar"), eqv("echo-1.0.jar"), anyOf(ByteArray::class.java), eqv("application/java-archive"))
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

        verify(store, never()).upload(anyOf(String::class.java), anyOf(String::class.java), anyOf(String::class.java), anyOf(ByteArray::class.java), ArgumentMatchers.any())
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
    }

    @Test
    fun `other file types such as ONNX models are stored as they are`() {
        signedInAs("sam")
        val model = MockMultipartFile("file", "model.onnx", "application/octet-stream", byteArrayOf(1, 2, 3))

        mockMvc.perform(
            multipart("/upload").file(model)
                .param("username", "sam").param("progLanguage", "onnx")
                .header("Authorization", "Bearer valid-token")
        )
            .andExpect(status().isOk)

        verify(store).upload(eqv("sam"), eqv("onnx"), eqv("model.onnx"), anyOf(ByteArray::class.java), eqv("application/octet-stream"))
    }

    @Test
    fun `a store that is down answers 502 and a refusal by the store 400`() {
        signedInAs("sam")
        val model = MockMultipartFile("file", "model.onnx", "application/octet-stream", byteArrayOf(1))
        `when`(store.upload(eqv("sam"), eqv("onnx"), eqv("model.onnx"), anyOf(ByteArray::class.java), ArgumentMatchers.any()))
            .thenThrow(PackageStoreException("unreachable", null))
            .thenThrow(PackageStoreException("Invalid file name", 400))

        mockMvc.perform(multipart("/upload").file(model).param("username", "sam").param("progLanguage", "onnx").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isBadGateway)
        mockMvc.perform(multipart("/upload").file(model).param("username", "sam").param("progLanguage", "onnx").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isBadRequest)
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

        verify(store, never()).upload(anyOf(String::class.java), anyOf(String::class.java), anyOf(String::class.java), anyOf(ByteArray::class.java), ArgumentMatchers.any())
    }

    // ---- removing and replacing --------------------------------------------------------------------

    @Test
    fun `DELETE upload removes the file under its language folder`() {
        signedInAs("sam")
        `when`(store.delete("uploads/sam/onnx/module.json")).thenReturn(true).thenReturn(false)

        mockMvc.perform(delete("/upload").param("filename", "module.json").param("progLanguage", "onnx").param("username", "sam").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(content().string("removed"))
        mockMvc.perform(delete("/upload").param("filename", "module.json").param("progLanguage", "onnx").param("username", "sam").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(content().string("not there"))

        verify(userService).logActivity("sam", "REMOVE_PACKAGE", "Removed package: module.json")
    }

    @Test
    fun `DELETE package by path only works on the caller's own files`() {
        signedInAs("sam")
        `when`(store.delete("uploads/sam/jar/echo.jar")).thenReturn(true)

        mockMvc.perform(delete("/package").param("path", "uploads/sam/jar/echo.jar").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)
        mockMvc.perform(delete("/package").param("path", "uploads/tom/jar/echo.jar").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/package").param("path", "uploads/sam/../tom/x").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isBadRequest)

        verify(store, never()).delete("uploads/tom/jar/echo.jar")
    }

    @Test
    fun `edit strips the user's folder from the path the website sends`() {
        signedInAs("sam")
        val file = MockMultipartFile("file", "notes.txt", "text/plain", "new".toByteArray())

        mockMvc.perform(multipart("/edit").file(file).param("filename", "uploads/sam/txt/notes.txt").param("username", "sam").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(content().string("File updated successfully"))

        verify(store).edit(eqv("sam"), eqv("txt/notes.txt"), anyOf(ByteArray::class.java), eqv("text/plain"))
    }

    // ---- device state ------------------------------------------------------------------------------

    @Test
    fun `get-device-status decodes the saved devices and update-device-status logs the activity`() {
        `when`(deviceService.repositoryDeviceController(eqv(DeviceActions.GET), eqv("sam"), ArgumentMatchers.anyMap()))
            .thenReturn(CompletableFuture.completedFuture(ResponseEntity.ok("""{"1":{"name":"uno","port":"/dev/tty1","deviceEndpoint":"http://x"}}""")))
        `when`(deviceService.repositoryDeviceController(eqv(DeviceActions.SET), eqv("sam"), ArgumentMatchers.anyMap()))
            .thenReturn(CompletableFuture.completedFuture(ResponseEntity.ok("saved")))

        mockMvc.perform(post("/get-device-status").contentType(MediaType.APPLICATION_JSON).content("""{"action":"GET","username":"sam","deviceMap":{}}"""))
            .andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.1.name").value("uno"))
            .andExpect(jsonPath("$.1.port").value("/dev/tty1"))

        mockMvc.perform(post("/update-device-status").contentType(MediaType.APPLICATION_JSON)
            .content("""{"action":"SET","username":"sam","deviceMap":{"1":{"name":"uno","port":"/dev/tty1"}}}"""))
            .andReturn().dispatched()
            .andExpect(status().isOk)
        verify(userService).logActivity("sam", "UPDATE_DEVICE_STATUS", "Updated device statuses")
        verify(deviceService).repositoryDeviceController(DeviceActions.SET, "sam", mapOf("1" to DeviceDto("uno", "/dev/tty1", null)))
    }

    @Test
    fun `get-device-status answers an empty map when the store has nothing or fails`() {
        `when`(deviceService.repositoryDeviceController(eqv(DeviceActions.GET), eqv("sam"), ArgumentMatchers.anyMap()))
            .thenReturn(CompletableFuture.completedFuture(ResponseEntity.ok("{}")))
            .thenReturn(CompletableFuture.completedFuture(ResponseEntity.status(502).body("down")))

        for (i in 1..2) {
            mockMvc.perform(post("/get-device-status").contentType(MediaType.APPLICATION_JSON).content("""{"action":"GET","username":"sam","deviceMap":{}}"""))
                .andReturn().dispatched()
                .andExpect(status().isOk)
                .andExpect(content().json("{}"))
        }
    }

    // Mockito matchers return null, which Kotlin refuses for non-null parameters unless typed generically.
    private fun <T> anyOf(type: Class<T>): T = ArgumentMatchers.any(type)
    private fun <T> eqv(value: T): T = ArgumentMatchers.eq(value)
}
