package org.example.amiepackagerepository.shared.integration.github.service

import org.example.amiepackagerepository.shared.integration.github.dto.GithubContentResponseDto
import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.autoconfigure.info.ProjectInfoProperties
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.Base64
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.times
import org.mockito.Mockito.`when` as whenever
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class GithubServiceTest {

    @Mock
    private lateinit var restClient: RestClient

    @Mock
    //POST/PUT client specifications in headers
    private lateinit var uriSpecs: RestClient.RequestHeadersUriSpec<*>

    @Mock
    //GET/DELETE client specifications in headers
    private lateinit var headerSpecs: RestClient.RequestHeadersSpec<*>

    @Mock
    private lateinit var responseSpec: RestClient.ResponseSpec

    @BeforeEach
    fun setUp() {
        // Chain the basic flow: get() -> uri(...) -> header(...) -> retrieve()
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>())))
    }

    @Test
    fun `when github token is present`() {
        setupRestMocks()

        mockStatic(System::class.java).use { mockedSystem ->
            mockedSystem.`when`<String> { System.getenv("GITHUB_TOKEN") }
                .thenReturn("ghp_myValidToken123")

            val serviceGithub = GithubService()
            serviceGithub.listFilesGithub()

            verify(headerSpecs).header("Authorization", "Bearer ghp_myValidToken123")
        }
    }

    @Test
    fun `when github token is absent`() {
        setupRestMocks()

        mockStatic(System::class.java).use { mockedSystem ->
            mockedSystem.`when`<String> { System.getenv("GITHUB_TOKEN") }
                .thenReturn(null)

            val serviceGithub = GithubService()
            serviceGithub.listFilesGithub()

            verify(headerSpecs).header("Authorization", "Bearer ")
        }
    }
    @Test
    fun `HTTP GET call when body is not null`() {
        val mockDto = GithubContentResponseDto(name = "file.kt", downloadUrl = "http://...", sha = "123", type = "file")
       val responseEntity = ResponseEntity.ok(mockDto)
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(responseEntity)

        val result = GithubService.listFilesGithub()
        assertEquals(1, result.size)
        assertEquals(mockDto.name, result[0].name)
    }

    @Test
    fun `HTTP GET call when body is null parameter`() {
        //empty parameter instead of null, to prevent null exception
        val responseEntityWithNullBody = ResponseEntity<List<GithubContentResponseDto<>>>
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>())))

        val result = GithubService.listFilesGithub()
        assertEquals(result.isEmpty())
    }

    @Test
    fun `HTTP GET returned on timeout or generic-network error`() {
        //network failure should return empty body
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenThrow("Runtime excpetion")

        val result = GithubService.listFilesGithub()
        assertTrue(result.isEmpty())
    }


    @Test
    fun `listUserPackages - includes non-md files and excludes md files`() {
        val rootItem1 = GithubContentResponseDto(name = "package.zip", path = "uploads/john/package.zip", type = "file")
        val rootItem2 = GithubContentResponseDto(name = "README.md", path = "uploads/john/README.md", type = "file")

        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))))
            .thenReturn(listOf(rootItem1, rootItem2))

        val result = GithubService.listUserPackages("john")
        assertEquals(1,result.size)
        assertEquals("package.zip", result[0].name)
    }

    @Test
    fun `listUserPackages - recursively walks directories`() {
        val rootDirectory = GithubContentResponseDto(name = "subfolder", path = "uploads/john/subfolder", type = "dir")
        val nestedFile = GithubContentResponseDto(name = "app.tar.gz", path = "uploads/john/subfolder/app.tar.gz", type = "file")

        whenever(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(listOf(rootDirectory)) // Call 1 (uploads/john)
            .thenReturn(listOf(nestedFile))    // Call 2 (uploads/john/subfolder)

        val result = simpleService.listUserPackages("john")

        assertEquals(1, result.size)
        assertEquals("app.tar.gz", result[0].name)

        verify(requestHeadersUriSpec, times(2)).uri(any<String>())
    }

    @Test
    fun `listUserPackages - encodes spaces in username path correctly`() {
        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(emptyList())

        simpleService.listUserPackages("john doe")

        verify(requestHeadersUriSpec).uri(argThat { contains("uploads/john%20doe") })
    }

    @Test
    fun `listUserPackages - handles exception during directory walk gracefully`() {
        //TypeErasure, that's why to use ParamaterizeTR
        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenThrow(RuntimeException("Network error"))

        val result = simpleService.listUserPackages("john")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryFilesGithub - decodes base64 searchables and filters by extension correctly`() {
        //becauase we are asking for request body
        val fakSearchable = """
            [
                {"lang": "kt", "url": "https://api.github.com/repos/owner/repo/contents/uploads/Main.kt"},
                {"lang": "java", "url": "https://api.github.com/repos/owner/repo/contents/uploads/App.java"}
            ]
        """.trimIndent()

        val base64EncodeContent = Base64.getEncoder().encodeToString(fakSearchable.toByteArray())
        val mockResponse = GithubContentResponseDto(content = base64EncodeContent)

        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)

        val result = GithubService.queryFilesGithub("*kt") as List<Map<String, Any>>
        assertEquals(1, result.size)

        val firstMatch = result[0]
        assertEquals("Main.kt", firstMatch["name"])
        assertEquals("uploads/Main.kt", firstMatch["path"])
        assertEquals("file", firstMatch["type"])
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/uploads/Main.kt", firstMatch["download_url"])

    }

    @Test
    fun `queryFilesGithub - handles corrupted base64 or JSON`() {
        val mockResponse = GithubContentResponseDto(content = "corruptedBase64")
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)))
        val result = GithubService.queryFilesGithub("*kt")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createUserDahsboard - creadentials are empty`() {
        val emptyCredentials = ""
        val result = GithubService.createUserDashboard(emptyCredentials)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createUserDahsboard - credentials are present`() {
        val username = "john_dough"

        mockServer.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        mockServer.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.CREATED))

        service.createUserDashboard(username = username)

        //resolves the async issue
        Thread.sleep(500)

        mockServer.verify()
    }

    @Test
    fun `createUserDahsboard - catch for 4xx`() {

    }

    @Test
    fun `postEndpoints -`() {

    }
    
}
