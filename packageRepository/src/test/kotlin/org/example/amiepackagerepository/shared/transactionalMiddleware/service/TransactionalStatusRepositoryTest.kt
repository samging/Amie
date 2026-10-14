package org.example.amiepackagerepository.shared.transactionalMiddleware.service

import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObject
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.http.HttpStatus

/** Devices go to Postgres and to the saved state `devices` in the package store. */
@ExtendWith(MockitoExtension::class)
class TransactionalStatusRepositoryTest {
    @Mock private lateinit var deviceStatusRepository: DeviceStatusRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var store: PackageStoreClient

    private lateinit var service: TransactionalStatusRepository
    private val sam = RestUserEntity(id = 7, username = "sam", password = "")

    @BeforeEach
    fun setUp() {
        service = TransactionalStatusRepository(deviceStatusRepository, userRepository, store)
    }

    @Test
    fun `SET replaces the rows and writes state devices json`() {
        `when`(userRepository.findByUsername("sam")).thenReturn(sam)
        val old = listOf(DeviceStatus(id = 1, deviceKey = "9", name = "old", port = "p", user = sam))
        `when`(deviceStatusRepository.findByUser(sam)).thenReturn(old)
        val devices = mapOf("1" to DeviceDto("uno", "/dev/tty1", "http://x"), "2" to DeviceDto("nano", "/dev/tty2"))

        val response = service.repositoryDeviceController(DeviceActions.SET, "sam", devices).get()

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(deviceStatusRepository).deleteAll(old)
        val saved = argumentCaptor<List<DeviceStatus>>()
        verify(deviceStatusRepository).saveAll(saved.capture())
        assertEquals(listOf("1", "2"), saved.firstValue.map { it.deviceKey })
        assertEquals("uno", saved.firstValue[0].name)
        val bytes = argumentCaptor<ByteArray>()
        verify(store).put(eq("uploads/sam/state/devices.json"), bytes.capture(), eq("application/json"))
        val json = String(bytes.firstValue)
        assertTrue(json.contains("\"1\":{\"name\":\"uno\",\"port\":\"/dev/tty1\",\"deviceEndpoint\":\"http://x\"}"), json)
        assertTrue(json.contains("\"2\":{\"name\":\"nano\",\"port\":\"/dev/tty2\"}"), json)
    }

    @Test
    fun `SET keeps the local rows and answers 502 when the store is down`() {
        `when`(userRepository.findByUsername("sam")).thenReturn(sam)
        `when`(deviceStatusRepository.findByUser(sam)).thenReturn(emptyList())
        `when`(store.put(eq("uploads/sam/state/devices.json"), any(), anyOrNull()))
            .thenThrow(PackageStoreException("unreachable", null))

        val response = service.repositoryDeviceController(DeviceActions.SET, "sam", mapOf("1" to DeviceDto("uno", "p"))).get()

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `GET answers the stored JSON, or an empty object when the user never saved devices`() {
        `when`(userRepository.findByUsername("sam")).thenReturn(sam)
        `when`(store.get("uploads/sam/state/devices.json"))
            .thenReturn(StoredObject("uploads/sam/state/devices.json", """{"1":{"name":"uno","port":"p"}}""".toByteArray(), "application/json", "\"e\""))
            .thenReturn(null)

        val first = service.repositoryDeviceController(DeviceActions.GET, "sam", emptyMap()).get()
        val second = service.repositoryDeviceController(DeviceActions.GET, "sam", emptyMap()).get()

        assertEquals("""{"1":{"name":"uno","port":"p"}}""", first.body)
        assertEquals("{}", second.body)
        verify(deviceStatusRepository, never()).deleteAll(any<List<DeviceStatus>>())
    }

    @Test
    fun `an unknown user is 400 before anything is touched`() {
        `when`(userRepository.findByUsername("nobody")).thenReturn(null)

        val response = service.repositoryDeviceController(DeviceActions.SET, "nobody", emptyMap()).get()
        val blank = service.repositoryDeviceController(DeviceActions.GET, "", emptyMap()).get()

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, blank.statusCode)
        verify(store, never()).put(any(), any(), anyOrNull())
        verify(store, never()).get(any())
    }

    @Test
    fun `saveDeviceStatuses and getDeviceStatuses only use the database`() {
        `when`(userRepository.findByUsername("sam")).thenReturn(sam)
        `when`(deviceStatusRepository.findByUser(sam)).thenReturn(emptyList())
        `when`(deviceStatusRepository.findByUserUsername("sam")).thenReturn(listOf(DeviceStatus(deviceKey = "1", name = "uno", port = "p", user = sam)))

        service.saveDeviceStatuses("sam", mapOf("1" to DeviceDto("uno", "p")))
        val rows = service.getDeviceStatuses("sam")

        assertEquals("uno", rows.single().name)
        verify(deviceStatusRepository).saveAll(any<List<DeviceStatus>>())
        verify(store, never()).put(any(), any(), anyOrNull())
    }

}
