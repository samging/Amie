package org.example.amiepackagerepository.shared.integration.github.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.amiepackagerepository.shared.integration.github.dto.GithubContentResponseDto
import org.example.amiepackagerepository.shared.integration.github.dto.GithubEndpointDto
import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.example.amiepackagerepository.shared.integration.github.dto.GithubSearchableDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Answers
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.Logger
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.util.Base64


@ExtendWith(MockitoExtension::class)
@Suppress("UNCHECKED_CAST", "NewApi")
class githubServiceTest {

    @Mock
    private lateinit var restClient: RestClient

    @Mock
    private lateinit var requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*>

    @Mock
    private lateinit var requestHeadersSpec: RestClient.RequestHeadersSpec<*>

    @Mock
    private lateinit var responseSpec: RestClient.ResponseSpec

    @Mock
    private lateinit var requestBodyUriSpec: RestClient.RequestBodyUriSpec

    @Mock
    private lateinit var requestBodySpec: RestClient.RequestBodySpec

    private val headerSpecs get() = requestHeadersSpec

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockServer: MockRestServiceServer

    @Mock
    private lateinit var logger: Logger

    @Spy
    @InjectMocks
    private lateinit var githubService: GithubService

    private val service get() = githubService

    private val githubApiBase = "https://api.github.com/repos"
    private val owner = "owner"
    private val name = "repository"
    private val urlSegment = "contents"
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
    }

    @Test
    fun `when github token is present`() {
        setUp()

        mockStatic(System::class.java).use { mockedSystem ->
            mockedSystem.`when`<String> { System.getenv("GITHUB_TOKEN") }
                .thenReturn("ghp_myValidToken123")

            githubService.listFilesGithub()

            verify(headerSpecs).header("Authorization", "Bearer ghp_myValidToken123")
        }
    }

    @Test
    fun `when github token is absent`() {
        setUp()

        mockStatic(System::class.java).use { mockedSystem ->
            mockedSystem.`when`<String> { System.getenv("GITHUB_TOKEN") }
                .thenReturn(null)

            githubService.listFilesGithub()

            verify(headerSpecs).header("Authorization", "Bearer ")
        }
    }

    @Test
    fun `HTTP GET call when body is not null`() {
        val mockDto = GithubContentResponseDto(
            name = "file.kt", path = "file.kt", sha = "123", size = 0L, url = "http://...", htmlUrl = "http://...", downloadUrl = "http://...", type = "file"
        )
        val responseEntity = ResponseEntity.ok(listOf(mockDto))
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(responseEntity)

        val result = githubService.listFilesGithub()
        assertEquals(1, result.size)
        assertEquals(mockDto.name, result[0].name)
    }

    @Test
    fun `HTTP GET call when body is null parameter`() {
        val responseEntityWithNullBody: ResponseEntity<List<GithubContentResponseDto>> = ResponseEntity.ok(null)
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(responseEntityWithNullBody)

        val result = githubService.listFilesGithub()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `HTTP GET returned on timeout or generic-network error`() {
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenThrow(RuntimeException("Runtime excpetion"))

        val result = githubService.listFilesGithub()
        assertTrue(result.isEmpty())
    }


    @Test
    fun `listUserPackages - includes non-md files and excludes md files`() {
        val rootItem1 = GithubContentResponseDto(
            name = "package.zip",
            path = "uploads/john/package.zip",
            sha = "9e107d9d372bb6826bd81d3542a419d6e2179ef3",
            size = 1048576,
            url = "https://api.github.com/repos/owner/repository/contents/uploads/john/package.zip?ref=main",
            htmlUrl = "https://github.com/owner/repository/blob/main/uploads/john/package.zip",
            downloadUrl = "https://raw.githubusercontent.com/owner/repository/main/uploads/john/package.zip",
            type = "file"
        )

        val rootItem2 = GithubContentResponseDto(
            name = "README.md",
            path = "uploads/john/README.md",
            sha = "3d2180cc10a192ee1b8227d65992928c039f9958",
            size = 33,
            url = "https://api.github.com/repos/owner/repository/contents/uploads/john/README.md?ref=main",
            htmlUrl = "https://github.com/owner/repository/blob/main/uploads/john/README.md",
            downloadUrl = "https://raw.githubusercontent.com/owner/repository/main/uploads/john/README.md",
            type = "file"
        )

        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(listOf(rootItem1, rootItem2))

        val result = githubService.listUserPackages("john")
        assertEquals(1, result.size)
        assertEquals("package.zip", result[0].name)
    }

    @Test
    fun `listUserPackages - recursively walks directories`() {

        val rootDirectory = GithubContentResponseDto(
            name = "subfolder", path = "uploads/john/subfolder", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "dir"
        )

        val nestedFile = GithubContentResponseDto(
            name = "app.tar.gz", path = "uploads/john/subfolder/app.tar.gz", sha = "456", size = 100L, url = "", htmlUrl = "", downloadUrl = "", type = "file"
        )

        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(listOf(rootDirectory))
            .thenReturn(listOf(nestedFile))

        val result = githubService.listUserPackages("john")

        assertEquals(1, result.size)
        assertEquals("app.tar.gz", result[0].name)

        verify(requestHeadersUriSpec, times(2)).uri(any<String>())
    }

    @Test
    fun `listUserPackages - encodes spaces in username path correctly`() {
        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(emptyList())

        githubService.listUserPackages("john doe")

        verify(requestHeadersUriSpec).uri(argThat<String> { it.contains("uploads/john%20doe") })
    }

    @Test
    fun `listUserPackages - handles exception during directory walk gracefully`() {
        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenThrow(RuntimeException("Network error"))

        val result = githubService.listUserPackages("john")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryFilesGithub - decodes base64 searchables and filters by extension correctly`() {
        val fakSearchable = """
            [
                {"lang": "kt", "url": "https://api.github.com/repos/owner/repo/contents/uploads/Main.kt"},
                {"lang": "java", "url": "https://api.github.com/repos/owner/repo/contents/uploads/App.java"}
            ]
        """.trimIndent()

        val base64EncodeContent = Base64.getEncoder().encodeToString(fakSearchable.toByteArray())
        val mockResponse = GithubContentResponseDto(
            name = "searchables.json", path = "uploads/searchables.json", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = base64EncodeContent
        )

        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)

        val result = githubService.queryFilesGithub("*kt") as List<Map<String, Any>>
        assertEquals(1, result.size)

        val firstMatch = result[0]
        assertEquals("Main.kt", firstMatch["name"])
        assertEquals("uploads/Main.kt", firstMatch["path"])
        assertEquals("file", firstMatch["type"])
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/uploads/Main.kt", firstMatch["download_url"])

    }

    @Test
    fun `queryFilesGithub - handles corrupted base64 or JSON`() {
        val mockResponse = GithubContentResponseDto(
            name = "searchables.json", path = "uploads/searchables.json", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = "corruptedBase64"
        )
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)
        val result = githubService.queryFilesGithub("*kt") as List<*>
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createUserDahsboard - creadentials are empty`() {
        val emptyCredentials = ""
        githubService.createUserDashboard(username = emptyCredentials)
    }

    @Test
    fun `createUserDahsboard - credentials are present`() {
        val username = "john_dough"
        val expectedUrl = "$githubApiBase/$owner/codeRepository/$urlSegment/uploads/$username/README.md"

        mockServer.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        mockServer.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.CREATED))

        service.createUserDashboard(username = username)

        Thread.sleep(500)

        mockServer.verify()
    }

    @Test
    fun `createUserDahsboard - catch for 4xx`() {

    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 422])
    fun `fetchEndpoints returns emptyMap for various 4xx client errors`(statusCode: Int) {
        val httpStatus = HttpStatus.valueOf(statusCode)

        val clientException = HttpClientErrorException.create(
            httpStatus,
            httpStatus.reasonPhrase,
            HttpHeaders.EMPTY,
            ByteArray(0),
            null
        )

        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenThrow(clientException)

        val result = service.fetchEndpoints()

        assertTrue(result.isEmpty())
        verify(logger).error(eq("Error fetching endpoints: {}"), any<Any>())
    }

    @Test
    fun `fetchEndpoints - stub json response on serialization`() {
        val rawJson = """
            [
                {"retailName": "Device A", "descriptiveName": "Description A"},
                {"retailName": "Device B", "descriptiveName": "Description B"}
            ]
        """.trimIndent()

        val mockResponse = GithubContentResponseDto(
            name = "repositoryInformations", path = "repositoryInformations", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = Base64.getEncoder().encodeToString(rawJson.toByteArray())
        )
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)

        val result = githubService.fetchEndpoints()

        assertEquals(2, result.size)
        assertEquals("Description A", result["Device A"])
        assertEquals("Description B", result["Device B"])
    }

    @Test
    fun `fetchEndpoints - parsing should fail in this case`() {
        val rawJson = """
            {
                "Device C": "Description C",
                "Device D": "Description D"
            }
        """.trimIndent()

        val mockResponse = GithubContentResponseDto(
            name = "repositoryInformations", path = "repositoryInformations", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = Base64.getEncoder().encodeToString(rawJson.toByteArray())
        )

        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(mockResponse)

        val result = githubService.fetchEndpoints()

        assertEquals(2, result.size)
        assertEquals("Description C", result["Device C"])

        verify(logger).warn(
            eq("Failed to parse as List<EndpointDto>, trying Map<String, String>: {}"),
            any<String>()
        )
    }

    @Test
    fun `fetchEndpoints - Failure Path returns emptyMap when JSON is completely invalid`() {
        val notJson = "Stringful string"
        val mockResponse = GithubContentResponseDto(
            name = "repositoryInformations", path = "repositoryInformations", sha = "123", size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = Base64.getEncoder().encodeToString(notJson.toByteArray())
        )

        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)
        val result = githubService.fetchEndpoints()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `uploadFile - Path with Username`() {
        val username = "john_doe"
        val progLanguage = "kt"
        val file = mock(MultipartFile::class.java)

        val safeUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20")
        val safeLang = URLEncoder.encode(progLanguage, "UTF-8").replace("+", "%20")
        val safeFileName = URLEncoder.encode("Main.kt", "UTF-8").replace("+", "%20")

        `when`(file.originalFilename).thenReturn("Main.kt")
        `when`(file.bytes).thenReturn(ByteArray(0))
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok("ok"))

        githubService.uploadFile(username, progLanguage, file)

        val expectedPath = if (username.isNotBlank()) "uploads/$safeUsername/$safeLang/$safeFileName" else "uploads/$safeLang/$safeFileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val encodedUrl = URLEncoder.encode(expectedUrl, "UTF-8").replace("+", "%20")
        verify(requestBodyUriSpec).uri(urlCaptor.capture())
        assertEquals(expectedUrl, urlCaptor.value)
    }

    @Test
    fun `uploadFile - Path without Username`() {
        val username = ""
        val progLanguage = "kt"
        val file = mock(MultipartFile::class.java)

        val safeLang = URLEncoder.encode(progLanguage, "UTF-8").replace("+", "%20")
        val safeFileName = URLEncoder.encode("Main.kt", "UTF-8").replace("+", "%20")

        `when`(file.originalFilename).thenReturn("Main.kt")
        `when`(file.bytes).thenReturn(ByteArray(0))
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok("ok"))

        githubService.uploadFile(username, progLanguage, file)

        val expectedPath = "uploads/$safeLang/$safeFileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val encodedUrl = URLEncoder.encode(expectedUrl, "UTF-8").replace("+", "%20")
        verify(requestBodyUriSpec).uri(urlCaptor.capture())
        assertEquals(expectedUrl, urlCaptor.value)
    }

    @Test
    fun `uploadFile - null fileName`() {
        val username = "john_doe"
        val progLanguage = "kt"
        val file = mock(MultipartFile::class.java)
        `when`(file.originalFilename).thenReturn(null)
        `when`(file.bytes).thenReturn(ByteArray(0))

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok("ok"))

        githubService.uploadFile(username, progLanguage, file)
    }


    @Test
    fun `uploadFile - SHA check OK`() {
        val expectedSha = "existing-file-sha-123"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val mockShaDto = GithubContentResponseDto(
            name = "script.kt", path = "uploads/john/kotlin/script.kt", sha = expectedSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file"
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(ResponseEntity.ok(mockShaDto))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("success"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).info(eq("GITHUB: File exists, retrieved SHA: {}"), eq(expectedSha) as Any)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.allValues.last() as Map<String, String>
        assertEquals(expectedSha, finalUploadBody["sha"])
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - SHA check Failed or Exception`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val exceptionMessage = "404 Not Found"

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException(exceptionMessage))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("success"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).info(eq("GITHUB: Initial file check info (not necessarily an error): {}"), eq(exceptionMessage) as Any)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.allValues.last() as Map<String, String>

        assertNull(finalUploadBody["sha"])
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - searchables - creates new searchables list when searchables json returns 404 or throws`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).info("GITHUB: searchables.json not found (will create new)")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val searchablesPutBody = mapCaptor.allValues.first() as Map<String, String>
        val content = searchablesPutBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))

        val generatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)
        assertEquals(1, generatedList.size)
        assertEquals("kotlin", generatedList[0].lang)
        assertNull(searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - updates and saves existing list when searchables json contains list`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val existingDto = GithubSearchableDto(
            url = "https://api.github.com/test-owner/test-repo/contents/uploads/alice/java/Main.java",
            lang = "java"
        )
        val existingListJson = json.encodeToString(listOf(existingDto))
        val existingContentBase64 = Base64.getEncoder().encodeToString(existingListJson.toByteArray())
        val searchablesSha = "searchables-sha-789"

        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                name = "searchables.json", path = "uploads/searchables.json", sha = searchablesSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = existingContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).info(eq("GITHUB: Existing searchables.json found, SHA: {}"), eq(searchablesSha) as Any)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val searchablesPutBody = mapCaptor.allValues.first() as Map<String, String>
        val content = searchablesPutBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))
        val updatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        assertEquals(2, updatedList.size)
        assertEquals("java", updatedList[0].lang)
        assertEquals("kotlin", updatedList[1].lang)

        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - converts single object to list and saves when searchables json contains single object`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val singleDto = GithubSearchableDto(
            url = "https://api.github.com/test-owner/test-repo/contents/uploads/alice/java/Main.java",
            lang = "java"
        )
        val singleObjectJson = json.encodeToString(singleDto)
        val encodedContentBase64 = Base64.getEncoder().encodeToString(singleObjectJson.toByteArray())
        val searchablesSha = "single-obj-sha-456"

        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                name = "searchables.json", path = "uploads/searchables.json", sha = searchablesSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = encodedContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val searchablesPutBody = mapCaptor.allValues.first() as Map<String, String>
        val content = searchablesPutBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        assertEquals(2, resultList.size)
        assertEquals("java", resultList[0].lang)
        assertEquals("kotlin", resultList[1].lang)
        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - falls back to fresh list when searchables json is corrupted`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val corruptedContent = "This is not valid JSON content"
        val corruptedBase64 = Base64.getEncoder().encodeToString(corruptedContent.toByteArray())
        val searchablesSha = "corrupted-json-sha-999"

        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                name = "searchables.json", path = "uploads/searchables.json", sha = searchablesSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = corruptedBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).warn(
            eq("GITHUB: Could not parse existing searchables.json, starting fresh list: {}"),
            any<String>()
        )

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val searchablesPutBody = mapCaptor.allValues.first() as Map<String, String>
        val content = searchablesPutBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        assertEquals(1, resultList.size)
        assertEquals("kotlin", resultList[0].lang)
        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - replaces entry with matching url instead of duplicating`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        val targetUrl = "https://api.github.com/test-owner/test-repo/contents/uploads/john/kotlin/script.kt"

        val duplicateEntry = GithubSearchableDto(
            url = targetUrl,
            lang = "old-kotlin"
        )

        val existingListJson = json.encodeToString(listOf(duplicateEntry))
        val existingContentBase64 = Base64.getEncoder().encodeToString(existingListJson.toByteArray())
        val searchablesSha = "dedup-sha-123"

        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                name = "searchables.json", path = "uploads/searchables.json", sha = searchablesSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file", content = existingContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val searchablesPutBody = mapCaptor.allValues.first() as Map<String, String>
        val content = searchablesPutBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        assertEquals(1, resultList.size)
        assertEquals(targetUrl, resultList[0].url)
        assertEquals("kotlin", resultList[0].lang)
    }

    @Test
    fun `uploadFile - searchables - skips searchables logic on 409 conflict and proceeds to main upload`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.CONFLICT).body("Conflict")) // patchSearchTree
            .thenReturn(ResponseEntity.ok("uploaded"))                                 // final upload

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).warn("GITHUB: Conflict updating searchables.json. Someone else updated it. Skipping metadata sync for this file.")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.allValues.last() as Map<String, String>
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - searchables - sends create put request when patchSearchTree returns 404`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        `when`(responseSpec.toBodilessEntity())
            .thenReturn(ResponseEntity.ok().build())

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).info("GITHUB: searchables.json not found, creating new")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, times(3)).body(mapCaptor.capture())

        val createSearchablesBody = mapCaptor.allValues[1] as Map<String, String>
        assertEquals("Create searchables.json", createSearchablesBody["message"])

        val content = createSearchablesBody["content"]!!
        val decodedContent = String(Base64.getDecoder().decode(content))
        val generatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)
        assertEquals(1, generatedList.size)
        assertEquals("kotlin", generatedList[0].lang)
    }

    @Test
    fun `uploadFile - searchables - logs warning and proceeds to main upload when searchables logic throws exception`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val searchablesExceptionMessage = "Connection reset by peer"

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException(searchablesExceptionMessage))
            .thenReturn(ResponseEntity.ok("uploaded"))

        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        verify(logger).warn(
            eq("GITHUB: Experimental searchables logic failed (non-fatal): {}"),
            eq(searchablesExceptionMessage) as Any
        )

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.allValues.last() as Map<String, String>
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - final upload - rethrows exception when final put request fails`() {
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val criticalErrorMessage = "500 Internal Server Error"

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenThrow(RuntimeException(criticalErrorMessage))

        val exception = assertThrows<RuntimeException> {
            service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)
        }

        assertEquals(criticalErrorMessage, exception.message)

        val expectedPath = "uploads/john/kotlin/script.kt"
        verify(logger).error(
            eq("GITHUB: CRITICAL upload error at path {}: {}"),
            eq(expectedPath) as Any,
            eq(criticalErrorMessage) as Any
        )
    }

    @Test
    fun `uploadFile - final upload - completes successfully on 200 or 201 response`() {
        val username = "john"
        val progLanguage = "kotlin"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val expectedPath = "uploads/john/kotlin/script.kt"

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"content\": {}}"))

        service.uploadFile(username = username, progLanguage = progLanguage, file = mockFile)

        verify(logger).info(
            eq("GITHUB: Upload successful for user '{}' at path '{}'! Status: {}"),
            eq(username) as Any,
            eq(expectedPath) as Any,
            eq(HttpStatus.CREATED.value()) as Any
        )

        verify(logger).info("--- [SimpleService: uploadFile] END ---")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.allValues.last() as Map<String, String>
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - final upload - rethrows exception when final put request fails 2`() {
        val username = "john"
        val progLanguage = "kotlin"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val criticalErrorMessage = "500 Internal Server Error"
        val expectedPath = "uploads/john/kotlin/script.kt"

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))                  // searchables succeeds
            .thenThrow(RuntimeException(criticalErrorMessage))          // final upload fails critically

        val exception = assertThrows<RuntimeException> {
            service.uploadFile(username = username, progLanguage = progLanguage, file = mockFile)
        }

        assertEquals(criticalErrorMessage, exception.message)

        verify(logger).error(
            eq("GITHUB: CRITICAL upload error at path {}: {}"),
            eq(expectedPath) as Any,
            eq(criticalErrorMessage) as Any
        )

        verify(logger).info("--- [SimpleService: uploadFile] END ---")
    }


    @Test
    fun `writeError - new file - uploads errors md without sha when existingSha is null`() {
        val username = "john"
        val error = "ERR_404"
        val message = "Resource not found"
        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedFormattedContent = "[$error]: $message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(expectedFormattedContent.toByteArray())

        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), isNull())

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        service.writeError(username = username, error = error, message = message)

        verify(logger).info("Error successfully logged to GitHub errors.md")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>

        assertEquals("Update $expectedPath via Amie Repository for $username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
        assertNull(requestBody["sha"])
    }

    @Test
    fun `writeError - existing file - includes sha in body when fetchFileSha returns non-null sha`() {
        val username = "john"
        val error = "ERR_500"
        val message = "Internal server error"
        val existingSha = "existing-error-sha-999"
        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedFormattedContent = "[$error]: $message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(expectedFormattedContent.toByteArray())

        doReturn(existingSha).`when`(service).fetchFileSha(eq(expectedUrl), isNull())

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        service.writeError(username = username, error = error, message = message)

        verify(logger).info("Error successfully logged to GitHub errors.md")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>

        assertEquals(existingSha, requestBody["sha"])
        assertEquals("Update $expectedPath via Amie Repository for $username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
    }

    @Test
    fun `writeError - path and content - encodes error message into base64 and formats github url correctly`() {
        val username = "alice"
        val error = "CUSTOM_ERR"
        val message = "Failed to parse metadata"

        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val rawFormattedText = "[$error]: $message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(rawFormattedText.toByteArray())

        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), isNull())

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        service.writeError(username = username, error = error, message = message)

        verify(requestBodyUriSpec).uri(expectedUrl)

        verify(requestBodySpec).header(eq("Authorization"), startsWith("Bearer "))

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>
        val encodedContent = requestBody["content"]!! //[Human] Explicit assertion

        assertEquals(expectedBase64Content, encodedContent)
        assertEquals(rawFormattedText, String(Base64.getDecoder().decode(encodedContent)))
    }

    @Test
    fun `writeError - upload failure - catches exception and logs error when restClient put fails`() {
        val username = "john"
        val error = "ERR_500"
        val message = "Internal Server Error"
        val exceptionMessage = "500 Internal Server Error"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/uploads/$username/errors.md"

        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), isNull())

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenThrow(RuntimeException(exceptionMessage))

        service.writeError(username = username, error = error, message = message)

        verify(logger).error(
            eq("Failed to log error to GitHub: {}"),
            eq(exceptionMessage) as Any
        )

        verify(logger, never()).info("Error successfully logged to GitHub errors.md")
    }

    @Test
    fun `sendEdit - happy path - fetches sha and updates file successfully on 200 or 201 response`() {
        val username = "john"
        val fileName = "script.kt"
        val existingSha = "existing-sha-12345"
        val updateFile = MockMultipartFile("file", fileName, "text/plain", "println(\"Updated\")".toByteArray())

        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64Content = Base64.getEncoder().encodeToString(updateFile.bytes)

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

        val mockDto = GithubContentResponseDto(
            name = fileName, path = expectedPath, sha = existingSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file"
        )

        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(mockDto)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.OK).body("updated"))

        service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)

        verify(requestHeadersUriSpec).uri(expectedUrl)

        verify(logger).info(
            eq("GitHub Update Success for {}! Status: {}"),
            eq(username) as Any,
            eq(HttpStatus.OK.value()) as Any
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>
        assertEquals("Update $fileName via Amie Repository for $username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
        assertEquals(existingSha, requestBody["sha"])
    }

    @Test
    fun `sendEdit - missing file - throws exception when get request returns null or 404`() {
        val username = "john"
        val fileName = "non_existent.kt"
        val updateFile = MockMultipartFile("file", fileName, "text/plain", "println()".toByteArray())
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"

        // 1. Stub RestClient GET chain returning null (simulating missing file body)
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(null)

        val exceptionNull = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        assertEquals("File not found for update: $expectedPath", exceptionNull.message)

        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username) as Any,
            eq("File not found for update: $expectedPath") as Any
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        clearInvocations(logger)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        val exception404 = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        assertEquals("404 Not Found", exception404.message)

        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username) as Any,
            eq("404 Not Found") as Any
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        verify(restClient, never()).put()
    }

    @Test
    fun `sendEdit - path encoding - correctly constructs and encodes url path with and without username`() {
        val mockFile = MockMultipartFile("file", "test.kt", "text/plain", "println()".toByteArray())
        val sha = "sha-abc-123"
        val mockDto = GithubContentResponseDto(
            name = "test.kt", path = "uploads/john/test.kt", sha = sha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file"
        )

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockDto)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok("ok"))

        val usernameWithSpaces = "john doe"
        val fileWithSpaces = "my script.kt"
        val expectedEncodedUrlWithUser = "$githubApiBase/$owner/$name/$urlSegment/uploads/john%20doe/my%20script.kt"

        service.sendEdit(username = usernameWithSpaces, fileName = fileWithSpaces, updateFile = mockFile)

        verify(requestHeadersUriSpec).uri(expectedEncodedUrlWithUser)
        verify(requestBodyUriSpec).uri(expectedEncodedUrlWithUser)

        clearInvocations(requestHeadersUriSpec, requestBodyUriSpec)

        val blankUsername = "   "
        val fileName = "global_config.json"
        val expectedEncodedUrlNoUser = "$githubApiBase/$owner/$name/$urlSegment/uploads/global_config.json"

        service.sendEdit(username = blankUsername, fileName = fileName, updateFile = mockFile)

        verify(requestHeadersUriSpec).uri(expectedEncodedUrlNoUser)
        verify(requestBodyUriSpec).uri(expectedEncodedUrlNoUser)
    }

    @Test
    fun `sendEdit - put request failure - catches, logs critical error, and rethrows exception when put fails`() {
        val username = "john"
        val fileName = "script.kt"
        val existingSha = "existing-sha-12345"
        val updateFile = MockMultipartFile("file", fileName, "text/plain", "println()".toByteArray())
        val putErrorMessage = "500 Internal Server Error"

        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val mockDto = GithubContentResponseDto(
            name = fileName, path = expectedPath, sha = existingSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file"
        )

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(mockDto)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException(putErrorMessage))

        val exception = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        assertEquals(putErrorMessage, exception.message)

        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username) as Any,
            eq(putErrorMessage) as Any
        )

        verify(logger).info("--- [SimpleService: sendEdit] END ---")
    }

    @Test
    fun `uploadFileData - new file - uploads raw bytes without sha when initial get check returns null or throws`() {
        val username = "john"
        val fileName = "data.bin"
        val data = "raw payload content".toByteArray()
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64 = Base64.getEncoder().encodeToString(data)

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        service.uploadFileData(username = username, fileName = fileName, data = data)

        verify(logger).info("No existing file found for raw upload (this is normal for new files)")
        verify(logger).info("GitHub raw data sync successful for '{}'", fileName)
        verify(logger).info("--- [SimpleService: uploadFileData] END ---")

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>
        assertEquals("Update $fileName via Amie Device Manager", requestBody["message"])
        assertEquals(expectedBase64, requestBody["content"])
        assertNull(requestBody["sha"])
    }

    @Test
    fun `uploadFileData - existing file - attaches sha to put body when existing file is found`() {
        val username = "john"
        val fileName = "config.json"
        val data = "{\"key\":\"value\"}".toByteArray()
        val existingSha = "raw-data-sha-555"
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64 = Base64.getEncoder().encodeToString(data)
        val mockDto = GithubContentResponseDto(
            name = fileName, path = expectedPath, sha = existingSha, size = 0L, url = "", htmlUrl = "", downloadUrl = null, type = "file"
        )

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(mockDto)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        service.uploadFileData(username = username, fileName = fileName, data = data)

        verify(logger).info("Retrieved SHA: {}", existingSha)
        verify(logger).info("GitHub raw data sync successful for '{}'", fileName)

        val mapCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.value as Map<String, String>
        assertEquals("Update $fileName via Amie Device Manager", requestBody["message"])
        assertEquals(expectedBase64, requestBody["content"])
        assertEquals(existingSha, requestBody["sha"])
    }

    @Test
    fun `uploadFileData - upload error - catches exception and logs debug error when put request fails`() {
        val username = "john"
        val fileName = "data.bin"
        val data = "raw payload content".toByteArray()
        val errorMessage = "500 Internal Server Error"
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(null)

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenThrow(RuntimeException(errorMessage))

        service.uploadFileData(username = username, fileName = fileName, data = data)

        verify(logger).error(
            eq("DEBUG: GitHub data sync failed for '{}': {}"),
            eq(fileName) as Any,
            eq(errorMessage) as Any
        )
        verify(logger).info("--- [SimpleService: uploadFileData] END ---")
    }

}
