package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.awss3.jar.JarInspector
import org.example.awss3.jar.JarService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * What the S3 service answers when S3 itself fails, through a throwing fixture and through the real
 * [UploadController] in front of a mocked [S3Client].
 */
@ExtendWith(MockitoExtension::class)
class ApiExceptionHandlerTest {
    @Mock private lateinit var s3Client: S3Client
    @Mock private lateinit var jarService: JarService

    private lateinit var mockMvc: MockMvc

    @RestController
    class ThrowingController {
        @GetMapping("/t/no-bucket") fun noBucket(): String =
            throw NoSuchBucketException.builder().statusCode(404).message("The specified bucket does not exist").build()
        @GetMapping("/t/denied") fun denied(): String = throw S3Exception.builder().statusCode(403)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").errorMessage("Access Denied").build())
            .message("Access Denied").build()
        @GetMapping("/t/s3-500") fun s3Down(): String = throw S3Exception.builder().statusCode(500)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("InternalError").errorMessage("We encountered an internal error").build())
            .message("We encountered an internal error").build()
        @GetMapping("/t/unreachable") fun unreachable(): String =
            throw SdkClientException.builder().message("Unable to load credentials from any of the providers in the chain\nmore lines").build()
        @GetMapping("/t/require") fun require(): String = throw IllegalArgumentException("Key must be below uploads/")
        @GetMapping("/t/status") fun status(): String = throw ResponseStatusException(HttpStatus.CONFLICT, "Already there")
        @GetMapping("/t/param") fun param(@RequestParam key: String) = "ok"
        @PostMapping("/t/too-large") fun tooLarge(): String = throw MaxUploadSizeExceededException(64L)
        @GetMapping("/t/bug") fun bug(): String = throw NullPointerException("secret internals")
    }

    @BeforeEach
    fun setUp() {
        val controller = UploadController(S3Service(s3Client, ObjectMapper(), "test-bucket"), JarInspector(), jarService)
        mockMvc = MockMvcBuilders.standaloneSetup(ThrowingController(), controller)
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    private fun MvcResult.dispatched() = mockMvc.perform(asyncDispatch(this))

    @Test
    fun `S3 errors are mapped by their status - 403 is a credentials problem, 500 an outage, a missing bucket its own code`() {
        mockMvc.perform(get("/t/denied"))
            .andExpect(status().isBadGateway)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_ACCESS_DENIED))
            .andExpect(jsonPath("$.awsCode").value("AccessDenied"))
            .andExpect(jsonPath("$.detail").value("S3 refused the call: check the AWS credentials and the bucket policy"))

        mockMvc.perform(get("/t/s3-500"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_STORE_ERROR))
            .andExpect(jsonPath("$.awsCode").value("InternalError"))
            .andExpect(jsonPath("$.awsStatus").value(500))
            .andExpect(jsonPath("$.detail").value("S3 failed: We encountered an internal error"))

        mockMvc.perform(get("/t/no-bucket"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_BUCKET_MISSING))
    }

    @Test
    fun `an SDK that never reached S3 is 503 with the first line of the reason`() {
        mockMvc.perform(get("/t/unreachable"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_STORE_UNREACHABLE))
            .andExpect(jsonPath("$.detail").value("S3 could not be reached: Unable to load credentials from any of the providers in the chain"))
    }

    @Test
    fun `input problems are 400, 413 or the status a route chose`() {
        mockMvc.perform(get("/t/require"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Key must be below uploads/"))
        mockMvc.perform(get("/t/param"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Missing parameter: key"))
        mockMvc.perform(post("/t/too-large"))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.detail").value("The upload is too large (at most 64 bytes)"))
        mockMvc.perform(get("/t/status"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Already there"))
            .andExpect(jsonPath("$.code").value("conflict"))
        mockMvc.perform(post("/t/param"))
            .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `a bug is 500 with an error id and without the exception text`() {
        val body = mockMvc.perform(get("/t/bug"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_INTERNAL))
            .andExpect(jsonPath("$.errorId").isString)
            .andReturn().response.contentAsString
        assertTrue("secret internals" !in body, body)
    }

    // ---- through the real routes -------------------------------------------------------------------

    @Test
    fun `downloadAmie of a missing key is 404 instead of 500`() {
        `when`(s3Client.getObject(any(GetObjectRequest::class.java)))
            .thenThrow(NoSuchKeyException.builder().statusCode(404).message("The specified key does not exist").build())

        mockMvc.perform(get("/api/aws/downloadAmie").param("keyName", "nope.jar"))
            .andReturn().dispatched()
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_NOT_FOUND))
            .andExpect(jsonPath("$.instance").value("/api/aws/downloadAmie"))
    }

    @Test
    fun `a 403 from S3 on a key route is 502 store_access_denied, a dead SDK 503`() {
        `when`(s3Client.headObject(any(HeadObjectRequest::class.java)))
            .thenThrow(S3Exception.builder().statusCode(403).awsErrorDetails(AwsErrorDetails.builder().errorCode("InvalidAccessKeyId").build()).message("bad key").build())
            .thenThrow(SdkClientException.builder().message("Connect timed out").build())

        mockMvc.perform(get("/api/aws/files/meta").param("key", "uploads/sam/jar/echo.jar"))
            .andReturn().dispatched()
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_ACCESS_DENIED))
            .andExpect(jsonPath("$.awsCode").value("InvalidAccessKeyId"))

        mockMvc.perform(delete("/api/aws/files/content").param("key", "uploads/sam/jar/echo.jar"))
            .andReturn().dispatched()
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value(ApiExceptionHandler.CODE_STORE_UNREACHABLE))
    }
}
