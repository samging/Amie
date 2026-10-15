package org.example.amiepackagerepository.shared.stats

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.errors.ApiExceptionHandler
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

/** Download counts and likes: the service's rules and the routes in front of it. */
@ExtendWith(MockitoExtension::class)
class StatsTest {
    @Mock private lateinit var stats: PackageStatRepository
    @Mock private lateinit var likes: PackageLikeRepository
    @Mock private lateinit var users: RestUserRepository
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var claims: Claims

    private lateinit var service: StatsService
    private lateinit var mockMvc: MockMvc
    private val sam = RestUserEntity(id = 1, username = "sam")
    private val jar = "uploads/sam/jar/echo.jar"

    @BeforeEach
    fun setUp() {
        service = StatsService(stats, likes, users)
        mockMvc = MockMvcBuilders.standaloneSetup(StatsController(service, userService))
            .setControllerAdvice(ApiExceptionHandler())
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    @Test
    fun `a download creates the row the first time and increments it after`() {
        `when`(stats.findByPackagePath(jar)).thenReturn(null).thenReturn(PackageStat(id = 4, packagePath = jar, downloads = 1))
        `when`(stats.save(any<PackageStat>())).thenAnswer { it.arguments[0] as PackageStat }
        assertEquals(1, service.trackDownload(jar))
        assertEquals(2, service.trackDownload(jar))
        assertThrows(IllegalArgumentException::class.java) { service.trackDownload("../etc") }
        verify(stats, org.mockito.Mockito.times(2)).save(any<PackageStat>())
    }

    @Test
    fun `a like toggles on and off and needs a known person`() {
        `when`(users.findByUsername("sam")).thenReturn(sam)
        `when`(likes.findByUserAndPackagePath(sam, jar)).thenReturn(null)
        `when`(likes.findByUser(sam)).thenReturn(emptyList())
        `when`(likes.countByPackagePath(jar)).thenReturn(1)
        assertEquals(true to 1L, service.toggleLike("sam", jar))
        val saved = argumentCaptor<PackageLike>().also { verify(likes).save(it.capture()) }.firstValue
        assertEquals(jar, saved.packagePath)
        assertEquals(sam, saved.user)

        val existing = PackageLike(id = 9, packagePath = jar, user = sam)
        `when`(likes.findByUserAndPackagePath(sam, jar)).thenReturn(existing)
        `when`(likes.countByPackagePath(jar)).thenReturn(0)
        assertEquals(false to 0L, service.toggleLike("sam", jar))
        verify(likes).delete(existing)

        `when`(users.findByUsername("ghost")).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service.toggleLike("ghost", jar) }
        assertThrows(IllegalArgumentException::class.java) { service.toggleLike("sam", "etc/passwd") }
    }

    @Test
    fun `an author's stats merge downloads and likes per package`() {
        `when`(stats.findByPackagePathStartingWith("uploads/sam/")).thenReturn(listOf(PackageStat(packagePath = jar, downloads = 12)))
        `when`(likes.findByPackagePathStartingWith("uploads/sam/")).thenReturn(listOf(
            PackageLike(packagePath = jar, user = sam), PackageLike(packagePath = "uploads/sam/onnx/m.onnx", user = sam), PackageLike(packagePath = jar, user = RestUserEntity(id = 2, username = "eve"))
        ))
        val rows = service.forAuthor("sam")
        assertEquals(listOf(PackageStatsDto(jar, 12, 2), PackageStatsDto("uploads/sam/onnx/m.onnx", 0, 1)), rows)
        assertThrows(IllegalArgumentException::class.java) { service.forAuthor("../x") }
        assertEquals(emptyList<PackageStatsDto>(), service.forPaths(emptyList()))
    }

    @Test
    fun `the routes count downloads for anyone and toggle likes for the signed-in person`() {
        `when`(stats.findByPackagePath(jar)).thenReturn(null)
        `when`(stats.save(any<PackageStat>())).thenAnswer { it.arguments[0] as PackageStat }
        mockMvc.perform(post("/stats/download").param("path", jar))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.downloads").value(1))
        mockMvc.perform(post("/stats/download").param("path", "nope"))
            .andExpect(status().isBadRequest)

        mockMvc.perform(post("/likes").param("path", jar)).andExpect(status().isUnauthorized)
        `when`(claims.subject).thenReturn("sam")
        `when`(userService.validateToken("valid-token")).thenReturn(claims)
        `when`(users.findByUsername("sam")).thenReturn(sam)
        `when`(likes.findByUserAndPackagePath(sam, jar)).thenReturn(null)
        `when`(likes.findByUser(sam)).thenReturn(emptyList())
        `when`(likes.countByPackagePath(jar)).thenReturn(3)
        mockMvc.perform(post("/likes").param("path", jar).header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.liked").value(true))
            .andExpect(jsonPath("$.likes").value(3))
        `when`(likes.findByUser(sam)).thenReturn(listOf(PackageLike(packagePath = jar, user = sam, createdAt = Instant.now())))
        mockMvc.perform(get("/likes").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value(jar))

        mockMvc.perform(get("/stats")).andExpect(status().isBadRequest)
        `when`(stats.findByPackagePathStartingWith("uploads/sam/")).thenReturn(listOf(PackageStat(packagePath = jar, downloads = 2)))
        `when`(likes.findByPackagePathStartingWith("uploads/sam/")).thenReturn(emptyList())
        mockMvc.perform(get("/stats").param("username", "sam"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].path").value(jar))
            .andExpect(jsonPath("$[0].downloads").value(2))
            .andExpect(jsonPath("$[0].likes").value(0))
        verify(likes, never()).deleteByPackagePath(any())
    }
}
