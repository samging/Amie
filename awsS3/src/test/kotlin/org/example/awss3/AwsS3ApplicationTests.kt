package org.example.awss3

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sts.StsClient

/** The context must start without AWS credentials on the machine, so the AWS clients are mocked. */
@SpringBootTest
class AwsS3ApplicationTests {
    @MockBean
    private lateinit var s3Client: S3Client

    @MockBean
    private lateinit var stsClient: StsClient

    @Test
    fun contextLoads() {
    }
}
