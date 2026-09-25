package org.example.amiepackagerepository.shared.transactionalMiddleware.service

import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.testcontainers.shaded.com.google.common.base.Verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val username = "unknown_user"
        `when`(userRepository.findByUsername(username)).thenReturn(null)

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("User not found or is Unidentifiable: '$username'", response.body)
        verify(deviceStatusRepository, never()).deleteAll(any())
    }

    @Test
    fun `repositoryDeviceController - SET action happy path - deletes existing, saves new entities, and syncs to github`() {
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)
        val deviceMap = mapOf(
            "dev1" to DeviceDto(name = "Device 1", port = 8080, deviceEndpoint = "/api/v1")
        )
        val existingStatuses = listOf(DeviceStatus(deviceKey = "old", user = user))

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        `when`(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }
        doNothing().`when`(simpleService).uploadFileData(eq(username), eq("$username-device.json"), any())

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = deviceMap
        )
        val response = future.get()

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

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())
        `when`(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }
        `when`(simpleService.uploadFileData(eq(username), eq("$username-device.json"), any()))
            .thenThrow(RuntimeException("Network timeout"))

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.SET,
            username = username,
            deviceMap = deviceMap
        )
        val response = future.get()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Local save OK, but GitHub sync failed: Network timeout", response.body)

        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `repositoryDeviceController - GET action happy path - returns github file content`() {
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)
        val expectedUrl = "$githubApiBase/$owner/$name/$urlSegment/uploads/$username/$username-device.json"
        val expectedJson = """{"dev1":{"name":"Device 1"}}"""

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(expectedUrl)).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java)).thenReturn(ResponseEntity.ok(expectedJson))

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedJson, response.body)

        verify(deviceStatusRepository).deleteAll(emptyList())
    }

    @Test
    fun `repositoryDeviceController - GET action http error - returns mapped status code and error body`() {
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Repository Error: 404 Not Found", response.body)
    }

    @Test
    fun `repositoryDeviceController - GET action generic exception - returns 500 Internal Server Error`() {
        val username = "john"
        val user = RestUserEntity(id = 1L, username = username)

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(emptyList())

        `when`(restClient.get()).thenReturn(requestHeadersUriSpec as RestClient.RequestHeadersUriSpec<Nothing>)
        `when`(requestHeadersUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec as RestClient.RequestHeadersSpec<Nothing>)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.toEntity(String::class.java))
            .thenThrow(RuntimeException("Unknown connection failure"))

        val future = transactionalStatusRepository.repositoryDeviceController(
            action = DeviceActions.GET,
            username = username,
            deviceMap = emptyMap()
        )
        val response = future.get()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Error: Unknown connection failure", response.body)
    }

    @Test
    fun `saveDeviceStatuses - happy path - clears existing statuses and persists mapped device entities`() {
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)

        val deviceMap = mapOf(
            "dev-01" to DeviceDto(name = "Sensor A", port = 8080, deviceEndpoint = "/api/v1/sensor"),
            "dev-02" to DeviceDto(name = "Actuator B", port = 9090, deviceEndpoint = "/api/v1/actuator")
        )

        val existingStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "old-dev", name = "Old Device", port = 7070, deviceEndpoint = "/old", user = user)
        )

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        `when`(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenAnswer { it.arguments[0] }

        service.saveDeviceStatuses(username, deviceMap)

        verify(deviceStatusRepository).deleteAll(existingStatuses)

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
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)
        val existingStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "old-dev", name = "Old Device", user = user)
        )

        `when`(userRepository.findByUsername(username)).thenReturn(user)
        `when`(deviceStatusRepository.findByUser(user)).thenReturn(existingStatuses)
        `when`(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).thenReturn(emptyList())

        service.saveDeviceStatuses(username, emptyMap())

        verify(deviceStatusRepository).deleteAll(existingStatuses)

        val captor = argumentCaptor<List<DeviceStatus>>()
        verify(deviceStatusRepository).saveAll(captor.capture())
        assertTrue(captor.firstValue.isEmpty())
    }

    @Test
    fun `saveDeviceStatuses - user not found - early returns without modifying or saving device statuses`() {
        val username = "non_existent_user"
        `when`(userRepository.findByUsername(username)).thenReturn(null)

        service.saveDeviceStatuses(username, mapOf("dev-01" to DeviceDto(name = "Sensor")))

        verify(deviceStatusRepository, never()).findByUser(any())
        verify(deviceStatusRepository, never()).deleteAll(any())
        verify(deviceStatusRepository, never()).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `getDeviceStatuses - happy path - delegates to repository and returns matching entities`() {
        val username = "john"
        val user = RestUserEntity(id = 10L, username = username)
        val expectedStatuses = listOf(
            DeviceStatus(id = 1L, deviceKey = "dev-01", name = "Sensor A", user = user),
            DeviceStatus(id = 2L, deviceKey = "dev-02", name = "Actuator B", user = user)
        )

        `when`(deviceStatusRepository.findByUserUsername(username)).thenReturn(expectedStatuses)

        val result = service.getDeviceStatuses(username)

        assertEquals(2, result.size)
        assertEquals("dev-01", result[0].deviceKey)
        assertEquals("dev-02", result[1].deviceKey)
        verify(deviceStatusRepository).findByUserUsername(username)
    }

    @Test
    fun `getDeviceStatuses - no records found - returns empty list`() {
        val username = "alice"
        `when`(deviceStatusRepository.findByUserUsername(username)).thenReturn(emptyList())

        val result = service.getDeviceStatuses(username)

        assertTrue(result.isEmpty())
        verify(deviceStatusRepository).findByUserUsername(username)
    }
}