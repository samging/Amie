package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CommonPrefix
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable

/**
 * Unit tests for [S3Service] against a mocked [S3Client]. Each test follows arrange / act / assert and
 * checks both what the service returns and what it asked S3 for.
 */
@ExtendWith(MockitoExtension::class)
class S3ServiceTest {
    @Mock
    private lateinit var s3Client: S3Client

    @Mock
    private lateinit var file: MultipartFile

    private lateinit var service: S3Service

    @BeforeEach
    fun setUp() {
        service = S3Service(s3Client, ObjectMapper(), BUCKET, PUBLIC_URL)
    }

    private fun stubPut(eTag: String) {
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().eTag(eTag).build())
    }

    private fun capturedPut(): Pair<PutObjectRequest, RequestBody> {
        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        val body = ArgumentCaptor.forClass(RequestBody::class.java)
        verify(s3Client).putObject(request.capture(), body.capture())
        return request.value to body.value
    }

    private fun RequestBody.bytes(): ByteArray = contentStreamProvider().newStream().readBytes()

    private fun notFound(): S3Exception = S3Exception.builder().statusCode(404).message("Not Found").build() as S3Exception

    /** A paginator whose `contents()` yields the given objects and whose pages are the given pages. */
    private fun stubPaginator(objects: List<S3Object> = emptyList(), pages: List<ListObjectsV2Response> = emptyList()) {
        val paginator = mock(ListObjectsV2Iterable::class.java)
        @Suppress("UNCHECKED_CAST")
        val contents = mock(SdkIterable::class.java) as SdkIterable<S3Object>
        lenient().`when`(contents.iterator()).thenAnswer { objects.toMutableList().iterator() }
        lenient().`when`(paginator.contents()).thenReturn(contents)
        lenient().`when`(paginator.iterator()).thenAnswer { pages.toMutableList().iterator() }
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java))).thenReturn(paginator)
    }

    // ---- downloads -------------------------------------------------------------------------------------

    @Test
    fun `downloadAmie streams the requested object from the requested bucket`() = runTest {
        @Suppress("UNCHECKED_CAST")
        val stream = mock(ResponseInputStream::class.java) as ResponseInputStream<GetObjectResponse>
        `when`(s3Client.getObject(any(GetObjectRequest::class.java))).thenReturn(stream)

        val result = service.downloadAmie("test-file.txt", "other-bucket")

        assertSame(stream, result)
        val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3Client).getObject(request.capture())
        assertEquals("other-bucket", request.value.bucket())
        assertEquals("test-file.txt", request.value.key())
    }

    @Test
    fun `listBucketObjects returns the keys from the S3 response`() = runTest {
        `when`(s3Client.listObjects(any(ListObjectsRequest::class.java))).thenReturn(
            ListObjectsResponse.builder()
                .contents(S3Object.builder().key("uploads/sam/a.txt").build(), S3Object.builder().key("uploads/sam/b.txt").build())
                .build()
        )

        val keys = service.listBucketObjects()

        assertEquals(listOf("uploads/sam/a.txt", "uploads/sam/b.txt"), keys)
        val request = ArgumentCaptor.forClass(ListObjectsRequest::class.java)
        verify(s3Client).listObjects(request.capture())
        assertEquals(BUCKET, request.value.bucket())
    }

    // ---- uploads ---------------------------------------------------------------------------------------

    @Test
    fun `uploadFile stores the multipart under its original name`() = runTest {
        `when`(file.originalFilename).thenReturn("test.txt")
        `when`(file.inputStream).thenReturn("hello".byteInputStream())
        `when`(file.size).thenReturn(5L)
        stubPut("\"1234567890abcdef\"")

        val result = service.uploadFile(file)

        assertEquals("File uploaded successfully. ETag: \"1234567890abcdef\"", result)
        val (request, body) = capturedPut()
        assertEquals(BUCKET, request.bucket())
        assertEquals("test.txt", request.key())
        assertEquals("test", request.metadata()["myVal"])
        assertArrayEquals("hello".toByteArray(), body.bytes())
    }

    @Test
    fun `uploadFile wraps S3 failures in a RuntimeException`() {
        `when`(file.originalFilename).thenReturn("test.txt")
        `when`(file.inputStream).thenReturn("hello".byteInputStream())
        `when`(file.size).thenReturn(5L)
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(RuntimeException("AWS Service Error"))

        val exception = assertThrows(RuntimeException::class.java) { runBlocking { service.uploadFile(file) } }

        assertEquals("S3 Upload Failed: AWS Service Error", exception.message)
    }

    @Test
    fun `uploadFileData stores bytes at the user upload key`() = runTest {
        stubPut("raw-etag")

        val result = service.uploadFileData("sam", "notes.txt", "hello".toByteArray())

        assertEquals("File uploaded successfully. ETag: raw-etag", result)
        val (request, body) = capturedPut()
        assertEquals(BUCKET, request.bucket())
        assertEquals("uploads/sam/notes.txt", request.key())
        assertArrayEquals("hello".toByteArray(), body.bytes())
    }

    @Test
    fun `uploadFileData rejects a blank username before calling S3`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.uploadFileData("", "notes.txt", byteArrayOf()) }
        }

        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `uploadFileData rejects a blank file name before calling S3`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.uploadFileData("sam", " ", byteArrayOf()) }
        }

        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `sendEdit replaces the object after S3 confirms it exists`() = runTest {
        `when`(file.contentType).thenReturn("text/plain")
        `when`(file.size).thenReturn(5L)
        `when`(file.inputStream).thenReturn("hello".byteInputStream())
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())
        stubPut("edit-etag")

        val result = service.sendEdit("sam", "notes.txt", file)

        assertEquals("File updated successfully. ETag: edit-etag", result)
        val head = ArgumentCaptor.forClass(HeadObjectRequest::class.java)
        verify(s3Client).headObject(head.capture())
        assertEquals("uploads/sam/notes.txt", head.value.key())
        val (request, _) = capturedPut()
        assertEquals("uploads/sam/notes.txt", request.key())
        assertEquals("text/plain", request.contentType())
    }

    @Test
    fun `sendEdit without a username edits below the uploads root`() = runTest {
        `when`(file.size).thenReturn(5L)
        `when`(file.inputStream).thenReturn("hello".byteInputStream())
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())
        stubPut("root-etag")

        service.sendEdit("", "notes.txt", file)

        val (request, _) = capturedPut()
        assertEquals("uploads/notes.txt", request.key())
    }

    @Test
    fun `sendEdit fails when the object does not exist`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenThrow(notFound())

        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking { service.sendEdit("sam", "missing.txt", file) }
        }

        assertEquals("S3 update failed for uploads/sam/missing.txt", exception.message)
        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `writeError appends a JSON line to the user's error log`() = runTest {
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "{\"time\":\"2026-10-14T10:00:00Z\",\"error\":\"old\",\"message\":\"first\"}\n".toByteArray()))
        stubPut("log")

        service.writeError("sam", "test-error", "test-message")

        val (request, body) = capturedPut()
        assertEquals("uploads/sam/state/errors.jsonl", request.key())
        assertEquals("application/x-ndjson; charset=utf-8", request.contentType())
        val lines = String(body.bytes()).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"error\":\"old\""))
        assertTrue(lines[1].contains("\"error\":\"test-error\"") && lines[1].contains("\"message\":\"test-message\""))
    }

    @Test
    fun `writeError starts a new log when there is none and keeps the last 200 lines`() = runTest {
        val existing = (1..200).joinToString("\n") { """{"time":"t$it","error":"e","message":"m$it"}""" } + "\n"
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenThrow(notFound())
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), existing.toByteArray()))
        stubPut("log")

        service.writeError("sam", "first", "boom")
        service.writeError("sam", "latest", "boom")

        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        val body = ArgumentCaptor.forClass(RequestBody::class.java)
        verify(s3Client, org.mockito.Mockito.times(2)).putObject(request.capture(), body.capture())
        val first = String(body.allValues[0].bytes()).trim().lines()
        assertEquals(1, first.size)
        val second = String(body.allValues[1].bytes()).trim().lines()
        assertEquals(200, second.size)
        assertTrue(second.first().contains("\"message\":\"m2\""))
        assertTrue(second.last().contains("\"error\":\"latest\""))
    }

    @Test
    fun `writeError swallows S3 failures`() = runTest {
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenThrow(notFound())
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(RuntimeException("down"))

        service.writeError("sam", "e", "m")
    }

    @Test
    fun `listErrors returns the log newest first and nothing when there is no log`() = runTest {
        val log = """{"time":"2026-10-14T10:00:00Z","error":"a","message":"first"}
{"time":"2026-10-14T11:00:00Z","error":"b","message":"second"}
not json
"""
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), log.toByteArray()))
            .thenThrow(notFound())

        val entries = service.listErrors("sam")
        assertEquals(listOf("b", "a"), entries.map { it.error })
        assertEquals("second", entries[0].message)
        assertEquals("2026-10-14T11:00:00Z", entries[0].time)

        assertTrue(service.listErrors("sam").isEmpty())
        assertTrue(service.listErrors("").isEmpty())
    }

    @Test
    fun `clearErrors deletes the log object`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())

        assertTrue(service.clearErrors("sam"))

        val request = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)
        verify(s3Client).deleteObject(request.capture())
        assertEquals("uploads/sam/state/errors.jsonl", request.value.key())
    }

    // ---- objects by key ----------------------------------------------------------------------------

    @Test
    fun `getObject returns bytes, content type and etag, or null when missing`() = runTest {
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("application/json").eTag("\"e1\"").build(), "{}".toByteArray()))
            .thenThrow(notFound())

        val stored = service.getObject("uploads/sam/state/devices.json")!!
        assertEquals("devices.json", stored.name)
        assertEquals("application/json", stored.contentType)
        assertEquals("\"e1\"", stored.eTag)
        assertArrayEquals("{}".toByteArray(), stored.bytes)

        assertNull(service.getObject("uploads/sam/state/missing.json"))
    }

    @Test
    fun `putObject writes the key with its content type`() = runTest {
        stubPut("\"put\"")

        val eTag = service.putObject("uploads/sam/state/devices.json", "{}".toByteArray(), "application/json")

        assertEquals("\"put\"", eTag)
        val (request, body) = capturedPut()
        assertEquals("uploads/sam/state/devices.json", request.key())
        assertEquals("application/json", request.contentType())
        assertArrayEquals("{}".toByteArray(), body.bytes())
    }

    @Test
    fun `deleteObject reports whether there was something to delete`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenReturn(HeadObjectResponse.builder().build())
            .thenThrow(notFound())

        assertTrue(service.deleteObject("uploads/sam/jar/echo.jar"))
        assertFalse(service.deleteObject("uploads/sam/jar/gone.jar"))

        verify(s3Client, org.mockito.Mockito.times(1)).deleteObject(any(DeleteObjectRequest::class.java))
    }

    @Test
    fun `objects outside uploads or with dot segments are refused`() {
        for (key in listOf("releases/amie.dmg", "uploads/", "uploads/../secret", "uploads/sam/./x", "/uploads/sam/x", "uploads/sam/")) {
            assertFalse(S3Service.isAllowedKey(key), key)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { service.getObject(key) } }
        }
        assertTrue(S3Service.isAllowedKey("uploads/sam/jar/echo.jar"))
        assertTrue(S3Service.isAllowedFileName("jar/echo.jar"))
        assertFalse(S3Service.isAllowedFileName("../echo.jar"))
        assertFalse(S3Service.isAllowedFileName("/echo.jar"))
    }

    @Test
    fun `listStates names the json files in the state folder`() = runTest {
        stubPaginator(
            objects = listOf(
                S3Object.builder().key("uploads/sam/state/devices.json").build(),
                S3Object.builder().key("uploads/sam/state/errors.jsonl").build(),
                S3Object.builder().key("uploads/sam/state/collections.json").build(),
                S3Object.builder().key("uploads/sam/state/nested/x.json").build()
            )
        )

        assertEquals(listOf("collections", "devices"), service.listStates("sam"))
        val request = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2Paginator(request.capture())
        assertEquals("uploads/sam/state/", request.value.prefix())
    }

    // ---- endpoints -------------------------------------------------------------------------------------

    @Test
    fun `fetchEndpoints converts an endpoint array to a name map`() = runTest {
        val json = """[{"retailName":"Arduino Uno","descriptiveName":"Development board"}]"""
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), json.toByteArray()))

        val endpoints = service.fetchEndpoints()

        assertEquals(mapOf("Arduino Uno" to "Development board"), endpoints)
        val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3Client).getObjectAsBytes(request.capture())
        assertEquals("repositoryInformations", request.value.key())
    }

    @Test
    fun `fetchEndpoints accepts an object of name to description`() = runTest {
        val json = """{"Arduino Uno":"Board","Arduino Nano":"Small board"}"""
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), json.toByteArray()))

        assertEquals(mapOf("Arduino Uno" to "Board", "Arduino Nano" to "Small board"), service.fetchEndpoints())
    }

    @Test
    fun `fetchEndpoints returns an empty map when S3 fails`() = runTest {
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenThrow(RuntimeException("down"))

        assertTrue(service.fetchEndpoints().isEmpty())
    }

    @Test
    fun `postEndpoints uploads the endpoint list as JSON`() = runTest {
        val expectedJson = """[{"retailName":"Arduino Uno","descriptiveName":"Arduino uno more descriptive"},{"retailName":"Arduino Uno2","descriptiveName":"Arduino uno more descriptive"},{"retailName":"Arduino Uno3","descriptiveName":"Arduino uno more descriptive"}]"""

        service.postEndpoints()

        val (request, body) = capturedPut()
        assertEquals("repositoryInformations", request.key())
        assertEquals("application/json", request.contentType())
        assertArrayEquals(expectedJson.toByteArray(), body.bytes())
    }

    // ---- queries ---------------------------------------------------------------------------------------

    @Test
    fun `queryFiles looks up one file by name`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build())

        val result = service.queryFiles("test.txt") as Map<*, *>

        assertEquals("test.txt", result["name"])
        assertEquals("uploads/test.txt", result["path"])
        assertEquals("file", result["type"])
        assertEquals(1024L, result["size"])
        assertEquals("$PUBLIC_URL/api/aws/files/content?key=uploads%2Ftest.txt", result["download_url"])
    }

    @Test
    fun `queryFiles searches by extension when the query starts with a star`() = runTest {
        stubPaginator(
            objects = listOf(
                S3Object.builder().key("uploads/sam/amie.jar").size(10L).build(),
                S3Object.builder().key("uploads/sam/notes.txt").size(5L).build(),
                S3Object.builder().key("uploads/tom/tool.JAR").size(7L).build()
            )
        )

        val result = service.queryFiles("*jar") as List<*>

        assertEquals(listOf("uploads/sam/amie.jar", "uploads/tom/tool.JAR"), result.map { (it as Map<*, *>)["path"] })
        val request = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2Paginator(request.capture())
        assertEquals("uploads/", request.value.prefix())
    }

    @Test
    fun `queryFiles with a bare star returns nothing without calling S3`() = runTest {
        val result = service.queryFiles("*") as List<*>

        assertTrue(result.isEmpty())
        verify(s3Client, never()).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))
    }

    @Test
    fun `queryFiles returns null on 404`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenThrow(notFound())

        assertNull(service.queryFiles("missing.txt"))
    }

    @Test
    fun `queryFiles returns null on other S3 errors`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(S3Exception.builder().statusCode(500).message("boom").build() as S3Exception)

        assertNull(service.queryFiles("broken.txt"))
    }

    // ---- dashboards ------------------------------------------------------------------------------------

    @Test
    fun `createUserDashboard returns early when the username is blank`() = runTest {
        service.createUserDashboard("   ")

        verify(s3Client, never()).headObject(any(HeadObjectRequest::class.java))
        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `createUserDashboard skips creation when the dashboard exists`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())

        service.createUserDashboard("john")

        verify(s3Client).headObject(any(HeadObjectRequest::class.java))
        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `createUserDashboard creates the README when S3 answers 404`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenThrow(notFound())

        service.createUserDashboard("john_doe")

        val (request, body) = capturedPut()
        assertEquals("uploads/john_doe/README.md", request.key())
        assertEquals("text/markdown; charset=utf-8", request.contentType())
        assertEquals("# Dashboard for john_doe", String(body.bytes()))
    }

    @Test
    fun `createUserDashboard swallows an upload failure`() = runTest {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenThrow(notFound())
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(RuntimeException("AWS Network Error"))

        service.createUserDashboard("john_doe")

        verify(s3Client).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    // ---- listings --------------------------------------------------------------------------------------

    @Test
    fun `listFiles maps common prefixes to dirs and contents to files`() = runTest {
        val page = ListObjectsV2Response.builder()
            .commonPrefixes(CommonPrefix.builder().prefix("uploads/documents/").build())
            .contents(S3Object.builder().key("uploads/photo.png").size(2048L).eTag("\"etag-123\"").build())
            .build()
        stubPaginator(pages = listOf(page))

        val result = service.listFiles()

        assertEquals(2, result.size)
        assertEquals(S3ItemDto("documents", "uploads/documents/", "", null, "dir", null), result[0])
        assertEquals(S3ItemDto("photo.png", "uploads/photo.png", "$PUBLIC_URL/api/aws/files/content?key=uploads%2Fphoto.png", "\"etag-123\"", "file", 2048L), result[1])
        val request = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2Paginator(request.capture())
        assertEquals("uploads/", request.value.prefix())
        assertEquals("/", request.value.delimiter())
    }

    @Test
    fun `listFiles returns an empty list on exception`() = runTest {
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java)))
            .thenThrow(RuntimeException("S3 connection timeout"))

        assertTrue(service.listFiles().isEmpty())
    }

    @Test
    fun `listUserPackages skips S3 when the username is blank`() = runTest {
        assertTrue(service.listUserPackages("   ").isEmpty())

        verify(s3Client, never()).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))
    }

    @Test
    fun `listUserPackages lists the user's files and drops markdown and state`() = runTest {
        stubPaginator(
            objects = listOf(
                S3Object.builder().key("uploads/john_doe/package.zip").size(1024L).eTag("\"etag-zip\"").build(),
                S3Object.builder().key("uploads/john_doe/README.MD").size(512L).eTag("\"etag-md\"").build(),
                S3Object.builder().key("uploads/john_doe/state/devices.json").size(12L).eTag("\"etag-state\"").build(),
                S3Object.builder().key("uploads/john_doe/state/errors.jsonl").size(12L).eTag("\"etag-log\"").build()
            )
        )

        val result = service.listUserPackages("john_doe")

        assertEquals(1, result.size)
        assertEquals("package.zip", result[0].name)
        assertEquals(1024L, result[0].size)
        val request = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2Paginator(request.capture())
        assertEquals("uploads/john_doe/", request.value.prefix())
    }

    @Test
    fun `listUserPackages returns an empty list on exception`() = runTest {
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java)))
            .thenThrow(RuntimeException("AWS Service Outage"))

        assertTrue(service.listUserPackages("john_doe").isEmpty())
    }

    @Test
    fun `the service is constructed with the configured bucket`() {
        assertNotNull(service)
    }

    companion object {
        private const val BUCKET = "test-bucket"
        private const val PUBLIC_URL = "http://s3.test:8088"
    }
}
