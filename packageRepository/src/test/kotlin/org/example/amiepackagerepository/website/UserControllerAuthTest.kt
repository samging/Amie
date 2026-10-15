package org.example.amiepackagerepository.website

import io.jsonwebtoken.Jwts
import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.CompletableFuture

/** The sign-in side of [UserController]: login (account, guest, refused), the token-guarded routes and the profile. */
@ExtendWith(MockitoExtension::class)
class UserControllerAuthTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var deviceService: TransactionalStatusRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(store, userService, deviceService))
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun claims(subject: String, userId: Long = 7L) = Jwts.claims().subject(subject).add("userId", userId).build()

    @Test
    fun `login with the right password answers the token and prepares the dashboard`() {
        `when`(userService.loginAsUser("sam", "pw")).thenReturn("tok-123")
        `when`(store.createUserDashboard("sam")).thenReturn(CompletableFuture.completedFuture(null))

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""{"username":"sam","password":"pw"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("tok-123"))

        verify(userService).grantUserToken("sam")
        verify(store).createUserDashboard("sam")
        verify(userService).logActivity("sam", "LOGIN", "User logged in: sam")
    }

    @Test
    fun `a guest name with no password gets a guest token`() {
        `when`(userService.loginAsUser("guest-desktop-user", "")).thenReturn(null)
        `when`(userService.grantGuestToken("guest-desktop-user")).thenReturn("guest-tok")
        `when`(store.createUserDashboard("guest-desktop-user")).thenReturn(CompletableFuture.completedFuture(null))

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""{"username":"guest-desktop-user"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("guest-tok"))
    }

    @Test
    fun `a wrong password is 401 and nothing is logged or created`() {
        `when`(userService.loginAsUser("sam", "nope")).thenReturn(null)

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""{"username":"sam","password":"nope"}"""))
            .andExpect(status().isUnauthorized)

        verify(userService, never()).grantUserToken("sam")
        verify(userService, never()).grantGuestToken("sam")
        verify(store, never()).createUserDashboard("sam")
    }

    @Test
    fun `a dashboard problem does not fail the login`() {
        `when`(userService.loginAsUser("sam", "pw")).thenReturn("tok")
        `when`(store.createUserDashboard("sam")).thenThrow(RuntimeException("store down"))

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""{"username":"sam","password":"pw"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("tok"))
    }

    @Test
    fun `dashboard greets the token's subject and refuses a bad token`() {
        `when`(userService.validateToken("good")).thenReturn(claims("sam", 7L))
        `when`(userService.validateToken("bad")).thenReturn(null)

        mockMvc.perform(get("/dashboard").header("Authorization", "Bearer good"))
            .andExpect(status().isOk)
            .andExpect(content().string("Welcome to your dashboard, sam (ID: 7)!"))
        verify(userService).logActivity("sam", "ACCESS_DASHBOARD", "User accessed dashboard")

        mockMvc.perform(get("/dashboard").header("Authorization", "Bearer bad"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me creates the backend user for a Keycloak token and answers the profile without the password`() {
        `when`(userService.validateToken("kc")).thenReturn(claims("sam", -1L))
        `when`(userService.getProfile("sam")).thenReturn(RestUserEntity(id = 3, username = "sam", password = "HASH", fullName = "Sam", email = "s@x", bio = "hi"))

        mockMvc.perform(get("/me").header("Authorization", "Bearer kc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.username").value("sam"))
            .andExpect(jsonPath("$.fullName").value("Sam"))
            .andExpect(jsonPath("$.password").doesNotExist())

        verify(userService).ensureUser("sam")

        `when`(userService.validateToken("bad")).thenReturn(null)
        mockMvc.perform(get("/me").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `profile is read by name and 404 for an unknown user`() {
        `when`(userService.getProfile("sam")).thenReturn(RestUserEntity(id = 3, username = "sam", password = "HASH"))
        `when`(userService.getProfile("nobody")).thenReturn(null)

        mockMvc.perform(get("/profile").param("username", "sam"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("sam"))
        mockMvc.perform(get("/profile").param("username", "nobody"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a profile update needs the user's own token`() {
        `when`(userService.validateToken("sam-token")).thenReturn(claims("sam"))
        `when`(userService.validateToken("tom-token")).thenReturn(claims("tom"))
        val body = """{"fullName":"Sam","email":"s@x","bio":"hi"}"""

        mockMvc.perform(post("/profile").param("username", "sam").header("Authorization", "Bearer sam-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(content().string("Profile updated successfully"))
        verify(userService).updateProfile(org.mockito.kotlin.eq("sam"), org.mockito.kotlin.any())

        mockMvc.perform(post("/profile").param("username", "sam").header("Authorization", "Bearer tom-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `delete user hands the name and password to the service`() {
        mockMvc.perform(delete("/user").param("username", "sam").param("password", "pw"))
            .andExpect(status().isOk)
        verify(userService).deleteUser("sam", "pw")
    }
}
