package org.example.amiepackagerepository.shared.packagestore

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.packagestore.dto.ErrorEntryDto
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** The error log and saved-state routes: who may read what, and what reaches the store. */
@ExtendWith(MockitoExtension::class)
class StateControllerTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var claims: Claims

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(StateController(store, userService))
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun signedInAs(username: String) {
        `when`(claims.subject).thenReturn(username)
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
    }

    @Test
    fun `a user reads and clears their own error log only`() {
        signedInAs("sam")
        `when`(store.listErrors("sam")).thenReturn(listOf(ErrorEntryDto("2026-10-14T10:00:00Z", "LoginError", "boom")))

        mockMvc.perform(get("/errors").param("username", "sam").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].error").value("LoginError"))
            .andExpect(jsonPath("$[0].message").value("boom"))
            .andExpect(jsonPath("$[0].time").value("2026-10-14T10:00:00Z"))

        mockMvc.perform(get("/errors").param("username", "tom").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/errors").param("username", "sam"))
            .andExpect(status().isBadRequest) // no Authorization header at all

        mockMvc.perform(delete("/errors").param("username", "sam").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)
        verify(store).clearErrors("sam")
        verify(store, never()).listErrors("tom")
    }

    @Test
    fun `a reported error is filed under the token's subject`() {
        signedInAs("sam")

        mockMvc.perform(post("/errors").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"error":"sandbox","message":"script failed: x"}"""))
            .andExpect(status().isNoContent)
        mockMvc.perform(post("/errors").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"error":"","message":""}"""))
            .andExpect(status().isBadRequest)

        verify(store).writeError("sam", "sandbox", "script failed: x")
    }

    @Test
    fun `an invalid token is 401 everywhere`() {
        `when`(userService.validateToken("bad")).thenReturn(null)

        mockMvc.perform(get("/errors").param("username", "sam").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/state").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized)
        mockMvc.perform(put("/state/devices").header("Authorization", "Bearer bad").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `states are listed, saved, loaded and deleted for the caller`() {
        signedInAs("sam")
        `when`(store.listStates("sam")).thenReturn(listOf("collections", "devices"))
        `when`(store.loadState("sam", "devices")).thenReturn("""{"1":{"name":"uno"}}""")
        `when`(store.loadState("sam", "missing")).thenReturn(null)
        `when`(store.deleteState("sam", "devices")).thenReturn(true)
        `when`(store.deleteState("sam", "missing")).thenReturn(false)

        mockMvc.perform(get("/state").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[1]").value("devices"))
        mockMvc.perform(put("/state/collections").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content("""{"a":[1]}"""))
            .andExpect(status().isNoContent)
        verify(store).saveState("sam", "collections", """{"a":[1]}""")
        verify(userService).logActivity("sam", "SAVE_STATE", "Saved state: collections")

        mockMvc.perform(get("/state/devices").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"1":{"name":"uno"}}"""))
        mockMvc.perform(get("/state/missing").header("Authorization", "Bearer valid-token")).andExpect(status().isNotFound)

        mockMvc.perform(delete("/state/devices").header("Authorization", "Bearer valid-token")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/state/missing").header("Authorization", "Bearer valid-token")).andExpect(status().isNotFound)
    }

    @Test
    fun `bad state names, invalid JSON and oversized states are refused`() {
        signedInAs("sam")
        `when`(store.saveState(eqv("sam"), eqv("devices"), eqv("nope"))).thenThrow(IllegalArgumentException("The state is not valid JSON"))

        mockMvc.perform(put("/state/a b").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(put("/state/devices").header("Authorization", "Bearer valid-token").contentType(MediaType.TEXT_PLAIN).content("nope"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(put("/state/devices").header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON).content("[" + "1,".repeat(600_000) + "1]"))
            .andExpect(status().isPayloadTooLarge)

        verify(store, never()).saveState(eqv("sam"), eqv("a b"), ArgumentMatchers.anyString())
    }

    @Test
    fun `a store that is down answers 502`() {
        signedInAs("sam")
        `when`(store.listStates("sam")).thenThrow(PackageStoreException("The package store at http://localhost:8088 is unreachable", null))

        mockMvc.perform(get("/state").header("Authorization", "Bearer valid-token")).andExpect(status().isBadGateway)
    }

    private fun <T> eqv(value: T): T = ArgumentMatchers.eq(value)
}
