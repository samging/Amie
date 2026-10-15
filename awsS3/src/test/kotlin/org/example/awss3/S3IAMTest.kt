package org.example.awss3

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse
import software.amazon.awssdk.services.sts.model.StsException
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** The start-up credential check: what it prints for a working identity, a missing bucket and no credentials at all. */
@ExtendWith(MockitoExtension::class)
class S3IAMTest {
    @Mock private lateinit var sts: StsClient
    @Mock private lateinit var s3: S3Client

    private val out = ByteArrayOutputStream()
    private lateinit var previous: PrintStream

    @BeforeEach
    fun capture() {
        previous = System.out
        System.setOut(PrintStream(out, true))
    }

    @AfterEach
    fun restore() {
        System.setOut(previous)
    }

    private fun printed() = out.toString()

    @Test
    fun `a working identity and bucket are reported`() = runTest {
        `when`(sts.callerIdentity).thenReturn(GetCallerIdentityResponse.builder().arn("arn:aws:iam::123:user/amie").account("123").build())
        `when`(s3.headBucket(any(HeadBucketRequest::class.java))).thenReturn(HeadBucketResponse.builder().build())

        S3IAM().checkIdentity(sts, s3, "setup-demo-sams")

        assertTrue(printed().contains("Identity Check SUCCESS"), printed())
        assertTrue(printed().contains("arn:aws:iam::123:user/amie"))
        assertTrue(printed().contains("Bucket 'setup-demo-sams' exists"))
        verify(s3).headBucket(HeadBucketRequest.builder().bucket("setup-demo-sams").build())
    }

    @Test
    fun `a missing bucket is reported after a good identity`() = runTest {
        `when`(sts.callerIdentity).thenReturn(GetCallerIdentityResponse.builder().arn("arn").account("123").build())
        `when`(s3.headBucket(any(HeadBucketRequest::class.java)))
            .thenThrow(S3Exception.builder().message("Not Found (Service: S3, Status Code: 404)").build())

        S3IAM().checkIdentity(sts, s3, "nope")

        assertTrue(printed().contains("Identity Check SUCCESS"))
        assertTrue(printed().contains("Bucket 'nope' check FAILED: Not Found"), printed())
    }

    @Test
    fun `without credentials the identity check fails and the bucket is not asked`() = runTest {
        `when`(sts.callerIdentity).thenThrow(StsException.builder().message("Unable to load credentials").build())

        S3IAM().checkIdentity(sts, s3, "setup-demo-sams")

        assertTrue(printed().contains("Identity Check FAILED: Unable to load credentials"), printed())
        verify(s3, never()).headBucket(any(HeadBucketRequest::class.java))
    }
}
