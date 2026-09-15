package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object

class S3ServiceTest {
    private val s3Client = mock(S3Client::class.java)
    private val service = S3Service(s3Client, ObjectMapper(), "test-bucket")

    @Test
    fun `uploadFileData stores bytes at the user upload key`() = runBlocking {
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

    @Test
    fun `uploadFileData rejects a blank username before calling S3`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.uploadFileData("", "notes.txt", byteArrayOf()) }
        }

        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `listBucketObjects returns keys from the S3 response`() = runBlocking {
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

    @Test
    fun `fetchEndpoints converts endpoint JSON to a name map`() = runBlocking {
        val responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            """[{"retailName":"Arduino Uno","descriptiveName":"Development board"}]""".toByteArray()
        )
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenReturn(responseBytes)

        val endpoints = service.fetchEndpoints()

        assertEquals(mapOf("Arduino Uno" to "Development board"), endpoints)
    }

    @Test
    fun `sendEdit replaces the requested object after S3 confirms it exists`() = runBlocking {
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
