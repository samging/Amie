package org.example.amiepackagerepository.shared.errors

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.packagestore.StateController
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/**
 * Every kind of failure a backend route can throw, and the problem document the client gets for it.
 * A small throwing controller stands in for the real ones; the last tests use the real [StateController]
 * to show the advice and a route together.
 */
@ExtendWith(MockitoExtension::class)
class ApiExceptionHandlerTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var claims: Claims

    private lateinit var mockMvc: MockMvc

    /** One route per failure the advice knows about. */
    @RestController
    class ThrowingController {
        @GetMapping("/t/refused") fun refused(): String = throw ResponseStatusException(HttpStatus.FORBIDDEN, "The token belongs to another user")
        @GetMapping("/t/no-reason") fun noReason(): String = throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        @GetMapping("/t/store-down") fun storeDown(): String =
            throw PackageStoreException("The package store at http://localhost:8088 is unreachable: Connection refused", null)
        @GetMapping("/t/store-refused") fun storeRefused(): String =
            throw PackageStoreException("The package store could not store echo.jar (507): bucket full", 507)
        @GetMapping("/t/require") fun require(): String = throw IllegalArgumentException("Invalid key")
        @GetMapping("/t/header") fun header(@RequestHeader("Authorization") authHeader: String) = "ok"
        @GetMapping("/t/other-header") fun otherHeader(@RequestHeader("X-Device") device: String) = "ok"
        @GetMapping("/t/param") fun param(@RequestParam name: String) = "ok"
        @GetMapping("/t/number") fun number(@RequestParam n: Int) = "ok"
        @PostMapping("/t/json") fun json(@RequestBody body: Map<String, String>) = "ok"
        @PostMapping("/t/part") fun part(@RequestParam("file") file: MultipartFile) = "ok"
        @PostMapping("/t/too-large") fun tooLarge(): String = throw MaxUploadSizeExceededException(100L)
        @GetMapping("/t/bug") fun bug(): String = throw IllegalStateException("a null where none was expected")
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ThrowingController(), StateController(store, userService))
            .setControllerAdvice(ApiExceptionHandler())
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    @Test
    fun `a ResponseStatusException keeps its status and its reason becomes the detail`() {
        mockMvc.perform(get("/t/refused"))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.detail").value("The token belongs to another user"))
            .andExpect(jsonPath("$.code").value("forbidden"))
            .andExpect(jsonPath("$.instance").value("/t/refused"))
            .andExpect(jsonPath("$.timestamp").isString)

        // Without a reason the title doubles as the detail, so the body is never empty
        mockMvc.perform(get("/t/no-reason"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Unauthorized"))
            .andExpect(jsonPath("$.code").value("unauthorized"))
    }

    @Test
    fun `a failing package store is 502 store_unavailable, with the store's own status when it answered`() {
        mockMvc.perform(get("/t/store-down"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_STORE_UNAVAILABLE))
            .andExpect(jsonPath("$.detail").value("The package store at http://localhost:8088 is unreachable: Connection refused"))
            .andExpect(jsonPath("$.storeStatus").doesNotExist())

        mockMvc.perform(get("/t/store-refused"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_STORE_UNAVAILABLE))
            .andExpect(jsonPath("$.storeStatus").value(507))
    }

    @Test
    fun `a require in a service is a 400 with its message`() {
        mockMvc.perform(get("/t/require"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("Invalid key"))
    }

    @Test
    fun `a missing Authorization header is 401, any other missing header 400`() {
        mockMvc.perform(get("/t/header"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Missing bearer token"))
            .andExpect(jsonPath("$.code").value("unauthorized"))

        mockMvc.perform(get("/t/other-header"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Missing header: X-Device"))
    }

    @Test
    fun `missing or malformed input is 400 and names what was wrong`() {
        mockMvc.perform(get("/t/param"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Missing parameter: name"))

        mockMvc.perform(get("/t/number").param("n", "many"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Parameter 'n' has the wrong value: many"))

        mockMvc.perform(post("/t/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.startsWith("The request body could not be read")))

        mockMvc.perform(multipart("/t/part").file(MockMultipartFile("other", "x.txt", "text/plain", "x".toByteArray())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Missing multipart field: file"))
    }

    @Test
    fun `an upload over the multipart limit is 413`() {
        mockMvc.perform(post("/t/too-large"))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("payload_too_large"))
            .andExpect(jsonPath("$.detail").value("The upload is too large (at most 100 bytes)"))
    }

    @Test
    fun `the wrong method or content type is 405 or 415`() {
        mockMvc.perform(post("/t/refused"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("method_not_allowed"))
            .andExpect(jsonPath("$.detail").value("POST is not allowed here; use GET"))

        mockMvc.perform(post("/t/json").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("unsupported_media_type"))
    }

    @Test
    fun `a bug is 500 with an error id and never the exception text`() {
        val body = mockMvc.perform(get("/t/bug"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_INTERNAL))
            .andExpect(jsonPath("$.errorId").isString)
            .andReturn().response.contentAsString
        assertTrue("a null where none was expected" !in body, "the exception message must not leak: $body")
        assertTrue(Regex("error [0-9a-f]{8}").containsMatchIn(body), body)
    }

    @Test
    fun `codeFor names every status the routes use`() {
        assertEquals("bad_request", ApiExceptionHandler.codeFor(HttpStatus.BAD_REQUEST))
        assertEquals("unauthorized", ApiExceptionHandler.codeFor(HttpStatus.UNAUTHORIZED))
        assertEquals("forbidden", ApiExceptionHandler.codeFor(HttpStatus.FORBIDDEN))
        assertEquals("not_found", ApiExceptionHandler.codeFor(HttpStatus.NOT_FOUND))
        assertEquals("conflict", ApiExceptionHandler.codeFor(HttpStatus.CONFLICT))
        assertEquals("payload_too_large", ApiExceptionHandler.codeFor(HttpStatus.PAYLOAD_TOO_LARGE))
        assertEquals("bad_gateway", ApiExceptionHandler.codeFor(HttpStatus.BAD_GATEWAY))
        assertEquals("internal_error", ApiExceptionHandler.codeFor(HttpStatus.INTERNAL_SERVER_ERROR))
        assertEquals("i_am_a_teapot", ApiExceptionHandler.codeFor(HttpStatus.I_AM_A_TEAPOT))
    }

    // ---- with a real route -------------------------------------------------------------------------

    @Test
    fun `a real route without a token answers the problem document instead of an empty 401`() {
        mockMvc.perform(get("/state"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Missing bearer token"))
            .andExpect(jsonPath("$.instance").value("/state"))

        `when`(userService.validateToken("expired")).thenReturn(null)
        mockMvc.perform(get("/state").header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Unauthorized or expired token"))
    }

    @Test
    fun `a real route in front of a dead store answers 502 with the reason`() {
        `when`(claims.subject).thenReturn("sam")
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
        `when`(store.listStates("sam")).thenThrow(PackageStoreException("The package store at http://localhost:8088 is unreachable: Connection refused"))

        mockMvc.perform(get("/state").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("bad_gateway"))
            .andExpect(jsonPath("$.detail").value("The package store at http://localhost:8088 is unreachable: Connection refused"))
    }
}
