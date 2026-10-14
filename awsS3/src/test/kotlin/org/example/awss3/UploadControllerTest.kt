package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.awss3.jar.JarInspector
import org.example.awss3.jar.JarService
import org.example.awss3.jar.TestJars
import org.example.awss3.jar.fixtures.GoodPackage
import org.example.awss3.jar.fixtures.NoClosePackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentCaptor
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

@ExtendWith(MockitoExtension::class)
class UploadControllerTest {
    @Mock
    private lateinit var s3Client: S3Client

    @Mock
    private lateinit var jarService: JarService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = UploadController(S3Service(s3Client, ObjectMapper(), "test-bucket"), JarInspector(), jarService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    /** The upload handlers are suspend functions, so MockMvc sees them as async requests. */
    private fun MvcResult.dispatched() = mockMvc.perform(asyncDispatch(this))

    @Test
    fun `a jar that satisfies the package contract is stored and the caches are cleared`() {
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().eTag("\"ok\"").build())
        val jar = MockMultipartFile("file", "good.jar", "application/java-archive", TestJars.jarOf(GoodPackage::class))

        mockMvc.perform(multipart("/api/aws/upload").file(jar)).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully. ETag: \"ok\""))

        verify(s3Client).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
        verify(jarService).invalidate()
    }

    @Test
    fun `a jar without run and close is refused before anything reaches S3`() {
        val jar = MockMultipartFile("file", "bad.jar", "application/java-archive", TestJars.jarOf(NoClosePackage::class))

        mockMvc.perform(multipart("/api/aws/upload").file(jar)).andReturn().dispatched()
            .andExpect(status().isBadRequest)
            .andExpect(content().string("No class in the jar has both run(String): List and close() methods"))

        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
        verify(jarService, never()).invalidate()
    }

    @Test
    fun `raw data named as a jar is inspected too`() {
        mockMvc.perform(
            post("/api/aws/upload-data")
                .param("username", "sam")
                .param("fileName", "fake.jar")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("print('hi')".toByteArray())
        ).andReturn().dispatched()
            .andExpect(status().isBadRequest)
            .andExpect(content().string("The file is not a valid jar archive"))

        verify(s3Client, never()).putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java))
    }

    @Test
    fun `files that are not jars are uploaded as before`() {
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().eTag("\"txt\"").build())

        mockMvc.perform(
            post("/api/aws/upload-data")
                .param("username", "sam")
                .param("fileName", "notes.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("hello".toByteArray())
        ).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(content().string("File uploaded successfully. ETag: \"txt\""))

        verify(jarService).invalidate()
    }

    @Test
    fun `upload answers 500 with the message when S3 fails`() {
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenThrow(RuntimeException("AWS down"))
        val file = MockMultipartFile("file", "notes.txt", "text/plain", "hi".toByteArray())

        mockMvc.perform(multipart("/api/aws/upload").file(file)).andReturn().dispatched()
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("S3 Upload Failed: AWS down"))

        verify(jarService, never()).invalidate()
    }

    @Test
    fun `edit replaces an existing object and clears the caches`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())
        `when`(s3Client.putObject(any(PutObjectRequest::class.java), any(RequestBody::class.java)))
            .thenReturn(PutObjectResponse.builder().eTag("\"edit\"").build())
        val file = MockMultipartFile("file", "notes.txt", "text/plain", "new".toByteArray())

        mockMvc.perform(multipart("/api/aws/edit").file(file).param("username", "sam").param("fileName", "notes.txt"))
            .andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(content().string("File updated successfully. ETag: \"edit\""))

        verify(jarService).invalidate()
    }

    @Test
    fun `edit of a missing object answers 500`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build() as S3Exception)
        val file = MockMultipartFile("file", "notes.txt", "text/plain", "new".toByteArray())

        mockMvc.perform(multipart("/api/aws/edit").file(file).param("fileName", "notes.txt"))
            .andReturn().dispatched()
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("S3 update failed for uploads/notes.txt"))
    }

    @Test
    fun `errors are written as markdown and answered with 204`() {
        mockMvc.perform(post("/api/aws/errors").param("username", "sam").param("error", "E1").param("message", "boom"))
            .andReturn().dispatched()
            .andExpect(status().isNoContent)

        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(s3Client).putObject(request.capture(), any(RequestBody::class.java))
        assertEquals("uploads/sam/errors.md", request.value.key())
    }

    @Test
    fun `endpoints can be read and written`() {
        `when`(s3Client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenReturn(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                """[{"retailName":"Arduino Uno","descriptiveName":"Board"}]""".toByteArray()
            )
        )

        mockMvc.perform(get("/api/aws/endpoints")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.['Arduino Uno']").value("Board"))

        mockMvc.perform(post("/api/aws/endpoints")).andReturn().dispatched()
            .andExpect(status().isNoContent)
        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(s3Client).putObject(request.capture(), any(RequestBody::class.java))
        assertEquals("repositoryInformations", request.value.key())
    }

    @Test
    fun `files query returns metadata or 404`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenReturn(HeadObjectResponse.builder().contentLength(12L).build())
            .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build() as S3Exception)

        mockMvc.perform(get("/api/aws/files").param("query", "essay.pdf")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("essay.pdf"))
            .andExpect(jsonPath("$.size").value(12))

        mockMvc.perform(get("/api/aws/files").param("query", "missing.pdf")).andReturn().dispatched()
            .andExpect(status().isNotFound)
    }

    @Test
    fun `dashboard creation answers 204`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java))).thenReturn(HeadObjectResponse.builder().build())

        mockMvc.perform(post("/api/aws/dashboard").param("username", "sam")).andReturn().dispatched()
            .andExpect(status().isNoContent)
    }

    @Test
    fun `root listing and user packages come from the paginator`() {
        val paginator = mock(ListObjectsV2Iterable::class.java)
        @Suppress("UNCHECKED_CAST")
        val contents = mock(SdkIterable::class.java) as SdkIterable<S3Object>
        val obj = S3Object.builder().key("uploads/sam/amie.jar").size(10L).eTag("\"e\"").build()
        `when`(contents.iterator()).thenAnswer { mutableListOf(obj).iterator() }
        `when`(paginator.contents()).thenReturn(contents)
        `when`(paginator.iterator()).thenAnswer {
            mutableListOf(ListObjectsV2Response.builder().contents(obj).build()).iterator()
        }
        `when`(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request::class.java))).thenReturn(paginator)

        mockMvc.perform(get("/api/aws/files/root")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("amie.jar"))
            .andExpect(jsonPath("$[0].type").value("file"))

        mockMvc.perform(get("/api/aws/users/sam/packages")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].path").value("uploads/sam/amie.jar"))
    }

    @Test
    fun `objects lists keys and reports failures`() {
        `when`(s3Client.listObjects(any(ListObjectsRequest::class.java)))
            .thenReturn(ListObjectsResponse.builder().contents(S3Object.builder().key("a.txt").build()).build())
            .thenThrow(RuntimeException("no bucket"))

        mockMvc.perform(get("/api/aws/objects")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("a.txt"))

        mockMvc.perform(get("/api/aws/objects")).andReturn().dispatched()
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("no bucket"))
    }

    @Test
    fun `downloadAmie streams the requested key as an attachment`() {
        val response = GetObjectResponse.builder().contentLength(5L).build()
        val stream = ResponseInputStream(response, AbortableInputStream.create("hello".byteInputStream()))
        `when`(s3Client.getObject(any(GetObjectRequest::class.java))).thenReturn(stream)

        mockMvc.perform(get("/api/aws/downloadAmie").param("keyName", "amie.jar").param("bucketName", "b"))
            .andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"amie.jar\""))
            .andExpect(content().string("hello"))

        val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
        verify(s3Client).getObject(request.capture())
        assertEquals("b", request.value.bucket())
        assertEquals("amie.jar", request.value.key())
    }
}
