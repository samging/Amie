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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.Base64
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.times
import org.springframework.http.HttpHeaders
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import kotlin.test.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile


@ExtendWith(MockitoExtension::class)
class githubServiceTest {

    @Mock
    private lateinit var restClient: RestClient

    @Mock
    //POST/PUT client specifications in headers
    private lateinit var requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*>

    @Mock
    //GET/DELETE client specifications in headers
    private lateinit var requestHeadersSpec: RestClient.RequestHeadersSpec<*>

    @Mock
    private lateinit var responseSpec: RestClient.ResponseSpec

    @Mock
    private lateinit var githubService: GithubService

    @BeforeEach
    fun setUp() {
        val mockServer = MockRestServiceServer.bindTo(restClient).build()
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>())))
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
        val mockDto = GithubContentResponseDto(name = "file.kt", downloadUrl = "http://...", sha = "123", type = "file")
       val responseEntity = ResponseEntity.ok(mockDto)
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(responseEntity)

        val result = githubService.listFilesGithub()
        assertEquals(1, result.size)
        assertEquals(mockDto.name, result[0].name)
    }

    @Test
    fun `HTTP GET call when body is null parameter`() {
        val responseEntityWithNullBody = ResponseEntity<List<GithubContentResponseDto<>>>
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>())))

        val result = githubService.listFilesGithub()
        assertEquals(result.isEmpty())
    }

    @Test
    fun `HTTP GET returned on timeout or generic-network error`() {
        `when`(responseSpec.toEntity(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenThrow("Runtime excpetion")

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

        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))))
            .thenReturn(listOf(rootItem1, rootItem2))

        val result = githubService.listUserPackages("john")
        assertEquals(1,result.size)
        assertEquals("package.zip", result[0].name)
    }

    @Test
    fun `listUserPackages - recursively walks directories`() {
        val rootDirectory = GithubContentResponseDto(name = "subfolder", path = "uploads/john/subfolder", type = "dir")
        val nestedFile = GithubContentResponseDto(name = "app.tar.gz", path = "uploads/john/subfolder/app.tar.gz", type = "file")

        `when`(responseSpec.body(any<ParameterizedTypeReference<List<GithubContentResponseDto>>>()))
            .thenReturn(listOf(rootDirectory)) // Call 1 (uploads/john)
            .thenReturn(listOf(nestedFile))    // Call 2 (uploads/john/subfolder)

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

        verify(requestHeadersUriSpec).uri(argThat { contains("uploads/john%20doe") })
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
        val mockResponse = GithubContentResponseDto(content = base64EncodeContent)

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
        val mockResponse = GithubContentResponseDto(content = "corruptedBase64")
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)))
        val result = githubService.queryFilesGithub("*kt")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createUserDahsboard - creadentials are empty`() {
        val emptyCredentials = ""
        val result = githubService.createUserDashboard(emptyCredentials)
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
        verify(logger).error(eq("Error fetching endpoints: {}"), any())
    }

    @Test
    fun `fetchEndpoints - stub json response on serialization`() {
        val rawJson = """
            [
                {"retailName": "Device A", "descriptiveName": "Description A"},
                {"retailName": "Device B", "descriptiveName": "Description B"}
            ]
        """.trimIndent()

        val mockResponse = GithubContentResponseDto(content = Base64.getEncoder().encodeToString(rawJson.toByteArray()))
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)

        val result = githubService.fetchEndpoints()

        assertEquals(2, result.size)
        assertEquals("Description A", result["Device A"])
        assertEquals("Description B", result["Device B"])
    }

    @Test
    fun `fetchEndpoints - parsing should fail in this case`() {
        //internal fallback try-catch logic
        val rawJson = """
            {
                "Device C": "Description C",
                "Device D": "Description D"
            }
        """.trimIndent()

        val mockResponse = GithubContentResponseDto(content = Base64.getEncoder().encodeToString(rawJson))
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

    fun `fetchEndpoints - Failure Path returns emptyMap when JSON is completely invalid`(){
        val notJson = "Stringful string"
        val mockResponse = GithubContentResponseDto(content = Base64.getEncoder().encodeToString(notJson.toByteArray()))

        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(mockResponse)
        val result = githubService.fetchEndpoints()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `uploadFile - Path with Username`() {
        val username = "john_doe"
        val progLanguage = "kt"
        val file = mock(MultipartFile::class.java)

        `when`(file.originalFilename).thenReturn("Main.kt")
        `when`(file.bytes).thenReturn(ByteArray(0))
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)

        githubService.uploadFile(username, progLanguage, file)
        verify(restClient).put(urlCaptor.capture(), any())

        val expectedPath = if (username.isNotBlank()) "uploads/$safeUsername/$safeLang/$safeFileName" else "uploads/$safeLang/$safeFileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val encodedUrl = URLEncoder.encode(expectedUrl, "UTF-8").replace("+", "%20")
        assertEquals(expectedUrl, urlCaptor.firstValue)
    }

    @Test
    fun `uploadFile - Path without Username`() {
        val username = "john_doe"
        val progLanguage = "kt"
        val file = mock(MultipartFile::class.java)

        `when`(file.originalFilename).thenReturn("Main.kt")
        `when`(file.bytes).thenReturn(ByteArray(0))
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)

        githubService.uploadFile(username, progLanguage, file)
        verify(restClient).put(urlCaptor.capture(), any())

        val expectedPath = "uploads/$safeLang/$safeFileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val encodedUrl = URLEncoder.encode(expectedUrl, "UTF-8").replace("+", "%20")
        assertEquals(expectedUrl, urlCaptor.firstValue)
    }

    @Test
    fun `uploadFile - null fileName`() {
        //maybe undone implementation in the code..
        val username = "john_doe"
        val progLanguage = "kt"
        val file = null

        val result = githubService.uploadFile(username, progLanguage, file)
        assertTrue(result.isEmpty())
    }


    //Attemtping for testing SHA check
    @Test
    fun `uploadFile - SHA check OK`() {
        // Arrange
        val expectedSha = "existing-file-sha-123"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // 1. Stub initial GET check to return an existing SHA
        val mockShaDto = GithubContentResponseDto(sha = expectedSha)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(ResponseEntity.ok(mockShaDto))

        // 2. Stub PUT responses (for searchables patch + final upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("success"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify logger recorded retrieving the existing SHA
        verify(logger).info(eq("GITHUB: File exists, retrieved SHA: {}"), eq(expectedSha))

        // Capture the body sent in the final upload PUT request to verify "sha" key was included
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.lastValue
        assertEquals(expectedSha, finalUploadBody["sha"])
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - SHA check Failed or Exception`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val exceptionMessage = "404 Not Found"

        // 1. Stub initial GET check to throw an exception (e.g. 404 file doesn't exist)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException(exceptionMessage))

        // 2. Stub PUT responses (for searchables patch + final upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("success"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify non-fatal log was captured for the initial check failure
        verify(logger).info(eq("GITHUB: Initial file check info (not necessarily an error): {}"), eq(exceptionMessage))

        // Capture the body sent in the final upload PUT request
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.lastValue

        // Verify "sha" is null/not present in payload
        assertNull(finalUploadBody["sha"])
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }
    // --- 3. searchables.json Metadata Parsing & Patching ---

    @Test
    fun `uploadFile - searchables - creates new searchables list when searchables json returns 404 or throws`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // 1. Initial file check returns no existing SHA (404/new file)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. PUT response stubs (searchables patch response + final upload response)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify fallback log was triggered for searchables
        verify(logger).info("GITHUB: searchables.json not found (will create new)")

        // Capture PUT payloads to inspect searchables payload
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        // First PUT corresponds to patchSearchTree
        val searchablesPutBody = mapCaptor.firstValue
        val decodedContent = String(Base64.getDecoder().decode(searchablesPutBody["content"]))

        // Verify a fresh JSON list with 1 element was generated
        val generatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)
        assertEquals(1, generatedList.size)
        assertEquals("kotlin", generatedList[0].lang)
        assertNull(searchablesPutBody["sha"]) // No existing SHA sent for searchables
    }

    @Test
    fun `uploadFile - searchables - updates and saves existing list when searchables json contains list`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // Existing list on GitHub with 1 item
        val existingDto = GithubSearchableDto(
            url = "https://api.github.com/test-owner/test-repo/contents/uploads/alice/java/Main.java",
            lang = "java"
        )
        val existingListJson = json.encodeToString(listOf(existingDto))
        val existingContentBase64 = Base64.getEncoder().encodeToString(existingListJson.toByteArray())
        val searchablesSha = "searchables-sha-789"

        // 1. Stub initial GET check for main file (throws 404 / new file)
        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        // 2. Stub GET check for searchables.json (returns existing list and SHA)
        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                sha = searchablesSha,
                content = existingContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        // 3. Stub PUT responses (for searchables patch + final upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify logger captured finding the existing searchables.json
        verify(logger).info("GITHUB: Existing searchables.json found, SHA: {}", searchablesSha)

        // Capture PUT payloads to inspect searchables payload
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        // First PUT corresponds to patchSearchTree
        val searchablesPutBody = mapCaptor.firstValue
        val decodedContent = String(Base64.getDecoder().decode(searchablesPutBody["content"]))
        val updatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        // Verify list grew to 2 items and includes both the existing and new entry
        assertEquals(2, updatedList.size)
        assertEquals("java", updatedList[0].lang)
        assertEquals("kotlin", updatedList[1].lang)

        // Verify existing SHA was passed in the searchables update body
        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - converts single object to list and saves when searchables json contains single object`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // Single JSON object (not an array) returned from GitHub
        val singleDto = GithubSearchableDto(
            url = "https://api.github.com/test-owner/test-repo/contents/uploads/alice/java/Main.java",
            lang = "java"
        )
        val singleObjectJson = json.encodeToString(singleDto)
        val encodedContentBase64 = Base64.getEncoder().encodeToString(singleObjectJson.toByteArray())
        val searchablesSha = "single-obj-sha-456"

        // 1. Stub initial GET check for main file (404 / new file)
        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        // 2. Stub GET check for searchables.json (returns single object JSON)
        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                sha = searchablesSha,
                content = encodedContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        // 3. Stub PUT responses (patchSearchTree + final file upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Capture PUT payloads to inspect searchables output
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        // First PUT corresponds to patchSearchTree
        val searchablesPutBody = mapCaptor.firstValue
        val decodedContent = String(Base64.getDecoder().decode(searchablesPutBody["content"]))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        // Verify it converted the single object into a list and appended the new item (2 items total)
        assertEquals(2, resultList.size)
        assertEquals("java", resultList[0].lang)
        assertEquals("kotlin", resultList[1].lang)
        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - falls back to fresh list when searchables json is corrupted`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // Corrupted content (valid Base64 string, but invalid JSON content)
        val corruptedContent = "This is not valid JSON content"
        val corruptedBase64 = Base64.getEncoder().encodeToString(corruptedContent.toByteArray())
        val searchablesSha = "corrupted-json-sha-999"

        // 1. Stub initial GET check for main file (404 / new file)
        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        // 2. Stub GET check for searchables.json (returns corrupted JSON content)
        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                sha = searchablesSha,
                content = corruptedBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        // 3. Stub PUT responses (patchSearchTree + final file upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify logger recorded the fallback warning
        verify(logger).warn(
            eq("GITHUB: Could not parse existing searchables.json, starting fresh list: {}"),
            any<String>()
        )

        // Capture PUT payloads to inspect searchables output
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        // First PUT corresponds to patchSearchTree
        val searchablesPutBody = mapCaptor.firstValue
        val decodedContent = String(Base64.getDecoder().decode(searchablesPutBody["content"]))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        // Verify it started fresh and created a list with only the 1 new entry
        assertEquals(1, resultList.size)
        assertEquals("kotlin", resultList[0].lang)
        assertEquals(searchablesSha, searchablesPutBody["sha"])
    }

    @Test
    fun `uploadFile - searchables - replaces entry with matching url instead of duplicating`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // Construct the exact URL that SimpleService generates for this upload
        val targetUrl = "https://api.github.com/test-owner/test-repo/contents/uploads/john/kotlin/script.kt"

        // Existing list on GitHub contains an entry with the EXACT SAME URL but an older language/metadata
        val duplicateEntry = GithubSearchableDto(
            url = targetUrl,
            lang = "old-kotlin"
        )
        val existingListJson = json.encodeToString(listOf(duplicateEntry))
        val existingContentBase64 = Base64.getEncoder().encodeToString(existingListJson.toByteArray())
        val searchablesSha = "dedup-sha-123"

        // 1. Stub initial GET check for main file (404 / new file)
        val fileCheckResponse: ResponseEntity<GithubContentResponseDto> = ResponseEntity.notFound().build()

        // 2. Stub GET check for searchables.json (returns list containing the duplicate URL entry)
        val searchablesCheckResponse = ResponseEntity.ok(
            GithubContentResponseDto(
                sha = searchablesSha,
                content = existingContentBase64
            )
        )

        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenReturn(fileCheckResponse)
            .thenReturn(searchablesCheckResponse)

        // 3. Stub PUT responses (patchSearchTree + final file upload)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))
            .thenReturn(ResponseEntity.ok("uploaded"))

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Capture PUT payloads to inspect searchables output
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, atLeastOnce()).body(mapCaptor.capture())

        // First PUT corresponds to patchSearchTree
        val searchablesPutBody = mapCaptor.firstValue
        val decodedContent = String(Base64.getDecoder().decode(searchablesPutBody["content"]))
        val resultList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)

        // Verify deduplication: list size remains 1 and entry updated to new language
        assertEquals(1, resultList.size)
        assertEquals(targetUrl, resultList[0].url)
        assertEquals("kotlin", resultList[0].lang)
    }

    // --- 4. searchables.json HTTP Status Code Handlers ---

    @Test
    fun `uploadFile - searchables - skips searchables logic on 409 conflict and proceeds to main upload`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // 1. Stub GET checks (404 for main file, 404 for searchables)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (patchSearchTree) returns 409 Conflict
        // Second PUT (final file upload) returns 200 OK
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.CONFLICT).body("Conflict")) // patchSearchTree
            .thenReturn(ResponseEntity.ok("uploaded"))                                 // final upload

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify logger captured the conflict warning
        verify(logger).warn("GITHUB: Conflict updating searchables.json. Someone else updated it. Skipping metadata sync for this file.")

        // Capture PUT bodies to confirm the main file upload still executed as the second PUT call
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.lastValue
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - searchables - sends create put request when patchSearchTree returns 404`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())

        // 1. Stub initial GET checks (404 for main file check, 404 for searchables check)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (patchSearchTree) returns 404 Not Found
        // Second PUT (creating searchables.json via toBodilessEntity)
        // Third PUT (final main file upload) returns 200 OK
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found")) // patchSearchTree
            .thenReturn(ResponseEntity.ok("uploaded"))                                  // final file upload

        `when`(responseSpec.toBodilessEntity())
            .thenReturn(ResponseEntity.ok().build())                                   // fallback creation PUT

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify logger captured the fallback creation message
        verify(logger).info("GITHUB: searchables.json not found, creating new")

        // Capture PUT bodies to confirm all 3 PUT requests were executed:
        // 1. patchSearchTree PUT
        // 2. explicit create searchables.json PUT
        // 3. final main file upload PUT
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, times(3)).body(mapCaptor.capture())

        // Verify the second PUT body was the explicit create searchables payload
        val createSearchablesBody = mapCaptor.allValues[1]
        assertEquals("Create searchables.json", createSearchablesBody["message"])

        // Verify content of createSearchablesBody is valid JSON list
        val decodedContent = String(Base64.getDecoder().decode(createSearchablesBody["content"]))
        val generatedList = json.decodeFromString<List<GithubSearchableDto>>(decodedContent)
        assertEquals(1, generatedList.size)
        assertEquals("kotlin", generatedList[0].lang)
    }

    @Test
    fun `uploadFile - searchables - logs warning and proceeds to main upload when searchables logic throws exception`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val searchablesExceptionMessage = "Connection reset by peer"

        // 1. Stub GET checks (404 for main file check, 404 for searchables check)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (patchSearchTree inside searchables block) throws an unexpected exception
        // Second PUT (final main file upload) succeeds and returns 200 OK
        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException(searchablesExceptionMessage)) // searchables PUT fails
            .thenReturn(ResponseEntity.ok("uploaded"))                 // final upload succeeds

        // Act
        service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)

        // Assert
        // Verify non-fatal warning was logged for searchables failure
        verify(logger).warn(
            eq("GITHUB: Experimental searchables logic failed (non-fatal): {}"),
            eq(searchablesExceptionMessage)
        )

        // Capture PUT payloads to verify main upload still executed
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        // The second captured body corresponds to the final main file upload PUT request
        val finalUploadBody = mapCaptor.lastValue
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - final upload - rethrows exception when final put request fails`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val criticalErrorMessage = "500 Internal Server Error"

        // 1. Stub GET checks (404 for main file check, 404 for searchables check)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (patchSearchTree inside searchables block) succeeds
        // Second PUT (final main file upload) throws a critical error
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))                  // searchables succeeds
            .thenThrow(RuntimeException(criticalErrorMessage))          // final upload fails critically

        // Act & Assert
        val exception = assertThrows<RuntimeException> {
            service.uploadFile(username = "john", progLanguage = "kotlin", file = mockFile)
        }

        // Verify exception message matches
        assertEquals(criticalErrorMessage, exception.message)

        // Verify critical error log was captured
        val expectedPath = "uploads/john/kotlin/script.kt"
        verify(logger).error(
            eq("GITHUB: CRITICAL upload error at path {}: {}"),
            eq(expectedPath),
            eq(criticalErrorMessage)
        )
    }

    @Test
    fun `uploadFile - final upload - completes successfully on 200 or 201 response`() {
        // Arrange
        val username = "john"
        val progLanguage = "kotlin"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val expectedPath = "uploads/john/kotlin/script.kt"

        // 1. Stub initial GET checks (404 for main file check, 404 for searchables check)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (patchSearchTree inside searchables block) succeeds
        // Second PUT (final main file upload) succeeds with 201 CREATED
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))                                          // searchables patch
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"content\": {}}"))   // final file upload

        // Act
        service.uploadFile(username = username, progLanguage = progLanguage, file = mockFile)

        // Assert
        // Verify final success log was recorded with path and status code (201)
        verify(logger).info(
            eq("GITHUB: Upload successful for user '{}' at path '{}'! Status: {}"),
            eq(username),
            eq(expectedPath),
            eq(HttpStatus.CREATED.value())
        )

        // Verify lifecycle log for method end
        verify(logger).info("--- [SimpleService: uploadFile] END ---")

        // Capture the final upload payload to ensure correct message and content were delivered
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec, times(2)).body(mapCaptor.capture())

        val finalUploadBody = mapCaptor.lastValue
        assertEquals("Upload script.kt via Amie Repository for john (kotlin)", finalUploadBody["message"])
        assertEquals(Base64.getEncoder().encodeToString(mockFile.bytes), finalUploadBody["content"])
    }

    @Test
    fun `uploadFile - final upload - rethrows exception when final put request fails`() {
        // Arrange
        val username = "john"
        val progLanguage = "kotlin"
        val mockFile = MockMultipartFile("file", "script.kt", "text/plain", "println()".toByteArray())
        val criticalErrorMessage = "500 Internal Server Error"
        val expectedPath = "uploads/john/kotlin/script.kt"

        // 1. Stub GET checks (404 for main file check, 404 for searchables check)
        `when`(responseSpec.toEntity(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT responses:
        // First PUT (searchables patch) succeeds
        // Second PUT (final main file upload) throws a critical exception
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.ok("patched"))                  // searchables succeeds
            .thenThrow(RuntimeException(criticalErrorMessage))          // final upload fails critically

        // Act & Assert
        val exception = assertThrows<RuntimeException> {
            service.uploadFile(username = username, progLanguage = progLanguage, file = mockFile)
        }

        // Verify exception message matches
        assertEquals(criticalErrorMessage, exception.message)

        // Verify critical error log was captured with path and error message
        verify(logger).error(
            eq("GITHUB: CRITICAL upload error at path {}: {}"),
            eq(expectedPath),
            eq(criticalErrorMessage)
        )

        // Verify finally block executed despite the exception
        verify(logger).info("--- [SimpleService: uploadFile] END ---")
    }


    @Test
    fun `writeError - new file - uploads errors md without sha when existingSha is null`() {
        // Arrange
        val username = "john"
        val error = "ERR_404"
        val message = "Resource not found"
        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedFormattedContent = "[$error]:$message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(expectedFormattedContent.toByteArray())

        // 1. Stub fetchFileSha on the service spy to return null (file does not exist)
        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), anyOrNull())

        // 2. Stub RestClient PUT chain to return a successful bodiless entity
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        // Act
        service.writeError(username = username, error = error, message = message)

        // Assert
        // Verify success log was recorded
        verify(logger).info("Error successfully logged to GitHub errors.md")

        // Capture the payload map passed into body()
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue

        // Assert payload values and verify 'sha' key is absent
        assertEquals("Update $expectedPath via Amie Repository for$username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
        assertNull(requestBody["sha"])
    }

    @Test
    fun `writeError - existing file - includes sha in body when fetchFileSha returns non-null sha`() {
        // Arrange
        val username = "john"
        val error = "ERR_500"
        val message = "Internal server error"
        val existingSha = "existing-error-sha-999"
        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedFormattedContent = "[$error]:$message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(expectedFormattedContent.toByteArray())

        // 1. Stub fetchFileSha on the service spy to return an existing SHA
        doReturn(existingSha).`when`(service).fetchFileSha(eq(expectedUrl), anyOrNull())

        // 2. Stub RestClient PUT chain
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        // Act
        service.writeError(username = username, error = error, message = message)

        // Assert
        // Verify logger recorded success
        verify(logger).info("Error successfully logged to GitHub errors.md")

        // Capture and inspect the request body
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue

        // Assert "sha" key is included alongside message and content
        assertEquals(existingSha, requestBody["sha"])
        assertEquals("Update $expectedPath via Amie Repository for$username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
    }

    @Test
    fun `writeError - path and content - encodes error message into base64 and formats github url correctly`() {
        // Arrange
        val username = "alice"
        val error = "CUSTOM_ERR"
        val message = "Failed to parse metadata"

        // Construct the expected target URL and formatted message
        val expectedPath = "uploads/$username/errors.md"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val rawFormattedText = "[$error]:$message"
        val expectedBase64Content = Base64.getEncoder().encodeToString(rawFormattedText.toByteArray())

        // 1. Stub fetchFileSha to return null
        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), anyOrNull())

        // 2. Stub RestClient PUT fluent chain
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        // Act
        service.writeError(username = username, error = error, message = message)

        // Assert
        // Verify URI was called with the exact target URL
        verify(requestBodyUriSpec).uri(expectedUrl)

        // Verify Authorization header was configured
        verify(requestBodySpec).header(eq("Authorization"), startsWith("Bearer "))

        // Capture and decode the body content to verify Base64 encoding
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue
        val encodedContent = requestBody["content"]

        assertEquals(expectedBase64Content, encodedContent)
        assertEquals(rawFormattedText, String(Base64.getDecoder().decode(encodedContent)))
    }

    @Test
    fun `writeError - upload failure - catches exception and logs error when restClient put fails`() {
        // Arrange
        val username = "john"
        val error = "ERR_500"
        val message = "Internal Server Error"
        val exceptionMessage = "500 Internal Server Error"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/uploads/$username/errors.md"

        // 1. Stub fetchFileSha to return null
        doReturn(null).`when`(service).fetchFileSha(eq(expectedUrl), anyOrNull())

        // 2. Stub RestClient PUT chain to throw an exception on retrieve/execute
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenThrow(RuntimeException(exceptionMessage))

        // Act
        service.writeError(username = username, error = error, message = message)

        // Assert
        // Verify exception was caught and logged gracefully
        verify(logger).error(
            eq("Failed to log error to GitHub: {}"),
            eq(exceptionMessage)
        )

        // Verify success log was NEVER reached
        verify(logger, never()).info("Error successfully logged to GitHub errors.md")
    }

    @Test
    fun `sendEdit - happy path - fetches sha and updates file successfully on 200 or 201 response`() {
        // Arrange
        val username = "john"
        val fileName = "script.kt"
        val existingSha = "existing-sha-12345"
        val updateFile = MockMultipartFile("file", fileName, "text/plain", "println(\"Updated\")".toByteArray())

        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64Content = Base64.getEncoder().encodeToString(updateFile.bytes)

        // 1. Stub RestClient GET response (fetches existing file metadata and SHA)
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(GithubContentResponseDto(sha = existingSha))

        // 2. Stub RestClient PUT response (performs the edit upload)
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenReturn(ResponseEntity.status(HttpStatus.OK).body("updated"))

        // Act
        service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)

        // Assert
        // Verify GET request was called with the correct URL
        verify(requestHeadersUriSpec).uri(expectedUrl)

        // Verify logger captured success message
        verify(logger).info(
            eq("GitHub Update Success for {}! Status: {}"),
            eq(username),
            eq(HttpStatus.OK.value())
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        // Capture and verify PUT payload
        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue
        assertEquals("Update $fileName via Amie Repository for$username", requestBody["message"])
        assertEquals(expectedBase64Content, requestBody["content"])
        assertEquals(existingSha, requestBody["sha"])
    }

    @Test
    fun `sendEdit - missing file - throws exception when get request returns null or 404`() {
        // Arrange
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

        // Act & Assert (Case 1: Body returns null)
        val exceptionNull = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        assertEquals("File not found for update: $expectedPath", exceptionNull.message)

        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username),
            eq("File not found for update: $expectedPath")
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        // 2. Stub RestClient GET chain throwing 404 Exception
        clearInvocations(logger)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // Act & Assert (Case 2: Retrieve throws 404)
        val exception404 = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        assertEquals("404 Not Found", exception404.message)

        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username),
            eq("404 Not Found")
        )
        verify(logger).info("--- [SimpleService: sendEdit] END ---")

        // Verify PUT request was NEVER called in either failure case
        verify(restClient, never()).put()
    }

    @Test
    fun `sendEdit - path encoding - correctly constructs and encodes url path with and without username`() {
        // Arrange
        val mockFile = MockMultipartFile("file", "test.kt", "text/plain", "println()".toByteArray())
        val sha = "sha-abc-123"

        // Set up default stubs for GET and PUT chains
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(GithubContentResponseDto(sha = sha))

        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok("ok"))

        // --- Case 1: With Username containing special characters and spaces ---
        val usernameWithSpaces = "john doe"
        val fileWithSpaces = "my script.kt"
        // "uploads/john doe/my script.kt" -> encoded path segments replace spaces with %20
        val expectedEncodedUrlWithUser = "$githubApiBase/$owner/$name/$urlSegment/uploads/john%20doe/my%20script.kt"

        // Act 1
        service.sendEdit(username = usernameWithSpaces, fileName = fileWithSpaces, updateFile = mockFile)

        // Assert 1
        verify(requestHeadersUriSpec).uri(expectedEncodedUrlWithUser)
        verify(requestBodyUriSpec).uri(expectedEncodedUrlWithUser)

        // Reset interactions for Case 2
        clearInvocations(requestHeadersUriSpec, requestBodyUriSpec)

        // --- Case 2: Without Username (Blank / Empty) ---
        val blankUsername = "   "
        val fileName = "global_config.json"
        // "uploads/global_config.json" -> username segment is omitted
        val expectedEncodedUrlNoUser = "$githubApiBase/$owner/$name/$urlSegment/uploads/global_config.json"

        // Act 2
        service.sendEdit(username = blankUsername, fileName = fileName, updateFile = mockFile)

        // Assert 2
        verify(requestHeadersUriSpec).uri(expectedEncodedUrlNoUser)
        verify(requestBodyUriSpec).uri(expectedEncodedUrlNoUser)
    }

    @Test
    fun `sendEdit - put request failure - catches, logs critical error, and rethrows exception when put fails`() {
        // Arrange
        val username = "john"
        val fileName = "script.kt"
        val existingSha = "existing-sha-12345"
        val updateFile = MockMultipartFile("file", fileName, "text/plain", "println()".toByteArray())
        val putErrorMessage = "500 Internal Server Error"

        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"

        // 1. Stub RestClient GET response (successfully fetches file SHA)
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(GithubContentResponseDto(sha = existingSha))

        // 2. Stub RestClient PUT response (throws RuntimeException during execution)
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException(putErrorMessage))

        // Act & Assert
        val exception = assertThrows<RuntimeException> {
            service.sendEdit(username = username, fileName = fileName, updateFile = updateFile)
        }

        // Verify exception was rethrown with the original message
        assertEquals(putErrorMessage, exception.message)

        // Verify logger captured the critical error message
        verify(logger).error(
            eq("CRITICAL: GitHub API Update Error for user '{}': {}"),
            eq(username),
            eq(putErrorMessage)
        )

        // Verify lifecycle log in the finally block executed
        verify(logger).info("--- [SimpleService: sendEdit] END ---")
    }

    @Test
    fun `uploadFileData - new file - uploads raw bytes without sha when initial get check returns null or throws`() {
        // Arrange
        val username = "john"
        val fileName = "data.bin"
        val data = "raw payload content".toByteArray()
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64 = Base64.getEncoder().encodeToString(data)

        // 1. Stub GET check to throw an exception (simulating new file / 404)
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenThrow(RuntimeException("404 Not Found"))

        // 2. Stub PUT request to succeed
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        // Act
        service.uploadFileData(username = username, fileName = fileName, data = data)

        // Assert
        verify(logger).info("No existing file found for raw upload (this is normal for new files)")
        verify(logger).info("GitHub raw data sync successful for '{}'", fileName)
        verify(logger).info("--- [SimpleService: uploadFileData] END ---")

        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue
        assertEquals("Update $fileName via Amie Device Manager", requestBody["message"])
        assertEquals(expectedBase64, requestBody["content"])
        assertNull(requestBody["sha"])
    }

    @Test
    fun `uploadFileData - existing file - attaches sha to put body when existing file is found`() {
        // Arrange
        val username = "john"
        val fileName = "config.json"
        val data = "{\"key\":\"value\"}".toByteArray()
        val existingSha = "raw-data-sha-555"
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"
        val expectedBase64 = Base64.getEncoder().encodeToString(data)

        // 1. Stub GET check returning an existing SHA
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java))
            .thenReturn(GithubContentResponseDto(sha = existingSha))

        // 2. Stub PUT request to succeed
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build())

        // Act
        service.uploadFileData(username = username, fileName = fileName, data = data)

        // Assert
        verify(logger).info("Retrieved SHA: {}", existingSha)
        verify(logger).info("GitHub raw data sync successful for '{}'", fileName)

        val mapCaptor = argumentCaptor<Map<String, String>>()
        verify(requestBodySpec).body(mapCaptor.capture())

        val requestBody = mapCaptor.firstValue
        assertEquals("Update $fileName via Amie Device Manager", requestBody["message"])
        assertEquals(expectedBase64, requestBody["content"])
        assertEquals(existingSha, requestBody["sha"])
    }

    @Test
    fun `uploadFileData - upload error - catches exception and logs debug error when put request fails`() {
        // Arrange
        val username = "john"
        val fileName = "data.bin"
        val data = "raw payload content".toByteArray()
        val errorMessage = "500 Internal Server Error"
        val expectedPath = "uploads/$username/$fileName"
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/$expectedPath"

        // 1. Stub GET check returning null
        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(GithubContentResponseDto::class.java)).thenReturn(null)

        // 2. Stub PUT request throwing an exception
        `when`(restClient.put()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(expectedUrl)).thenReturn(requestBodySpec)
        `when`(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(any())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toBodilessEntity()).thenThrow(RuntimeException(errorMessage))

        // Act
        service.uploadFileData(username = username, fileName = fileName, data = data)

        // Assert
        verify(logger).error(
            eq("DEBUG: GitHub data sync failed for '{}': {}"),
            eq(fileName),
            eq(errorMessage)
        )
        verify(logger).info("--- [SimpleService: uploadFileData] END ---")
    }

}
