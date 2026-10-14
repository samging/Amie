package org.example.awss3.jar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import org.junit.jupiter.api.Assertions.assertThrows
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class JarServiceTest {
    @Mock
    private lateinit var s3Client: S3Client

    private val ticker = FakeTicker()
    private lateinit var service: JarService

    @BeforeEach
    fun setUp() {
        service = JarService(s3Client, "test-bucket", "", "releases/", 60, 3600, 1024 * 1024, ticker)
    }

    private fun stubListing(vararg objects: S3Object) {
        val paginator = mock(ListObjectsV2Iterable::class.java)
        @Suppress("UNCHECKED_CAST")
        val contents = mock(SdkIterable::class.java) as SdkIterable<S3Object>
        `when`(contents.iterator()).thenAnswer { objects.toMutableList().iterator() }
        `when`(paginator.contents()).thenReturn(contents)
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java))).thenReturn(paginator)
    }

    private fun s3Object(key: String, size: Long, modified: String, eTag: String = "\"etag-$key\"") = S3Object.builder()
        .key(key).size(size).lastModified(Instant.parse(modified)).eTag(eTag).build()

    @Test
    fun `lists only jars, newest first, with a version parsed from the name`() {
        stubListing(
            s3Object("uploads/sam/amie-1.2.0.jar", 10, "2026-01-01T00:00:00Z"),
            s3Object("uploads/sam/notes.txt", 5, "2026-01-05T00:00:00Z"),
            s3Object("releases/amie-1.3.0.jar", 20, "2026-02-01T00:00:00Z"),
            s3Object("tools/helper.JAR", 30, "2026-01-15T00:00:00Z")
        )

        val jars = service.listJars()

        assertEquals(listOf("amie-1.3.0.jar", "helper.JAR", "amie-1.2.0.jar"), jars.map { it.name })
        assertEquals("1.3.0", jars[0].version)
        assertNull(jars[1].version)
        assertEquals("releases/amie-1.3.0.jar", jars[0].key)
        assertEquals(20, jars[0].size)
        assertEquals("2026-02-01T00:00:00Z", jars[0].lastModified)
        val request = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2Paginator(request.capture())
        assertEquals("test-bucket", request.value.bucket())
    }

    @Test
    fun `application builds under the releases prefix are listed whatever their extension`() {
        stubListing(
            s3Object("releases/amie-pilot-1.0.0.dmg", 50, "2026-03-01T00:00:00Z"),
            s3Object("releases/amie-pilot-1.0.0.jar", 40, "2026-03-01T00:00:00Z"),
            s3Object("releases/", 0, "2026-03-01T00:00:00Z"),
            s3Object("uploads/sam/notes.txt", 5, "2026-01-05T00:00:00Z"),
            s3Object("uploads/sam/echo.jar", 10, "2026-01-01T00:00:00Z")
        )

        val jars = service.listJars()

        assertEquals(listOf("amie-pilot-1.0.0.dmg", "amie-pilot-1.0.0.jar", "echo.jar"), jars.map { it.name })
        assertEquals(listOf("application", "application", "package"), jars.map { it.kind })
        assertEquals("1.0.0", jars[0].version)
    }

    @Test
    fun `deleteJar removes the object and forgets the caches`() {
        stubListing(s3Object("uploads/sam/echo.jar", 10, "2026-01-01T00:00:00Z"))
        `when`(s3Client.deleteObject(any(DeleteObjectRequest::class.java))).thenReturn(DeleteObjectResponse.builder().build())

        assertTrue(service.deleteJar("echo.jar"))
        assertFalse(service.cacheInfo().listingCached)
        assertFalse(service.deleteJar("missing.jar"))

        val request = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)
        verify(s3Client).deleteObject(request.capture())
        assertEquals("uploads/sam/echo.jar", request.value.key())
    }

    @Test
    fun `storeRelease puts the build under the releases prefix with a URL safe name`() {
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().build())

        val stored = service.storeRelease("amie pilot 1.0.0.dmg", byteArrayOf(1, 2, 3))

        assertEquals("amie-pilot-1.0.0.dmg", stored)
        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(s3Client).putObject(request.capture(), any(RequestBody::class.java))
        assertEquals("releases/amie-pilot-1.0.0.dmg", request.value.key())
        assertEquals("application/x-apple-diskimage", request.value.contentType())
    }

    @Test
    fun `storeRelease refuses empty files, odd names and jars that are not archives`() {
        assertThrows(IllegalArgumentException::class.java) { service.storeRelease("amie.dmg", byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { service.storeRelease("amie.txt", byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { service.storeRelease("amie.jar", "nope".toByteArray()) }
        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `the newest object wins when two jars share a name`() {
        stubListing(
            s3Object("old/amie.jar", 1, "2026-01-01T00:00:00Z"),
            s3Object("new/amie.jar", 2, "2026-03-01T00:00:00Z")
        )

        val jars = service.listJars()

        assertEquals(1, jars.size)
        assertEquals("new/amie.jar", jars[0].key)
    }

    @Test
    fun `the listing is cached until it expires or is invalidated`() {
        stubListing(s3Object("amie.jar", 1, "2026-01-01T00:00:00Z"))

        service.listJars()
        service.listJars()
        verify(s3Client, times(1)).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))
        assertTrue(service.cacheInfo().listingCached)

        ticker.advance(61)
        service.listJars()
        verify(s3Client, times(2)).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))

        service.invalidate()
        assertFalse(service.cacheInfo().listingCached)
        service.listJars()
        verify(s3Client, times(3)).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))
    }

    @Test
    fun `loads jar bytes once and serves them from the cache afterwards`() {
        stubListing(s3Object("uploads/sam/amie.jar", 3, "2026-01-01T00:00:00Z"))
        val modified = Instant.parse("2026-01-01T00:00:00Z")
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().eTag("\"abc\"").lastModified(modified).build(),
                byteArrayOf(1, 2, 3)
            )
        )

        val first = service.loadJar("amie.jar")!!
        val second = service.loadJar("amie.jar")!!

        assertEquals("amie.jar", first.name)
        assertEquals("uploads/sam/amie.jar", first.key)
        assertEquals("\"abc\"", first.eTag)
        assertEquals(modified, first.lastModified)
        assertTrue(first.bytes.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(first === second)
        val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3Client, times(1)).getObjectAsBytes(request.capture())
        assertEquals("uploads/sam/amie.jar", request.value.key())
        assertEquals(JarCacheInfo(listingCached = true, cachedJars = 1, cachedBytes = 3), service.cacheInfo())
    }

    @Test
    fun `asks for an unknown jar without touching S3 objects`() {
        stubListing(s3Object("amie.jar", 1, "2026-01-01T00:00:00Z"))

        assertNull(service.loadJar("missing.jar"))
        assertNull(service.findJar("missing.jar"))
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest::class.java))
    }

    @Test
    fun `openJar streams the object without caching it`() {
        stubListing(s3Object("releases/amie-pilot-1.0.0.dmg", 900_000_000, "2026-01-01T00:00:00Z"))
        @Suppress("UNCHECKED_CAST")
        val stream = mock(ResponseInputStream::class.java) as ResponseInputStream<GetObjectResponse>
        `when`(s3Client.getObject(any(GetObjectRequest::class.java))).thenReturn(stream)

        assertTrue(service.openJar("amie-pilot-1.0.0.dmg") === stream)
        assertNull(service.openJar("missing.dmg"))
        assertEquals(0, service.cacheInfo().cachedJars)
        assertEquals(1024 * 1024 / 4, service.maxCachedItemBytes)
    }

    @Test
    fun `versionOf reads semantic versions and leaves other names alone`() {
        assertEquals("1.2.3", JarService.versionOf("amie-1.2.3.jar"))
        assertEquals("2.0.0-beta.1", JarService.versionOf("amie_2.0.0-beta.1.jar"))
        assertEquals("1.0", JarService.versionOf("v1.0.jar"))
        assertNull(JarService.versionOf("amie.jar"))
    }
}
