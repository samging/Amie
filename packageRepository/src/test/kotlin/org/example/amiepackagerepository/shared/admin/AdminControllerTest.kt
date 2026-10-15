package org.example.amiepackagerepository.shared.admin

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.errors.ApiExceptionHandler
import org.example.amiepackagerepository.shared.notes.PackageNote
import org.example.amiepackagerepository.shared.notes.PackageNoteRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

/** Who gets into the admin routes (a Keycloak token with the admin role, nothing else) and what they can do. */
@ExtendWith(MockitoExtension::class)
class AdminControllerTest {
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var notes: PackageNoteRepository
    @Mock private lateinit var claims: Claims
    @Mock private lateinit var badgeService: org.example.amiepackagerepository.shared.badges.BadgeService
    @Mock private lateinit var publications: org.example.amiepackagerepository.shared.publications.PublicationService

    private lateinit var mockMvc: MockMvc
    private val root = RestUserEntity(id = 1, username = "root")
    private val eve = RestUserEntity(id = 2, username = "eve", nickname = "Evil Eve", avatar = byteArrayOf(1), avatarType = "image/png", avatarApproved = true)

    @BeforeEach
    fun setUp() {
        val access = AdminAccess(userService, "admin", "amie")
        mockMvc = MockMvcBuilders.standaloneSetup(AdminController(access, userService, notes, badgeService, publications))
            .setControllerAdvice(ApiExceptionHandler())
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    /** A verified token for [username] whose Keycloak claims carry [realmRoles] and [clientRoles]. */
    private fun tokenFor(username: String, realmRoles: List<String> = emptyList(), clientRoles: List<String> = emptyList()) {
        `when`(claims.subject).thenReturn(username)
        `when`(claims["realm_access"]).thenReturn(mapOf("roles" to realmRoles))
        `when`(claims["resource_access"]).thenReturn(mapOf("amie" to mapOf("roles" to clientRoles)))
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
    }

    private val auth = "Bearer valid-token"

    @Test
    fun `without the admin role every admin route is 403, without a token 401`() {
        mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized)

        tokenFor("eve", realmRoles = listOf("default-roles-master", "offline_access"))
        mockMvc.perform(get("/admin/users").header("Authorization", auth))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.detail").value("The admin role is required"))
        mockMvc.perform(delete("/admin/users/root").header("Authorization", auth)).andExpect(status().isForbidden)
        verify(userService, never()).adminDeleteUser(any())
    }

    @Test
    fun `a realm role or a client role named admin both open the panel`() {
        tokenFor("root", realmRoles = listOf("admin"))
        mockMvc.perform(get("/admin/me").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("root"))
            .andExpect(jsonPath("$.roles[0]").value("admin"))

        tokenFor("ops", clientRoles = listOf("admin"))
        mockMvc.perform(get("/admin/me").header("Authorization", auth)).andExpect(status().isOk)
    }

    @Test
    fun `a token this backend issued itself carries no roles and is never admin`() {
        `when`(claims.subject).thenReturn("sam")
        `when`(claims["realm_access"]).thenReturn(null)
        `when`(claims["resource_access"]).thenReturn(null)
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
        mockMvc.perform(get("/admin/users").header("Authorization", auth)).andExpect(status().isForbidden)
        assertEquals(emptySet<String>(), AdminAccess.rolesOf(claims, "amie"))
    }

    @Test
    fun `the user list carries profiles without secrets, with note counts and how they sign in`() {
        tokenFor("root", realmRoles = listOf("admin"))
        `when`(userService.allUsers()).thenReturn(listOf(eve.copy(password = "\$2a\$hash"), root))
        `when`(notes.countByAuthor(any())).thenReturn(3L)
        mockMvc.perform(get("/admin/users").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].profile.username").value("eve"))
            .andExpect(jsonPath("$[0].profile.nickname").value("Evil Eve"))
            .andExpect(jsonPath("$[0].profile.hasAvatar").value(true))
            .andExpect(jsonPath("$[0].profile.password").doesNotExist())
            .andExpect(jsonPath("$[0].notes").value(3))
            .andExpect(jsonPath("$[0].hasPassword").value(true))
            .andExpect(jsonPath("$[1].hasPassword").value(false))
    }

    @Test
    fun `latest notes are listed and any note can be removed`() {
        tokenFor("root", realmRoles = listOf("admin"))
        val note = PackageNote(id = 9, packagePath = "uploads/sam/jar/x.jar", text = "spam", author = eve)
        `when`(notes.findAll(any<Pageable>())).thenReturn(PageImpl(listOf(note)))
        mockMvc.perform(get("/admin/notes").param("limit", "50").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(9))
            .andExpect(jsonPath("$[0].author").value("eve"))

        `when`(notes.findById(9)).thenReturn(Optional.of(note))
        `when`(notes.findById(10)).thenReturn(Optional.empty())
        mockMvc.perform(delete("/admin/notes/9").header("Authorization", auth)).andExpect(status().isNoContent)
        verify(notes).delete(note)
        mockMvc.perform(delete("/admin/notes/10").header("Authorization", auth)).andExpect(status().isNotFound)
    }

    @Test
    fun `waiting pictures are listed and approved, badges granted and revoked`() {
        tokenFor("root", realmRoles = listOf("admin"))
        `when`(userService.pendingAvatars()).thenReturn(listOf(eve))
        mockMvc.perform(get("/admin/avatars/pending").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].username").value("eve"))
            .andExpect(jsonPath("$[0].contentType").value("image/png"))
            .andExpect(jsonPath("$[0].bytes").value(1))
        `when`(userService.approveAvatar("eve")).thenReturn(true).thenReturn(false)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/eve/avatar/approve").header("Authorization", auth)).andExpect(status().isNoContent)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/eve/avatar/approve").header("Authorization", auth)).andExpect(status().isNotFound)

        mockMvc.perform(get("/admin/badges").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].key").value("verified"))
        `when`(userService.getProfile("eve")).thenReturn(eve)
        `when`(badgeService.grant(eve, "helper", "root")).thenReturn(true)
        `when`(badgeService.of(eve)).thenReturn(listOf("helper"))
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/eve/badges/helper").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.granted").value(true))
            .andExpect(jsonPath("$.badges[0]").value("helper"))
        `when`(badgeService.grant(eve, "nope", "root")).thenThrow(IllegalArgumentException("No such badge: nope"))
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/eve/badges/nope").header("Authorization", auth))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("No such badge: nope"))
        `when`(badgeService.revoke(eve, "helper")).thenReturn(true)
        mockMvc.perform(delete("/admin/users/eve/badges/helper").header("Authorization", auth)).andExpect(status().isNoContent)
        `when`(userService.getProfile("ghost")).thenReturn(null)
        mockMvc.perform(delete("/admin/users/ghost/badges/helper").header("Authorization", auth)).andExpect(status().isNotFound)
    }

    @Test
    fun `nicknames, pictures and accounts can be taken away, but not the admin's own account`() {
        tokenFor("root", realmRoles = listOf("admin"))
        `when`(userService.adminClearNickname("eve")).thenReturn(true)
        `when`(userService.getProfile("eve")).thenReturn(eve)
        `when`(userService.clearAvatar("eve")).thenReturn(true)
        `when`(userService.adminDeleteUser("eve")).thenReturn(true)
        `when`(userService.adminDeleteUser("ghost")).thenReturn(false)

        mockMvc.perform(delete("/admin/users/eve/nickname").header("Authorization", auth)).andExpect(status().isNoContent)
        mockMvc.perform(delete("/admin/users/eve/avatar").header("Authorization", auth)).andExpect(status().isNoContent)
        mockMvc.perform(delete("/admin/users/eve").header("Authorization", auth)).andExpect(status().isNoContent)
        mockMvc.perform(delete("/admin/users/ghost").header("Authorization", auth)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/admin/users/root").header("Authorization", auth))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("You cannot remove your own account here"))
        verify(userService, never()).adminDeleteUser("root")
    }
}
