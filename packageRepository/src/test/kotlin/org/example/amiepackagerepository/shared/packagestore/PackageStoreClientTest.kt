package org.example.amiepackagerepository.shared.packagestore

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/** [PackageStoreClient] against a recorded S3 service: what it asks for and how it maps the answers. */
class PackageStoreClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: PackageStoreClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = PackageStoreClient("http://s3:8088/", "http://site:8081/", builder)
    }

    @Test
    fun `listRoot maps the service's items and keeps folders without a download URL`() {
        server.expect(requestTo("http://s3:8088/api/aws/files/root")).andExpect(method(HttpMethod.GET)).andRespond(
            withSuccess(
                """[{"name":"sam","path":"uploads/sam/","downloadUrl":"","eTag":null,"type":"dir","size":null},
                    {"name":"tool.c","path":"uploads/tool.c","downloadUrl":"http://s3:8088/api/aws/files/content?key=uploads%2Ftool.c","eTag":"\"e\"","type":"file","size":12}]""",
                MediaType.APPLICATION_JSON
            )
        )

        val items = client.listRoot()

        assertEquals(2, items.size)
        assertEquals("sam", items[0].name); assertEquals("dir", items[0].type); assertNull(items[0].downloadUrl); assertNull(items[0].id)
        assertEquals("tool.c", items[1].name); assertEquals("\"e\"", items[1].id); assertEquals(12L, items[1].size)
        assertEquals("http://s3:8088/api/aws/files/content?key=uploads%2Ftool.c", items[1].downloadUrl)
        server.verify()
    }

    @Test
    fun `listUserPackages keeps the legacy field names and links to the website's view page`() {
        server.expect(requestTo("http://s3:8088/api/aws/users/sam/packages")).andRespond(
            withSuccess(
                """[{"name":"echo.jar","path":"uploads/sam/jar/echo.jar","downloadUrl":"http://s3/echo.jar","eTag":"\"j\"","type":"file","size":100}]""",
                MediaType.APPLICATION_JSON
            )
        )

        val files = client.listUserPackages("sam")

        assertEquals(1, files.size)
        val file = files[0]
        assertEquals("echo.jar", file.name)
        assertEquals("uploads/sam/jar/echo.jar", file.path)
        assertEquals("\"j\"", file.sha)
        assertEquals(100L, file.size)
        assertEquals("http://s3/echo.jar", file.downloadUrl)
        assertEquals("http://s3/echo.jar", file.url)
        assertEquals("http://site:8081/view?package=uploads%2Fsam%2Fjar%2Fecho.jar", file.htmlUrl)
        assertTrue(client.listUserPackages("").isEmpty())
    }

    @Test
    fun `the JSON of a user package carries download_url and html_url`() {
        server.expect(requestTo("http://s3:8088/api/aws/users/sam/packages")).andRespond(
            withSuccess("""[{"name":"a.txt","path":"uploads/sam/txt/a.txt","downloadUrl":"http://d","eTag":"\"t\"","type":"file","size":1}]""", MediaType.APPLICATION_JSON)
        )

        val json = ObjectMapper().writeValueAsString(client.listUserPackages("sam"))

        assertTrue(json.contains("\"download_url\":\"http://d\""), json)
        assertTrue(json.contains("\"html_url\":"), json)
        assertTrue(json.contains("\"sha\":\"\\\"t\\\"\""), json)
    }

    @Test
    fun `upload posts the bytes under the user's language folder with the content type`() {
        server.expect(requestTo("http://s3:8088/api/aws/upload-data?username=sam&fileName=onnx/module.json&contentType=application/json"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
            .andExpect(content().bytes("{}".toByteArray()))
            .andRespond(withSuccess("File uploaded successfully. ETag: x", MediaType.TEXT_PLAIN))

        client.upload("sam", "onnx", "module.json", "{}".toByteArray(), "application/json")

        server.verify()
    }

    @Test
    fun `upload refuses names that leave the user's folder before calling the store`() {
        assertThrows(IllegalArgumentException::class.java) { client.upload("sam", "jar", "../tom.jar", byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { client.upload("", "jar", "a.jar", byteArrayOf(1)) }
        server.verify()
    }

    @Test
    fun `a refusal by the store becomes a PackageStoreException with its status and body`() {
        server.expect(requestToUriTemplate("http://s3:8088/api/aws/upload-data?username=sam&fileName=jar/fake.jar"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("The file is not a valid jar archive").contentType(MediaType.TEXT_PLAIN))

        val e = assertThrows(PackageStoreException::class.java) { client.upload("sam", "jar", "fake.jar", byteArrayOf(1)) }

        assertEquals(400, e.status)
        assertTrue(e.message!!.contains("The file is not a valid jar archive"))
    }

    @Test
    fun `get returns the object with its headers, null on 404, and fails loudly on 500`() {
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fstate%2Fdevices.json"))
            .andRespond(withSuccess("""{"1":{}}""".toByteArray(), MediaType.APPLICATION_JSON).header("ETag", "\"e1\""))
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fstate%2Fnone.json")).andRespond(withResourceNotFound())
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fstate%2Fbroken.json")).andRespond(withServerError())

        val stored = client.get("uploads/sam/state/devices.json")!!
        assertArrayEquals("""{"1":{}}""".toByteArray(), stored.bytes)
        assertEquals("application/json", stored.contentType)
        assertEquals("\"e1\"", stored.eTag)

        assertNull(client.get("uploads/sam/state/none.json"))
        val e = assertThrows(PackageStoreException::class.java) { client.get("uploads/sam/state/broken.json") }
        assertEquals(500, e.status)
        assertThrows(IllegalArgumentException::class.java) { client.get("releases/x.dmg") }
    }

    @Test
    fun `head reads the metadata route`() {
        server.expect(requestTo("http://s3:8088/api/aws/files/meta?key=uploads%2Fsam%2Fjar%2Fecho.jar")).andRespond(
            withSuccess("""{"name":"echo.jar","path":"uploads/sam/jar/echo.jar","size":42,"eTag":"\"m\"","contentType":"application/java-archive","downloadUrl":"http://d"}""", MediaType.APPLICATION_JSON)
        )
        server.expect(requestTo("http://s3:8088/api/aws/files/meta?key=uploads%2Fsam%2Fjar%2Fnone.jar")).andRespond(withResourceNotFound())

        val meta = client.head("uploads/sam/jar/echo.jar")!!
        assertEquals(42L, meta.size)
        assertEquals("\"m\"", meta.eTag)
        assertEquals("http://d", meta.downloadUrl)
        assertNull(client.head("uploads/sam/jar/none.jar"))
    }

    @Test
    fun `put and delete address the object by key`() {
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads/sam/state/devices.json&contentType=application/json"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().bytes("{}".toByteArray()))
            .andRespond(withSuccess("""{"key":"uploads/sam/state/devices.json","eTag":"\"w\""}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fjar%2Fecho.jar")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fjar%2Fgone.jar")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withResourceNotFound())

        client.put("uploads/sam/state/devices.json", "{}".toByteArray(), "application/json")
        assertTrue(client.delete("uploads/sam/jar/echo.jar"))
        assertFalse(client.delete("uploads/sam/jar/gone.jar"))
        server.verify()
    }

    @Test
    fun `states are stored as normalised JSON under the state folder`() {
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads/sam/state/collections.json&contentType=application/json"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string("""{"a":1}"""))
            .andRespond(withSuccess())
        server.expect(requestTo("http://s3:8088/api/aws/files/content?key=uploads%2Fsam%2Fstate%2Fcollections.json"))
            .andRespond(withSuccess("""{"a":1}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://s3:8088/api/aws/users/sam/states")).andRespond(withSuccess("""["collections","devices"]""", MediaType.APPLICATION_JSON))

        client.saveState("sam", "collections", "{ \"a\" : 1 }")
        assertEquals("""{"a":1}""", client.loadState("sam", "collections"))
        assertEquals(listOf("collections", "devices"), client.listStates("sam"))

        assertThrows(IllegalArgumentException::class.java) { client.saveState("sam", "collections", "not json") }
        assertThrows(IllegalArgumentException::class.java) { client.saveState("sam", "../x", "{}") }
        assertThrows(IllegalArgumentException::class.java) { client.loadState("sam", "a/b") }
        server.verify()
    }

    @Test
    fun `errors are appended through the service and read back newest first`() {
        server.expect(requestTo("http://s3:8088/api/aws/errors?username=sam&error=LoginError&message=boom")).andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        server.expect(requestTo("http://s3:8088/api/aws/errors?username=sam")).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""[{"time":"t2","error":"b","message":"second"},{"time":"t1","error":"a","message":"first"}]""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://s3:8088/api/aws/errors?username=sam")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        client.writeError("sam", "LoginError", "boom")
        val errors = client.listErrors("sam")
        assertEquals(listOf("b", "a"), errors.map { it.error })
        assertEquals("second", errors[0].message)
        client.clearErrors("sam")
        server.verify()
    }

    @Test
    fun `writeError and fetchEndpoints never throw when the store is down`() {
        server.expect(ExpectedCount.once(), requestToUriTemplate("http://s3:8088/api/aws/errors?username=sam&error=e&message=m")).andRespond(withServerError())
        server.expect(requestTo("http://s3:8088/api/aws/endpoints")).andRespond(withServerError())

        client.writeError("sam", "e", "m")
        assertTrue(client.fetchEndpoints().isEmpty())
        server.verify()
    }

    @Test
    fun `query passes the store's answer through and maps 404 to null`() {
        server.expect(requestTo("http://s3:8088/api/aws/files?query=%2Ajar")).andRespond(
            withSuccess("""[{"name":"echo.jar","path":"uploads/sam/jar/echo.jar","type":"file","download_url":"http://d","size":3}]""", MediaType.APPLICATION_JSON)
        )
        server.expect(requestTo("http://s3:8088/api/aws/files?query=missing.pdf")).andRespond(withResourceNotFound())

        val matches = client.query("*jar") as List<*>
        assertEquals("echo.jar", (matches[0] as Map<*, *>)["name"])
        assertNull(client.query("missing.pdf"))
    }

    @Test
    fun `createUserDashboard runs in the background and swallows failures`() {
        server.expect(requestTo("http://s3:8088/api/aws/dashboard?username=sam")).andExpect(method(HttpMethod.POST)).andRespond(withServerError())

        client.createUserDashboard("sam").get() // completes without throwing
        client.createUserDashboard("").get()
        server.verify()
    }

    @Test
    fun `an unreachable store is reported without a status`() {
        val builder = RestClient.builder()
        val offline = PackageStoreClient("http://127.0.0.1:1", "http://site", builder)

        val e = assertThrows(PackageStoreException::class.java) { offline.listRoot() }

        assertNull(e.status)
        assertTrue(e.message!!.contains("unreachable"), e.message)
    }

    @Test
    fun `key helpers`() {
        assertEquals("uploads/sam/state/devices.json", PackageStoreClient.stateKey("sam", "devices"))
        assertEquals("uploads/sam/onnx/model.onnx", PackageStoreClient.packageKey("sam", "onnx", "model.onnx"))
        assertEquals("uploads/sam/model.onnx", PackageStoreClient.packageKey("sam", "", "model.onnx"))
        assertEquals("sam", PackageStoreClient.ownerOf("uploads/sam/jar/echo.jar"))
        assertNull(PackageStoreClient.ownerOf("uploads/"))
        assertNull(PackageStoreClient.ownerOf("releases/amie.dmg"))
        assertTrue(PackageStoreClient.isValidStateName("devices"))
        assertFalse(PackageStoreClient.isValidStateName("dev/ices"))
        assertFalse(PackageStoreClient.isValidStateName(""))
        assertEquals("http://site:8081/view?package=uploads%2Fsam%2Fa.txt", client.viewUrl("uploads/sam/a.txt"))
    }
}
