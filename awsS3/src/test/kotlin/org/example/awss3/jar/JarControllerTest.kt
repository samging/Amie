package org.example.awss3.jar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class JarControllerTest {
    @Mock
    private lateinit var jarService: JarService

    private lateinit var mockMvc: MockMvc

    private val jars = listOf(
        JarDto("amie-1.3.0.jar", "releases/amie-1.3.0.jar", 20, "2026-02-01T00:00:00Z", "\"e1\"", "1.3.0"),
        JarDto("helper.jar", "tools/helper.jar", 30, "2026-01-15T00:00:00Z", "\"e2\"", null)
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(JarController(jarService, 60, 3600)).build()
    }

    @Test
    fun `GET jars returns the listing with cache headers`() {
        `when`(jarService.listJars()).thenReturn(jars)

        mockMvc.perform(get("/api/aws/jars"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
            .andExpect(header().string(HttpHeaders.ETAG, JarController.listingETag(jars)))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("amie-1.3.0.jar"))
            .andExpect(jsonPath("$[0].version").value("1.3.0"))
            .andExpect(jsonPath("$[1].size").value(30))
    }

    @Test
    fun `GET jars answers 304 when the client already has this listing`() {
        `when`(jarService.listJars()).thenReturn(jars)

        mockMvc.perform(get("/api/aws/jars").header(HttpHeaders.IF_NONE_MATCH, JarController.listingETag(jars)))
            .andExpect(status().isNotModified)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(content().string(""))
    }

    @Test
    fun `GET a jar streams the bytes as an attachment`() {
        val modified = Instant.parse("2026-02-01T00:00:00Z")
        `when`(jarService.findJar("amie-1.3.0.jar")).thenReturn(jars[0])
        `when`(jarService.maxCachedItemBytes).thenReturn(1_000_000L)
        `when`(jarService.loadJar("amie-1.3.0.jar"))
            .thenReturn(CachedJar("amie-1.3.0.jar", "releases/amie-1.3.0.jar", byteArrayOf(1, 2, 3), "\"e1\"", modified))

        mockMvc.perform(get("/api/aws/jars/amie-1.3.0.jar"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"amie-1.3.0.jar\""))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/java-archive"))
            .andExpect(header().string(HttpHeaders.ETAG, "\"e1\""))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"))
            .andExpect(header().string(HttpHeaders.LAST_MODIFIED, "Sun, 01 Feb 2026 00:00:00 GMT"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 3))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `GET a jar answers 304 for a matching If-None-Match`() {
        `when`(jarService.findJar("helper.jar")).thenReturn(jars[1])
        `when`(jarService.maxCachedItemBytes).thenReturn(1_000_000L)
        `when`(jarService.loadJar("helper.jar"))
            .thenReturn(CachedJar("helper.jar", "tools/helper.jar", byteArrayOf(9), "\"e2\"", null))

        mockMvc.perform(get("/api/aws/jars/helper.jar").header(HttpHeaders.IF_NONE_MATCH, "\"other\", \"e2\""))
            .andExpect(status().isNotModified)
            .andExpect(header().string(HttpHeaders.ETAG, "\"e2\""))
    }

    @Test
    fun `GET a jar computes an ETag when S3 gave none`() {
        `when`(jarService.findJar("helper.jar")).thenReturn(jars[1])
        `when`(jarService.maxCachedItemBytes).thenReturn(1_000_000L)
        `when`(jarService.loadJar("helper.jar"))
            .thenReturn(CachedJar("helper.jar", "tools/helper.jar", byteArrayOf(9), null, null))

        mockMvc.perform(get("/api/aws/jars/helper.jar"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ETAG, "\"${org.springframework.util.DigestUtils.md5DigestAsHex(byteArrayOf(9))}\""))
    }

    @Test
    fun `GET an unknown jar is 404 and a bad name is 400`() {
        `when`(jarService.findJar("missing.jar")).thenReturn(null)

        mockMvc.perform(get("/api/aws/jars/missing.jar")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/aws/jars/evil.txt")).andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE removes a jar, 404 for unknown and 400 for odd names`() {
        `when`(jarService.deleteJar("helper.jar")).thenReturn(true)
        `when`(jarService.deleteJar("missing.jar")).thenReturn(false)

        mockMvc.perform(delete("/api/aws/jars/helper.jar")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/aws/jars/missing.jar")).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/aws/jars/evil.txt")).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST releases stores an application build`() {
        `when`(jarService.storeRelease("amie pilot.dmg", byteArrayOf(1, 2))).thenReturn("amie-pilot.dmg")

        mockMvc.perform(
            post("/api/aws/jars/releases").param("fileName", "amie pilot.dmg")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(byteArrayOf(1, 2))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("amie-pilot.dmg"))
            .andExpect(jsonPath("$.kind").value("application"))
    }

    @Test
    fun `POST releases reports why a build was refused`() {
        `when`(jarService.storeRelease("amie.txt", byteArrayOf(1))).thenThrow(IllegalArgumentException("Release names may only contain letters"))

        mockMvc.perform(
            post("/api/aws/jars/releases").param("fileName", "amie.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(byteArrayOf(1))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string("Release names may only contain letters"))
    }

    @Test
    fun `GET a dmg build is served with its own content type`() {
        `when`(jarService.findJar("amie-pilot.dmg")).thenReturn(JarDto("amie-pilot.dmg", "releases/amie-pilot.dmg", 1, null, "\"d\"", null, "application"))
        `when`(jarService.maxCachedItemBytes).thenReturn(1_000_000L)
        `when`(jarService.loadJar("amie-pilot.dmg"))
            .thenReturn(CachedJar("amie-pilot.dmg", "releases/amie-pilot.dmg", byteArrayOf(7), "\"d\"", null))

        mockMvc.perform(get("/api/aws/jars/amie-pilot.dmg"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/x-apple-diskimage"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"amie-pilot.dmg\""))
    }

    @Test
    fun `GET a large build is streamed from S3 instead of the cache`() {
        val big = JarDto("amie-pilot-1.0.0.dmg", "releases/amie-pilot-1.0.0.dmg", 300_000_000, null, "\"big\"", "1.0.0", "application")
        `when`(jarService.findJar("amie-pilot-1.0.0.dmg")).thenReturn(big)
        `when`(jarService.maxCachedItemBytes).thenReturn(64L * 1024 * 1024)
        val stream = software.amazon.awssdk.core.ResponseInputStream(
            software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().contentLength(5L).build(),
            software.amazon.awssdk.http.AbortableInputStream.create("hello".byteInputStream())
        )
        `when`(jarService.openJar("amie-pilot-1.0.0.dmg")).thenReturn(stream)

        mockMvc.perform(get("/api/aws/jars/amie-pilot-1.0.0.dmg"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ETAG, "\"big\""))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5))
            .andExpect(content().string("hello"))

        mockMvc.perform(get("/api/aws/jars/amie-pilot-1.0.0.dmg").header(HttpHeaders.IF_NONE_MATCH, "\"big\""))
            .andExpect(status().isNotModified)
        verify(jarService, org.mockito.Mockito.never()).loadJar("amie-pilot-1.0.0.dmg")
    }

    @Test
    fun `POST refresh clears the caches`() {
        mockMvc.perform(post("/api/aws/jars/refresh")).andExpect(status().isNoContent)

        verify(jarService).invalidate()
    }

    @Test
    fun `GET cache reports what is cached`() {
        `when`(jarService.cacheInfo()).thenReturn(JarCacheInfo(true, 2, 300))

        mockMvc.perform(get("/api/aws/jars/cache"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listingCached").value(true))
            .andExpect(jsonPath("$.cachedJars").value(2))
            .andExpect(jsonPath("$.cachedBytes").value(300))
    }

    @Test
    fun `If-None-Match matching understands lists, weak tags and the wildcard`() {
        assertTrue(JarController.matches("\"a\"", "\"a\""))
        assertTrue(JarController.matches("W/\"a\"", "\"a\""))
        assertTrue(JarController.matches("\"b\", \"a\"", "\"a\""))
        assertTrue(JarController.matches("*", "\"a\""))
        assertFalse(JarController.matches("\"b\"", "\"a\""))
        assertFalse(JarController.matches(null, "\"a\""))
        assertEquals(JarController.listingETag(jars), JarController.listingETag(jars.toList()))
    }
}
