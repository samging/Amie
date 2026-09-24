package org.example.amiepackagerepository.shared.transactionalMiddleware

import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.util.concurrent.CompletableFuture

@ExtendWith(MockitoExtension::class)
class TransactionalStatusRepositoryTest {
    @Mock
    private lateinit var deviceStatusRepository: DeviceStatusRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var simpleService: GithubService

    @InjectMocks
    private lateinit var service: TransactionalStatusRepository

    private val testUser = mock<User>(id = 1L, username = "john_doe")

    private val testDeviceMap = mapOf(
        "dev-1" to DeviceDto(name = "Router", port = 8080, deviceEndpoint = "/api/v1")
    )

    @BeforeEach
    fun setUp() {
        // Inject Spring @Value annotated fields via Reflection
        ReflectionTestUtils.setField(service, "owner", "my-owner")
        ReflectionTestUtils.setField(service, "name", "my-repo")
        ReflectionTestUtils.setField(service, "githubApiBase", "https://api.github.com")
        ReflectionTestUtils.setField(service, "urlSegment", "repos")
    }

    @Test
    fun`should return BAD_REQUEST when user is not found`(){
        `when`(userRepository.findByUsername("unknown_user")).thenReturn(null)

        val future = service.repositoryDeviceController(
            DeviceActions.SET,
            "unknown_user",
            testDeviceMap
        )

        val response = future.get()

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("User not found or is Unidentifiable: 'unknown_user'", response.body)

    }

    @Test
    fun `SET action - should clear existing statuses, save new entities, upload to github, and return OK`(){
        val existingStatuses = listOf(DeviceStatus(deviceKey = "old-dev", user = testUser))
        `when`(deviceStatusRepository.findByUser(testUser)).thenReturn(existingStatuses))
        `when`(userRepository.findByUsername("john_doe")).thenReturn(testUser))
        `when`(deviceStatusRepository.saveAll(any<List<DeviceStatus>>())).doAnswer { invocation ->
            invocation.getArgument(0)
        }

        val future = service.repositoryDeviceController(
            DeviceActions.SET,
            testUser.username,
            testDeviceMap
        )

        val response = future.get()
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Device statuses saved and synced for ${testUser.username}", response.body)

        verify(deviceStatusRepository).deleteAll(existingStatuses)
        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
        verify(simpleService).uploadFileData(
            eq(testUser.username),
            eq("${testUser.username}-device.json"),
            any()
        )
    }

    @Test
    fun `SET action - should return INTERNAL_SERVER_ERROR when github upload fails`() {
        `when`(userRepository.findByUsername(testUser.username)).thenReturn(testUser)
        `when`(deviceStatusRepository.findByUser(testUser)).thenReturn(emptyList())

        doThrow(RuntimeException("Network error"))
            .whenever(simpleService).uploadFileData(any(), any(), any())

        val future = service.repositoryDeviceController(
            DeviceActions.SET,
            testUser.username,
            testDeviceMap
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Local save OK, but GitHub sync failed: Network error", response.body)

        verify(deviceStatusRepository).deleteAll(emptyList())
        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `should delete old statuses and save new ones when user exists`() {
        // Given
        val existingStatuses = listOf(DeviceStatus(deviceKey = "old-key", user = testUser))
        whenever(userRepository.findByUsername(testUser.username)).thenReturn(testUser)
        whenever(deviceStatusRepository.findByUser(testUser)).thenReturn(existingStatuses)

        // When
        service.saveDeviceStatuses(testUser.username, testDeviceMap)

        // Then
        verify(deviceStatusRepository).deleteAll(existingStatuses)
        verify(deviceStatusRepository).saveAll(
            argThat<List<DeviceStatus>> { savedList ->
                savedList.size == 1 &&
                        savedList[0].deviceKey == "dev-1" &&
                        savedList[0].name == "Router" &&
                        savedList[0].port == 8080 &&
                        savedList[0].deviceEndpoint == "/api/v1" &&
                        savedList[0].user == testUser
            }
        )
    }

    @Test
    fun `should return early and perform no DB modifications when user is not found`() {
        // Given
        whenever(userRepository.findByUsername("unknown_user")).thenReturn(null)

        // When
        service.saveDeviceStatuses("unknown_user", testDeviceMap)

        // Then
        verify(deviceStatusRepository, never()).findByUser(any())
        verify(deviceStatusRepository, never()).deleteAll(any())
        verify(deviceStatusRepository, never()).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `should handle empty device map without throwing and save empty list`() {
        // Given
        whenever(userRepository.findByUsername(testUser.username)).thenReturn(testUser)
        whenever(deviceStatusRepository.findByUser(testUser)).thenReturn(emptyList())

        // When
        service.saveDeviceStatuses(testUser.username, emptyMap())

        // Then
        verify(deviceStatusRepository).deleteAll(emptyList())
        verify(deviceStatusRepository).saveAll(emptyList())
    }

    @Test
    fun `getDeviceStatuses - should return list of device statuses when records exist`() {
        // Given
        val expectedStatuses = listOf(
            DeviceStatus(deviceKey = "dev-1", name = "Router", port = 8080, deviceEndpoint = "/api/v1", user = testUser),
            DeviceStatus(deviceKey = "dev-2", name = "Switch", port = 9090, deviceEndpoint = "/api/v2", user = testUser)
        )
        whenever(deviceStatusRepository.findByUserUsername("john_doe")).thenReturn(expectedStatuses)

        // When
        val result = service.getDeviceStatuses("john_doe")

        // Then
        assertEquals(2, result.size)
        assertEquals("dev-1", result[0].deviceKey)
        assertEquals("dev-2", result[1].deviceKey)
        verify(deviceStatusRepository).findByUserUsername("john_doe")
    }

    @Test
    fun `getDeviceStatuses - should return empty list when no records exist`() {
        `when`(deviceStatusRepository.findByUserUsername("unknown_user")).thenReturn(emptyList())

        val result = service.getDeviceStatuses("unknown_user")

        assertTrue(result.isEmpty())
        verify(deviceStatusRepository).findByUserUsername("unknown_user")
    }
}