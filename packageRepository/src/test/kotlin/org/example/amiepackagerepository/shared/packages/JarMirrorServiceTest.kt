package org.example.amiepackagerepository.shared.packages

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class JarMirrorServiceTest {

    @Test
    fun `posts the jar bytes to the S3 service's raw upload endpoint`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("http://s3-service:8088/api/aws/upload-data?username=sam&fileName=amie-1.0.jar"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))
            .andRespond(withSuccess("File uploaded successfully. ETag: \"x\"", MediaType.TEXT_PLAIN))

        val service = JarMirrorService("http://s3-service:8088/", builder)

        assertTrue(service.enabled)
        assertTrue(service.mirror("sam", "amie-1.0.jar", byteArrayOf(1, 2, 3)))
        server.verify()
    }

    @Test
    fun `a failing S3 service does not fail the caller`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("http://s3-service:8088/api/aws/upload-data?username=sam&fileName=bad.jar"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("The jar contains no classes"))

        assertFalse(JarMirrorService("http://s3-service:8088", builder).mirror("sam", "bad.jar", byteArrayOf(1)))
        server.verify()
    }

    @Test
    fun `mirroring is off when no service url is configured`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()

        val service = JarMirrorService("", builder)

        assertFalse(service.enabled)
        assertFalse(service.mirror("sam", "amie.jar", byteArrayOf(1)))
        server.verify() // nothing was called
    }
}
