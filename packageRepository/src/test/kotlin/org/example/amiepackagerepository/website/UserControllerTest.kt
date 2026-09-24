package org.example.amiepackagerepository.website

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.example.amiepackagerepository.TestcontainersConfiguration
import org.junit.jupiter.api.MediaType
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import

@WebMvcTest(AuthController::class)
class UserControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var userService: UserService

    @MockBean
    private lateinit var simpleService: SimpleService

    @Test
    fun `register - should return 200 OK and register user successfully`() {
        val user = "sam"
        val password = "password"
        val registerRequest = """
            {
                "username" : $user 
                "password" : $password
            }
           """.trimIndent()

        val expectedResponse = "User registered successfully"

        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest
        }.andExpect {
            status { isOk() }
            content { string(expectedResponse) }
        }
        verify(userService).userExists(username)
        verify(userService).createUser(username, password)
        verify(simpleService).createUserDashboard(username = username)
        verify(userService).logActivity(username, "REGISTER", "User registered: $username")
    }

    @Test
    fun `register - should return 409 Conflict when username already exists`() {
        val user = "sam"
        val password = "password"
        val registerRequest = """
            {
                "username" : $user 
                "password" : $password
            }
           """.trimIndent()
        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest
        }.andExpect {
            status { isConflict() }
            status { reason("Username already exists") }
        }

        verify(userService).userExists(username)
        verify(userService, never()).createUser(any(), any())
        verify(simpleService, never()).createUserDashboard(any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `register - should return 400 BAD_REQUEST when usrename is missing`() {
        val requestBody = """
                "password" : "somePassword"
            """.trimIndent()

        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            status { reason("Username required") }
        }

        verify(userService, never()).userExists(any()))
     }


    @Test
    fun `register - should return 400 BAD_REQUEST when password is missing`() {
        val requestBody = """
                "password" : "somePassword"
            """.trimIndent()

        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            status { reason("Username required") }
        }
    }

    @Test
    fun `login - should return 200 OK and token for valid user credentials`() {
        // Given
        val username = "john_doe"
        val password = "secretPassword"
        val expectedToken = "jwt.user.token"

        whenever(userService.loginAsUser(username, password)).thenReturn(expectedToken)

        val requestJson = """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value(expectedToken) }
        }

        // Verify side effects
        verify(userService).loginAsUser(username, password)
        verify(userService).grantUserToken(username)
        verify(simpleService).createUserDashboard(username = username)
        verify(userService).logActivity(username, "LOGIN", "User logged in: $username")
    }

    @Test
    fun `login - should return 200 OK and guest token when user fails but username starts with guest-`() {
        // Given
        val guestUsername = "guest-1234"
        val expectedGuestToken = "jwt.guest.token"

        whenever(userService.loginAsUser(guestUsername, "")).thenReturn(null)
        whenever(userService.grantGuestToken(guestUsername)).thenReturn(expectedGuestToken)

        val requestJson = """
            {
                "username": "$guestUsername",
                "password": ""
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value(expectedGuestToken) }
        }

        verify(userService).loginAsUser(guestUsername, "")
        verify(userService).grantGuestToken(guestUsername)
        verify(simpleService).createUserDashboard(username = guestUsername)
        verify(userService).logActivity(guestUsername, "LOGIN", "User logged in: $guestUsername")
    }

    @Test
    fun `login - should return 401 UNAUTHORIZED for invalid credentials`() {
        // Given
        val username = "john_doe"
        val password = "wrongPassword"

        whenever(userService.loginAsUser(username, password)).thenReturn(null)

        val requestJson = """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isUnauthorized() }
            status { reason("Invalid credentials") }
        }

        // Verify dashboard creation and user token granting are NEVER executed on 401
        verify(userService, never()).grantUserToken(any())
        verify(simpleService, never()).createUserDashboard(any())
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `login - should return 200 OK even if dashboard creation throws exception (non-fatal)`() {
        // Given
        val username = "john_doe"
        val password = "secretPassword"
        val expectedToken = "jwt.user.token"

        whenever(userService.loginAsUser(username, password)).thenReturn(expectedToken)
        doThrow(RuntimeException("Dashboard DB down"))
            .whenever(simpleService).createUserDashboard(username = username)

        val requestJson = """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { value(expectedToken) }
        }

        // Ensure execution reached dashboard creation despite exception
        verify(simpleService).createUserDashboard(username = username)
    }
    @Test
    fun `handleError - should call writeError with provided username and error message`() {
        // Given
        val username = "john_doe"
        val error = "Invalid Password"
        val requestJson = """
            {
                "username": "$username",
                "error": "$error"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/handle-login-error") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
        }

        // Verify side effect
        verify(simpleService).writeError(username, "LoginError", error)
    }

    @Test
    fun `handleError - should fall back to default values when keys are missing`() {
        // Given
        val requestJson = "{}" // Empty map payload

        // When & Then
        mockMvc.post("/handle-login-error") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
        }

        // Verify fallback defaults ("unknown" for username, "Unknown" for error)
        verify(simpleService).writeError("unknown", "LoginError", "Unknown")
    }

    @Test
    fun `handleError - should use default username when only error is provided`() {
        // Given
        val errorMsg = "OAuth Timeout"
        val requestJson = """
            {
                "error": "$errorMsg"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/handle-login-error") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
        }

        verify(simpleService).writeError("unknown", "LoginError", errorMsg)
    }

    @Test
    fun `validateToken - should return 200 OK and welcome message when token is valid`() {
        // Given
        val rawToken = "my-valid-jwt-token"
        val authHeader = "Bearer $rawToken"
        val username = "john_doe"
        val userId = 42L

        // Mock JWT Claims behavior
        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(mockClaims["userId"]).thenReturn(userId)

        whenever(userService.validateToken(rawToken)).thenReturn(mockClaims)

        // When & Then
        mockMvc.get("/dashboard") {
            header("Authorization", authHeader)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { string("Welcome to your dashboard, john_doe (ID: 42)!") }
        }

        // Verify token stripping and activity logging
        verify(userService).validateToken(rawToken)
        verify(userService).logActivity(username, "ACCESS_DASHBOARD", "User accessed dashboard")
    }

    @Test
    fun `validateToken - should handle missing or non-numeric userId claim with fallback to -1`() {
        // Given
        val rawToken = "valid-token-no-id"
        val authHeader = "Bearer $rawToken"
        val username = "jane_doe"

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(mockClaims["userId"]).thenReturn(null) // Missing claim

        whenever(userService.validateToken(rawToken)).thenReturn(mockClaims)

        // When & Then
        mockMvc.get("/dashboard") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            content { string("Welcome to your dashboard, jane_doe (ID: -1)!") }
        }

        verify(userService).logActivity(username, "ACCESS_DASHBOARD", "User accessed dashboard")
    }

    @Test
    fun `validateToken - should return 401 UNAUTHORIZED when token validation fails`() {
        // Given
        val invalidToken = "expired-or-bad-token"
        val authHeader = "Bearer $invalidToken"

        whenever(userService.validateToken(invalidToken)).thenReturn(null)

        // When & Then
        mockMvc.get("/dashboard") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isUnauthorized() }
            status { reason("Unauthorized or expired token") }
        }

        // Verify activity logging was NEVER called when token is invalid
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `validateToken - should return 400 BAD_REQUEST when Authorization header is missing`() {
        // When & Then
        mockMvc.get("/dashboard")
            .andExpect {
                status { isBadRequest() }
            }

        verify(userService, never()).validateToken(any())
    }

    @Test
    fun `deleteUser - should return 200 OK and delete user when credentials are valid`() {
        // Given
        val username = "john_doe"
        val password = "validPassword123"

        // When & Then
        mockMvc.delete("/user") {
            param("username", username)
            param("password", password)
        }.andExpect {
            status { isOk() }
        }

        // Verify side effect
        verify(userService).deleteUser(username, password)
    }

    @Test
    fun `deleteUser - should propagate UNAUTHORIZED when service throws 401 ResponseStatusException`() {
        // Given
        val username = "john_doe"
        val wrongPassword = "wrongPassword"

        doThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password"))
            .whenever(userService).deleteUser(username, wrongPassword)

        // When & Then
        mockMvc.delete("/user") {
            param("username", username)
            param("password", wrongPassword)
        }.andExpect {
            status { isUnauthorized() }
            status { reason("Invalid password") }
        }

        verify(userService).deleteUser(username, wrongPassword)
    }

    @Test
    fun `deleteUser - should return 400 BAD_REQUEST when username parameter is missing`() {
        // When & Then
        mockMvc.delete("/user") {
            param("password", "somePassword")
        }.andExpect {
            status { isBadRequest() }
        }

        // Verify service is never called when request parameter validation fails
        verify(userService, never()).deleteUser(any(), any())
    }

    @Test
    fun `deleteUser - should return 400 BAD_REQUEST when password parameter is missing`() {
        // When & Then
        mockMvc.delete("/user") {
            param("username", "john_doe")
        }.andExpect {
            status { isBadRequest() }
        }

        verify(userService, never()).deleteUser(any(), any())
    }

    @Test
    fun `searchFiles - should return 200 OK and list of files when query finds results`() {
        // Given
        val query = "test-file"
        val expectedResults = listOf(
            mapOf("name" to "test-file.json", "path" to "uploads/test-file.json"),
            mapOf("name" to "test-file.txt", "path" to "uploads/test-file.txt")
        )

        whenever(simpleService.queryFilesGithub(query)).thenReturn(expectedResults)

        // When & Then
        mockMvc.get("/query") {
            param("query", query)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].name") { value("test-file.json") }
            jsonPath("$[1].name") { value("test-file.txt") }
        }

        verify(simpleService).queryFilesGithub(query)
    }

    @Test
    fun `searchFiles - should return 200 OK and empty list when service returns null`() {
        // Given
        val query = "non_existent"
        whenever(simpleService.queryFilesGithub(query)).thenReturn(null)

        // When & Then
        mockMvc.get("/query") {
            param("query", query)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) } // Asserts empty array []
        }

        verify(simpleService).queryFilesGithub(query)
    }

    @Test
    fun `searchFiles - should handle extension pattern query correctly`() {
        // Given
        val extensionQuery = "*.kt"
        val expectedResults = listOf(mapOf("name" to "App.kt", "path" to "src/App.kt"))

        whenever(simpleService.queryFilesGithub(extensionQuery)).thenReturn(expectedResults)

        // When & Then
        mockMvc.get("/query") {
            param("query", extensionQuery)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("App.kt") }
        }

        verify(simpleService).queryFilesGithub(extensionQuery)
    }

    @Test
    fun `searchFiles - should return 400 BAD_REQUEST when query parameter is missing`() {
        // When & Then
        mockMvc.get("/query")
            .andExpect {
                status { isBadRequest() }
            }

        verify(simpleService, never()).queryFilesGithub(any())
    }

    @Test
    fun `getUserPackages - should return 200 OK and list of package DTOs when packages exist`() {
        // Given
        val username = "john_doe"
        val expectedPackages = listOf(
            GithubContentResponseDto(name = "package-1.json", path = "uploads/john_doe/package-1.json"),
            GithubContentResponseDto(name = "package-2.zip", path = "uploads/john_doe/package-2.zip")
        )

        whenever(simpleService.listUserPackages(username)).thenReturn(expectedPackages)

        // When & Then
        mockMvc.get("/user-packages") {
            param("username", username)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].name") { value("package-1.json") }
            jsonPath("$[0].path") { value("uploads/john_doe/package-1.json") }
            jsonPath("$[1].name") { value("package-2.zip") }
            jsonPath("$[1].path") { value("uploads/john_doe/package-2.zip") }
        }

        verify(simpleService).listUserPackages(username)
    }

    @Test
    fun `getUserPackages - should return 200 OK and empty list when user has no packages`() {
        // Given
        val username = "jane_doe"
        whenever(simpleService.listUserPackages(username)).thenReturn(emptyList())

        // When & Then
        mockMvc.get("/user-packages") {
            param("username", username)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        verify(simpleService).listUserPackages(username)
    }

    @Test
    fun `getUserPackages - should return 400 BAD_REQUEST when username parameter is missing`() {
        // When & Then
        mockMvc.get("/user-packages")
            .andExpect {
                status { isBadRequest() }
            }

        verify(simpleService, never()).listUserPackages(any())
    }

    @Test
    fun `getUserPackages - should return 500 INTERNAL_SERVER_ERROR when service fails`() {
        // Given
        val username = "john_doe"
        whenever(simpleService.listUserPackages(username))
            .doThrow(RuntimeException("GitHub API Unavailable"))

        // When & Then
        mockMvc.get("/user-packages") {
            param("username", username)
        }.andExpect {
            status { isInternalServerError() }
        }

        verify(simpleService).listUserPackages(username)
    }
    @Test
    fun `ssoRegister - should return 500 when Keycloak endpoint is unreachable`() {
        // Given
        val requestJson = """
            {
                "username": "john_keycloak",
                "email": "john@example.com"
            }
        """.trimIndent()

        // When & Then
        // Attempting to hit http://192.168.1.114:8080 during unit tests will fail and trigger the catch block
        mockMvc.post("/api/sso-register") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isInternalServerError() }
        }

        // Verify activity was never logged on failure
        verify(userService, never()).logActivity(any(), any(), any())
    }

    @Test
    fun `ssoRegister - should extract fallback username 'unknown' when username key is missing in request body`() {
        // Given
        val requestJson = """
            {
                "email": "no_username@example.com"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/api/sso-register") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isInternalServerError() }
        }

        // Confirms fallback value logic ('unknown') runs safely without throwing NullPointerException
        verify(userService, never()).logActivity(any(), any(), any())
    }
    @Test
    fun `getProfile - should return 200 OK and profile entity when user exists`() {
        // Given
        val username = "john_doe"
        val expectedProfile = RestUserEntity(
            id = 100L,
            username = username,
            email = "john@example.com"
        ) // Adjust constructor arguments to match your RestUserEntity

        whenever(userService.getProfile(username)).thenReturn(expectedProfile)

        // When & Then
        mockMvc.get("/profile") {
            param("username", username)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(100) }
            jsonPath("$.username") { value(username) }
            jsonPath("$.email") { value("john@example.com") }
        }

        verify(userService).getProfile(username)
    }

    @Test
    fun `getProfile - should return 404 NOT_FOUND when profile does not exist`() {
        // Given
        val username = "non_existent_user"
        whenever(userService.getProfile(username)).thenReturn(null)

        // When & Then
        mockMvc.get("/profile") {
            param("username", username)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            status { reason("Profile not found") }
        }

        verify(userService).getProfile(username)
    }

    @Test
    fun `getProfile - should return 400 BAD_REQUEST when username parameter is missing`() {
        // When & Then
        mockMvc.get("/profile")
            .andExpect {
                status { isBadRequest() }
            }

        verify(userService, never()).getProfile(any())
    }

    @Test
    fun `updateProfile - should return 200 OK and success message when authenticated user updates own profile`() {
        // Given
        val rawToken = "valid-jwt-token"
        val authHeader = "Bearer $rawToken"
        val username = "john_doe"

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(username)
        whenever(userService.validateToken(rawToken)).thenReturn(mockClaims)

        val requestJson = """
            {
                "bio": "New bio",
                "email": "john.new@example.com"
            }
        """.trimIndent()

        // When & Then
        mockMvc.post("/profile") {
            header("Authorization", authHeader)
            param("username", username)
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
            content { string("Profile updated successfully") }
        }

        verify(userService).validateToken(rawToken)
        verify(userService).updateProfile(
            eq(username),
            argThat<ProfileUpdateDto> {
                this.bio == "New bio" && this.email == "john.new@example.com"
            }
        )
    }

    @Test
    fun `updateProfile - should return 401 UNAUTHORIZED when token is invalid or expired`() {
        // Given
        val rawToken = "invalid-token"
        val authHeader = "Bearer $rawToken"
        val username = "john_doe"

        whenever(userService.validateToken(rawToken)).thenReturn(null)

        val requestJson = """{"bio": "New bio"}"""

        // When & Then
        mockMvc.post("/profile") {
            header("Authorization", authHeader)
            param("username", username)
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(userService, never()).updateProfile(any(), any())
    }

    @Test
    fun `updateProfile - should return 403 FORBIDDEN when token subject does not match query username`() {
        // Given
        val rawToken = "hacker-token"
        val authHeader = "Bearer $rawToken"
        val loggedInUser = "attacker_user"
        val targetUser = "john_doe"

        val mockClaims = mock<Claims>()
        whenever(mockClaims.subject).thenReturn(loggedInUser)
        whenever(userService.validateToken(rawToken)).thenReturn(mockClaims)

        val requestJson = """{"bio": "Hacked bio"}"""

        // When & Then
        mockMvc.post("/profile") {
            header("Authorization", authHeader)
            param("username", targetUser)
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isForbidden() }
        }

        verify(userService).validateToken(rawToken)
        verify(userService, never()).updateProfile(any(), any())
    }

    @Test
    fun `updateProfile - should return 400 BAD_REQUEST when Authorization header is missing`() {
        val requestJson = """{"bio": "New bio"}"""

        mockMvc.post("/profile") {
            param("username", "john_doe")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isBadRequest() }
        }

        verify(userService, never()).validateToken(any())
        verify(userService, never()).updateProfile(any(), any())
    }

    @Test
    fun `updateProfile - should return 400 BAD_REQUEST when username query parameter is missing`() {
        val requestJson = """{"bio": "New bio"}"""

        mockMvc.post("/profile") {
            header("Authorization", "Bearer token123")
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isBadRequest() }
        }

        verify(userService, never()).updateProfile(any(), any())
    }
}
