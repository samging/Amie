package org.example.amiepackagerepository.shared.transactionalMiddleware.service

@ExtendWith(MockitoExtension::class)
class TransactionalStatusRepositoryTest {
    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var deviceStatusRepository: DeviceStatusRepository

    @Mock
    private lateinit var simpleService: SimpleService

    @Mock
    private lateinit var restClient: RestClient

    @Mock
    private lateinit var requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*>

    @Mock
    private lateinit var requestHeadersSpec: RestClient.RequestHeadersSpec<*>

    @Mock
    private lateinit var responseSpec: RestClient.ResponseSpec

    @InjectMocks
    private lateinit var transactionalStatusRepository: TransactionalStatusRepositoryImpl

    private val githubApiBase = "https://api.github.com"
    private val owner = "test-owner"
    private val name = "test-repo"
    private val urlSegment = "contents"

    @Test
    fun `repositoryDeviceController - user not found - returns 400 Bad Request`() {
        // Arrange
        val username = "unknown_user"
        whenever(userRepository.findByUsername(username)).thenReturn(null)

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("User not found or is Unidentifiable: '$username'", response.body)
        verify(deviceStatusRepository, never()).deleteAll(any())
    }

    @Test
    fun `repositoryDeviceController - SET action happy path - deletes existing, saves new entities, and syncs to github`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)
        val deviceMap = mapOf(
            "dev1" to DeviceDto(name = "Device 1", port = 8080, deviceEndpoint = "/api/v1")
        )
        val existingStatuses = listOf(DeviceStatus(deviceKey = "old", user = user))

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        whenever(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }
        doNothing().whenever(simpleService).uploadFileData(eq(username), eq("$username-device.json"), any())

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = deviceMap
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Device statuses saved and synced for $username", response.body)

        verify(deviceStatusRepository).deleteAll(existingStatuses)

        val captor = argumentCaptor<List<DeviceStatus>>()
        verify(deviceStatusRepository).saveAll(captor.capture())
        val savedList = captor.firstValue
        assertEquals(1, savedList.size)
        assertEquals("dev1", savedList[0].deviceKey)
        assertEquals("Device 1", savedList[0].name)

        verify(simpleService).uploadFileData(eq(username), eq("$username-device.json"), any())
    }

    @Test
    fun `repositoryDeviceController - SET action github sync failure - returns 500 Internal Server Error but saves entities`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)
        val deviceMap = mapOf(
            "dev1" to DeviceDto(name = "Device 1", port = 8080, deviceEndpoint = "/api/v1")
        )

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())
        whenever(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }
        whenever(simpleService.uploadFileData(eq(username), eq("$username-device.json"), any()))
            .thenThrow(RuntimeException("Network timeout"))

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = deviceMap
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Local save OK, but GitHub sync failed: Network timeout", response.body)

        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `repositoryDeviceController - GET action happy path - returns github file content`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/uploads/$username/$username-device.json"
        val expectedJson = """{"dev1":{"name":"Device 1"}}"""

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        whenever(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        whenever(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok(expectedJson))

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedJson, response.body)

        verify(deviceStatusRepository).deleteAll(emptyList())
    }

    @Test
    fun `repositoryDeviceController - GET action http error - returns mapped status code and error body`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        whenever(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        whenever(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.toEntity(String::class.java))
            .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Repository Error: 404 Not Found", response.body)
    }

    @Test
    fun `repositoryDeviceController - GET action generic exception - returns 500 Internal Server Error`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        whenever(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        whenever(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException("Unknown connection failure"))

        // Act
        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Error: Unknown connection failure", response.body)
    }

    @Test
    fun `saveDeviceStatuses - happy path - clears existing statuses and persists mapped device entities`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)

        val deviceMap = mapOf(
            "dev-01" to DeviceDto(name = "Sensor A", port = 8080, deviceEndpoint = "/api/v1/sensor"),
            "dev-02" to DeviceDto(name = "Actuator B", port = 9090, deviceEndpoint = "/api/v1/actuator")
        )

        val existingStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "old-dev", name = "Old Device", port = 7070, deviceEndpoint = "/old", user = user)
        )

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        whenever(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }

        // Act
        service.saveDeviceStatuses(username, deviceMap)

        // Assert
        // 1. Verify existing records were cleared
        verify(deviceStatusRepository).deleteAll(existingStatuses)

        // 2. Capture and verify saved DeviceStatus entities
        val captor = argumentCaptor<List<DeviceStatus>>()
        verify(deviceStatusRepository).saveAll(captor.capture())

        val savedEntities = captor.firstValue
        assertEquals(2, savedEntities.size)

        val dev1 = savedEntities.first { it.deviceKey == "dev-01" }
        assertEquals("Sensor A", dev1.name)
        assertEquals(8080, dev1.port)
        assertEquals("/api/v1/sensor", dev1.deviceEndpoint)
        assertEquals(user, dev1.user)

        val dev2 = savedEntities.first { it.deviceKey == "dev-02" }
        assertEquals("Actuator B", dev2.name)
        assertEquals(9090, dev2.port)
        assertEquals("/api/v1/actuator", dev2.deviceEndpoint)
        assertEquals(user, dev2.user)
    }

    @Test
    fun `saveDeviceStatuses - empty device map - clears existing statuses and saves empty list`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)
        val existingStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "old-dev", name = "Old Device", user = user)
        )

        whenever(userRepository.findByUsername(username)).thenReturn(user)
        whenever(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        whenever(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenReturn(emptyList())

        // Act
        service.saveDeviceStatuses(username, emptyMap())

        // Assert
        verify(deviceStatusRepository).deleteAll(existingStatuses)

        val captor = argumentCaptor<List<DeviceStatus>>()
        verify(deviceStatusRepository).saveAll(captor.capture())
        assertTrue(captor.firstValue.isEmpty())
    }

    @Test
    fun `saveDeviceStatuses - user not found - early returns without modifying or saving device statuses`() {
        // Arrange
        val username = "non_existent_user"
        whenever(userRepository.findByUsername(username)).thenReturn(null)

        // Act
        service.saveDeviceStatuses(username, mapOf("dev-01" to DeviceDto(name = "Sensor")))

        // Assert
        verify(deviceStatusRepository, never()).findByUser(any())
        verify(deviceStatusRepository, never()).deleteAll(any())
        verify(deviceStatusRepository, never()).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `getDeviceStatuses - happy path - delegates to repository and returns matching entities`() {
        // Arrange
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)
        val expectedStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "dev-01", name = "Sensor A", user = user),
            DeviceStatus(id = 2L, deviceKey = "dev-02", name = "Actuator B", user = user)
        )

        whenever(deviceStatusRepository.findByUserUsername(username)).thenReturn(expectedStatuses)

        // Act
        val result = service.getDeviceStatuses(username)

        // Assert
        assertEquals(2, result.size)
        assertEquals("dev-01", result[0].deviceKey)
        assertEquals("dev-02", result[1].deviceKey)
        verify(deviceStatusRepository).findByUserUsername(username)
    }

    @Test
    fun `getDeviceStatuses - no records found - returns empty list`() {
        // Arrange
        val username = "alice"
        whenever(deviceStatusRepository.findByUserUsername(username)).thenReturn(emptyList())

        // Act
        val result = service.getDeviceStatuses(username)

        // Assert
        assertTrue(result.isEmpty())
        verify(deviceStatusRepository).findByUserUsername(username)
    }
}