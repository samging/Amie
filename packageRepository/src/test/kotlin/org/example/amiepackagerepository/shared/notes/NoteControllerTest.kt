package org.example.amiepackagerepository.shared.notes

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.errors.ApiExceptionHandler
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** The note routes and the status each refusal gets. */
@ExtendWith(MockitoExtension::class)
class NoteControllerTest {
    @Mock private lateinit var notes: NoteService
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var claims: Claims

    private lateinit var mockMvc: MockMvc
    private val path = "uploads/sam/jar/echo.jar"
    private val dto = NoteDto(7, path, "eve", "", false, "Works on my Uno!", "2026-10-15T10:00:00Z")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(NoteController(notes, userService))
            .setControllerAdvice(ApiExceptionHandler())
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun signedInAs(username: String) {
        `when`(claims.subject).thenReturn(username)
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
    }

    @Test
    fun `anyone can read the notes of a package`() {
        `when`(notes.list(path)).thenReturn(listOf(dto))
        mockMvc.perform(get("/notes").param("path", path))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].author").value("eve"))
            .andExpect(jsonPath("$[0].text").value("Works on my Uno!"))
    }

    @Test
    fun `posting needs a token and answers 201 with the stored note`() {
        mockMvc.perform(post("/notes").contentType(MediaType.APPLICATION_JSON).content("""{"path":"$path","text":"hi"}"""))
            .andExpect(status().isUnauthorized)

        signedInAs("eve")
        `when`(notes.add("eve", path, "hi")).thenReturn(dto)
        mockMvc.perform(post("/notes").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content("""{"path":"$path","text":"hi"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(7))
    }

    @Test
    fun `each refusal has its status - empty 400, missing package 404, too fast 429, store down 502`() {
        signedInAs("eve")
        `when`(notes.add("eve", path, "")).thenThrow(IllegalArgumentException("The note is empty"))
        `when`(notes.add("eve", path, "gone")).thenThrow(NoSuchElementException("No such package"))
        `when`(notes.add("eve", path, "fast")).thenThrow(TooManyNotesException("At most 10 notes a minute; try again shortly"))
        `when`(notes.add("eve", path, "down")).thenThrow(PackageStoreException("The package store at http://localhost:8088 is unreachable"))
        val auth = "Bearer valid-token"
        fun body(text: String) = """{"path":"$path","text":"$text"}"""
        mockMvc.perform(post("/notes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("")))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.detail").value("The note is empty"))
        mockMvc.perform(post("/notes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("gone")))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("not_found"))
        mockMvc.perform(post("/notes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("fast")))
            .andExpect(status().isTooManyRequests).andExpect(jsonPath("$.detail").value("At most 10 notes a minute; try again shortly"))
        mockMvc.perform(post("/notes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body("down")))
            .andExpect(status().isBadGateway)
    }

    @Test
    fun `deleting answers 204, 404 for an unknown note and 403 for someone else's`() {
        signedInAs("eve")
        `when`(notes.delete("eve", 7)).thenReturn(true)
        `when`(notes.delete("eve", 8)).thenReturn(false)
        `when`(notes.delete("eve", 9)).thenThrow(SecurityException("Only the author or the package's owner can remove this note"))
        mockMvc.perform(delete("/notes/7").header("Authorization", "Bearer valid-token")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/notes/8").header("Authorization", "Bearer valid-token")).andExpect(status().isNotFound)
        mockMvc.perform(delete("/notes/9").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.detail").value("Only the author or the package's owner can remove this note"))
        mockMvc.perform(delete("/notes/7")).andExpect(status().isUnauthorized)
    }
}
