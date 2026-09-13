package org.example.awss3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.sts.StsClient

@Configuration
class S3IAM {
    @Bean
    fun stsClient(): StsClient = StsClient.create()

    @Bean
    fun s3Client(): S3Client = S3Client.create()

    @Bean
    fun verifyAwsCredentials(
        stsClient: StsClient,
        s3Client: S3Client,
        @Value("\${aws.s3.bucket-name}") bucketName: String
    ) = ApplicationRunner {
        runBlocking {
            checkIdentity(stsClient, s3Client, bucketName)
        }
    }

    suspend fun checkIdentity(
        stsClient: StsClient,
        s3Client: S3Client,
        bucketName: String
    ) = withContext(Dispatchers.IO) {
        try {
            val identity = stsClient.callerIdentity
            println("==================================================")
            println("Spring Boot AWS Identity Check SUCCESS:")
            println("  ARN:     ${identity.arn()}")
            println("  Account: ${identity.account()}")
            println("==================================================")

            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
                println("Bucket '$bucketName' exists and is accessible.")
            } catch (e: Exception) {
                println("Bucket '$bucketName' check FAILED: ${e.message}")
            }
            println("==================================================")
        } catch (e: Exception) {
            println("==================================================")
            println("Spring Boot AWS Identity Check FAILED: ${e.message}")
            println("==================================================")
        }
    }
}
