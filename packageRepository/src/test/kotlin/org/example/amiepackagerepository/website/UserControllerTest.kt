package org.example.amiepackagerepository.website

import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.packagestore.dto.PackageFileDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.CompletableFuture

/** The website routes of [UserController] that touch the package store. */
@ExtendWith(MockitoExtension::class)
class UserControllerTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var deviceService: TransactionalStatusRepository
    @Mock private lateinit var badgeService: org.example.amiepackagerepository.shared.badges.BadgeService
    @Mock private lateinit var adminAccess: org.example.amiepackagerepository.shared.admin.AdminAccess

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(store, userService, deviceService, badgeService, adminAccess))
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    @Test
    fun `register creates the user, their dashboard folder and an activity entry`() {
        `when`(userService.userExists("sam")).thenReturn(false)
        `when`(store.createUserDashboard("sam")).thenReturn(CompletableFuture.completedFuture(null))

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("""{"username":"sam","password":"pw"}"""))
            .andExpect(status().isOk)
            .andExpect(content().string("User registered successfully"))

        verify(userService).createUser("sam", "pw")
        verify(store).createUserDashboard("sam")
        verify(userService).logActivity("sam", "REGISTER", "User registered: sam")
    }

    @Test
    fun `register refuses a taken username with 409 and a missing password with 400`() {
        `when`(userService.userExists("sam")).thenReturn(true)

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("""{"username":"sam","password":"pw"}"""))
            .andExpect(status().isConflict)
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("""{"username":"tom"}"""))
            .andExpect(status().isBadRequest)

        verify(store, never()).createUserDashboard("sam")
    }

    @Test
    fun `handle-login-error appends to the user's error log in the store`() {
        mockMvc.perform(post("/handle-login-error").contentType(MediaType.APPLICATION_JSON)
            .content("""{"error":"Connection refused","username":"sam"}"""))
            .andExpect(status().isOk)

        verify(store).writeError("sam", "LoginError", "Connection refused")
    }

    @Test
    fun `user-packages lists what the store has, with the legacy JSON names, and hides a store outage`() {
        `when`(store.listUserPackages("sam")).thenReturn(
            listOf(PackageFileDto("echo.jar", "uploads/sam/jar/echo.jar", "\"j\"", 100, "http://d", "http://site/view?package=x", "http://d"))
        )
        `when`(store.listUserPackages("tom")).thenThrow(PackageStoreException("unreachable", null))

        mockMvc.perform(get("/user-packages").param("username", "sam"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("echo.jar"))
            .andExpect(jsonPath("$[0].path").value("uploads/sam/jar/echo.jar"))
            .andExpect(jsonPath("$[0].sha").value("\"j\""))
            .andExpect(jsonPath("$[0].size").value(100))
            .andExpect(jsonPath("$[0].download_url").value("http://d"))
            .andExpect(jsonPath("$[0].html_url").value("http://site/view?package=x"))
            .andExpect(jsonPath("$[0].type").value("file"))

        mockMvc.perform(get("/user-packages").param("username", "tom"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `query passes the store's matches through and answers an empty list when nothing matched`() {
        `when`(store.query("*jar")).thenReturn(listOf(mapOf("name" to "echo.jar", "path" to "uploads/sam/jar/echo.jar", "type" to "file", "download_url" to "http://d", "size" to 3)))
        `when`(store.query("none.pdf")).thenReturn(null)

        mockMvc.perform(get("/query").param("query", "*jar"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("echo.jar"))
            .andExpect(jsonPath("$[0].download_url").value("http://d"))
        mockMvc.perform(get("/query").param("query", "none.pdf"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }
}
