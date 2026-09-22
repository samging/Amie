package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CommonPrefix
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class S3ServiceTest {
    //AAA: arrange, act, assert
    private val s3Client: S3Client = mock(S3Client::class.java)
    private val service = S3Service(s3Client, ObjectMapper(), "test-bucket")

    @Mock
    private lateinit var file: MultipartFile

    @Test
    fun `downloadAmie_checks_if_application_is_being_downloaded`() {
        runTest {
            val keyName = "test-file.txt"
            val bucketName = "test-bucket"

            val mockResponseStream = mock(ResponseInputStream::class.java) as ResponseInputStream<GetObjectResponse>

            `when`(s3Client.getObject(any(GetObjectRequest::class.java)))
                .thenReturn(mockResponseStream)

            val awaitedResp = service.downloadAmie(keyName, bucketName)

            assertNotNull(awaitedResp)
            assertEquals(mockResponseStream, awaitedResp)

            verify(s3Client).getObject(any(GetObjectRequest::class.java))
        }
    }

    @Test
    fun `uploadFileData_stores_bytes_at_the_user_upload_key`() {
        runTest {
            `when`(
                s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
            ).thenReturn(PutObjectResponse.builder().eTag("raw-etag").build())

            val result = service.uploadFileData("sam", "notes.txt", "hello".toByteArray())

            val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody::class.java))
            assertEquals("test-bucket", requestCaptor.value.bucket())
            assertEquals("uploads/sam/notes.txt", requestCaptor.value.key())
            assertEquals("File uploaded successfully. ETag: raw-etag", result)
        }
    }

    @Test
    fun `uploadFileData_rejects_a_blank_username_before_calling_S3`() {
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.uploadFileData("", "notes.txt", byteArrayOf()) }
            }

            verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
        }
    }

    @Test
    fun `listBucketObjects_returns_keys_from_the_S3_response`() {
        runTest {
            `when`(s3Client.listObjects(any(ListObjectsRequest::class.java))).thenReturn(
                ListObjectsResponse.builder()
                    .contents(
                        S3Object.builder().key("uploads/sam/a.txt").build(),
                        S3Object.builder().key("uploads/sam/b.txt").build()
                    )
                    .build()
            )

            val keys = service.listBucketObjects()

            assertEquals(listOf("uploads/sam/a.txt", "uploads/sam/b.txt"), keys)
        }
    }


    @Test
    fun `fetchEndpoints_converts_endpoint_JSON_to_a_name_map`() {
        runTest {
            val responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                """[{"retailName":"Arduino Uno","descriptiveName":"Development board"}]""".toByteArray()
            )
            `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenReturn(responseBytes)

            val endpoints = service.fetchEndpoints()

            assertEquals(mapOf("Arduino Uno" to "Development board"), endpoints)
        }
    }

    @Test
    fun `sendEdit_replaces_the_requested_object_after_S3_confirms_it_exists`() {
        runTest {
            val file = mock(MultipartFile::class.java)
            `when`(file.contentType).thenReturn("text/plain")
            `when`(file.size).thenReturn(5)
            `when`(file.inputStream).thenReturn("hello".byteInputStream())
            `when`(
                s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
            ).thenReturn(PutObjectResponse.builder().eTag("edit-etag").build())

            val result = service.sendEdit("sam", "notes.txt", file)

            val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody::class.java))
            assertEquals("uploads/sam/notes.txt", requestCaptor.value.key())
            assertEquals("File updated successfully. ETag: edit-etag", result)
        }
    }

    @Test
    fun `uploadFile_testing_if_file_is_uploaded_successfully`() {
        runTest {
            val dummyMultipart = mock(MultipartFile::class.java)
            val fileName = "test.txt"
            val expectedETag = "\"1234567890abcdef\""

            `when`(dummyMultipart.originalFilename).thenReturn(fileName)
            `when`(dummyMultipart.inputStream).thenReturn("hello".byteInputStream())
            `when`(dummyMultipart.size).thenReturn(5L)

            val fakeResponse = PutObjectResponse.builder().eTag(expectedETag).build()
            `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))).thenReturn(fakeResponse)
            val result = service.uploadFile(dummyMultipart)
            assertEquals("File uploaded successfully. ETag: $expectedETag", result)
        }
    }

    @Test
    fun `uploadFile should throw RuntimeException when S3 upload fails`() {
        runTest {
            `when`(file.originalFilename).thenReturn("test.txt")
            `when`(file.inputStream).thenReturn("hello".byteInputStream())
            `when`(file.size).thenReturn(4L)

            `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
                .thenThrow(RuntimeException("AWS Service Error"))

            val exception = assertThrows(RuntimeException::class.java) {
                runBlocking { service.uploadFile(file) }
            }
            assertTrue(exception.message!!.contains("S3 Upload Failed: AWS Service Error"))
        }
    }

    @Test
    fun `writeError should log to S3`() = runTest {
        val username = "sam"
        val error = "test-error"
        val message = "test-message"

        service.writeError(username, error, message)

        verify(s3Client)
            .putObject(any(PutObjectRequest::class.java),
                any(RequestBody::class.java)
            )
    }

    @Test
    fun `postEndpoints should upload to S3 consumer and target device packages`() = runTest {
        val expectedJson = """[{"retailName":"Arduino Uno","descriptiveName":"Arduino uno more descriptive"},{"retailName":"Arduino Uno2","descriptiveName":"Arduino uno more descriptive"},{"retailName":"Arduino Uno3","descriptiveName":"Arduino uno more descriptive"}]"""
            .toByteArray(Charsets.UTF_8)

        val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        val bodyCaptor = ArgumentCaptor.forClass(RequestBody::class.java)

        service.postEndpoints()

        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture())
        assertEquals("repositoryInformations", requestCaptor.value.key())
        assertEquals("application/json", requestCaptor.value.contentType())

        val actualBytes = bodyCaptor.value.contentStreamProvider().newStream().readBytes()
        assertArrayEquals(expectedJson, actualBytes)
    }

    @Test
    fun `queryFiles should fetch metadata for specific file`() = runTest {
        val fakeHeaders = software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder().contentLength(1024L).build()
        `when`(
            s3Client.headObject(any(HeadObjectRequest::class.java
            ))).thenReturn(fakeHeaders)
        val result = service.queryFiles("test.txt")
        assertEquals(true, result is Map<*, *>)
    }

    @Test
    fun `queryFiles should return null on 404 Not Found`() = runTest {
        val notFoundPage = S3Exception.builder()
            .statusCode(404).message("not found").build() as S3Exception
        `when`(
            s3Client.headObject(any(HeadObjectRequest::class.java
            ))).thenThrow(notFoundPage)

        val result = service.queryFiles("missing.txt")

        assertEquals(null, result)
    }

    @Test
    fun `createUserDashboard should return early when username is blank`() = runTest {
        service.createUserDashboard("   ")
        verify(s3Client, never()).headObject(any<HeadObjectRequest>())
        verify(s3Client, never()).putObject(any<PutObjectRequest>(), any<RequestBody>())
    }

    @Test
    fun `createUserDashboard should skip creation when dashboard already exists`() = runTest {
        val username = "john"
        `when`(
            s3Client.headObject(any(HeadObjectRequest::class.java
            ))).thenReturn(null)

        service.createUserDashboard(username)
        verify(s3Client).headObject(any<HeadObjectRequest>())
        verify(s3Client, never()).putObject(any<PutObjectRequest>(), any<RequestBody>())
    }

    @Test
    fun `createUserDashboard should create dashboard when headObject returns 404`() = runTest {
        // ARRANGE
        val username = "john_doe"
        val expectedKey = "uploads/john_doe/README.md"

        // Mock headObject to throw 404 S3Exception
        val notFoundException = S3Exception.builder()
            .statusCode(404)
            .message("Not Found")
            .build() as S3Exception

        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(notFoundException)

        val requestCaptor = ArgumentCaptor.forClass(PutObjectRequest::class.java)

        // ACT
        service.createUserDashboard(username)

        // ASSERT
        verify(s3Client).headObject(any(HeadObjectRequest::class.java))
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody::class.java))

        val capturedRequest = requestCaptor.value
        assertEquals(expectedKey, capturedRequest.key())
        assertEquals("text/markdown; charset=utf-8", capturedRequest.contentType())
    }

    @Test
    fun `createUserDashboard should catch exception when S3 upload fails`() = runTest {
        val username = "john_doe"

        val notFoundException = S3Exception.builder().statusCode(404).build() as S3Exception
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenThrow(notFoundException)

        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(RuntimeException("AWS Network Error"))

        service.createUserDashboard(username)

        verify(s3Client).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `listFiles should map commonPrefixes as dir and contents as file`() = runTest {
        val dummyFolder = CommonPrefix.builder().prefix("uploads/documents/").build()
        val dummyFile = S3Object.builder()
            .key("uploads/photo.png")
            .size(2048L)
            .eTag("\"etag-123\"")
            .build()

        val fakePage = ListObjectsV2Response.builder()
            .commonPrefixes(dummyFolder)
            .contents(dummyFile)
            .build()

        val paginator = mock(ListObjectsV2Iterable::class.java)
        `when`(paginator.iterator()).thenReturn(mutableListOf(fakePage).iterator())
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java))).thenReturn(paginator)

        val requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)

        // --- ACT ---
        val result = service.listFiles()

        // --- ASSERT ---
        // Verify total mapped items count
        assertEquals(2, result.size)

        // Verify request configuration
        verify(s3Client).listObjectsV2Paginator(requestCaptor.capture())
        val capturedRequest = requestCaptor.value
        assertEquals("uploads/", capturedRequest.prefix())
        assertEquals("/", capturedRequest.delimiter())
    }

    @Test
    fun `listFiles should return empty list on exception`() = runTest {
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java)))
            .thenThrow(RuntimeException("S3 connection timeout"))

        val result = service.listFiles()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listUserPackages should return empty list and skip S3 when username is blank`() = runTest {
        // ACT
        val result = service.listUserPackages("   ")

        // ASSERT
        assertTrue(result.isEmpty())
        verify(s3Client, never()).listObjectsV2Paginator(any(ListObjectsV2Request::class.java))
    }

    @Test
    fun `listUserPackages should fetch user files and filter out md files`() = runTest {
        // --- ARRANGE ---
        val username = "john_doe"

        // 1. Create fake S3Objects (1 zip file, 1 markdown file that should be filtered)
        val zipFile = S3Object.builder()
            .key("uploads/john_doe/package.zip")
            .size(1024L)
            .eTag("\"etag-zip\"")
            .build()

        val mdFile = S3Object.builder()
            .key("uploads/john_doe/README.MD") // uppercase extension to test ignoreCase = true
            .size(512L)
            .eTag("\"etag-md\"")
            .build()

        // 2. Stub paginator's contents() method
        // `.contents()` returns a SdkIterable that delegates to iterator()
        val paginator = mock(ListObjectsV2Iterable::class.java)
        @Suppress("UNCHECKED_CAST")
        val mockContentsIterable = mock(SdkIterable::class.java) as SdkIterable<S3Object>
        `when`(mockContentsIterable.iterator()).thenReturn(mutableListOf(zipFile, mdFile).iterator())
        `when`(paginator.contents()).thenReturn(mockContentsIterable)

        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java))).thenReturn(paginator)

        val requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)

        // --- ACT ---
        val result = service.listUserPackages(username)

        // --- ASSERT ---
        // Verify .md file was filtered out (only 1 file remains)
        assertEquals(1, result.size)

        // Verify request parameters
        verify(s3Client).listObjectsV2Paginator(requestCaptor.capture())
        val capturedRequest = requestCaptor.value
        assertEquals("uploads/john_doe/", capturedRequest.prefix())
    }

    @Test
    fun `listUserPackages should return empty list on exception`() = runTest {
        // --- ARRANGE ---
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java)))
            .thenThrow(RuntimeException("AWS Service Outage"))

        // --- ACT ---
        val result = service.listUserPackages("john_doe")

        // --- ASSERT ---
        assertTrue(result.isEmpty())
    }
}
