package org.example.amiepackagerepository.application

import org.example.amiepackagerepository.shared.errors.ApiExceptionHandler
import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import java.util.concurrent.CompletableFuture

/** The device routes, and in particular how a failed repository action turns into an HTTP status. */
@ExtendWith(MockitoExtension::class)
class DeviceControllerTest {
    @Mock private lateinit var store: PackageStoreClient
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var deviceService: TransactionalStatusRepository

    private lateinit var mockMvc: MockMvc

    private val devices = """{"uno":{"name":"Uno","port":"/dev/tty.usb1","deviceEndpoint":"http://10.0.0.5"}}"""

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(DeviceController(store, userService, deviceService))
            .setControllerAdvice(ApiExceptionHandler())
            .addPlaceholderValue("amie.cors.allowed-origins", "http://localhost:8081")
            .build()
    }

    private fun MvcResult.dispatched() = mockMvc.perform(asyncDispatch(this))

    private fun action(action: String, username: String = "sam") =
        post("/device-repository-controller").param("action", action).param("username", username)
            .contentType(MediaType.APPLICATION_JSON).content(devices)

    private fun failingWith(e: Throwable) {
        `when`(deviceService.repositoryDeviceController(eq(DeviceActions.SET), eq("sam"), ArgumentMatchers.anyMap()))
            .thenReturn(CompletableFuture.failedFuture(e))
    }

    @Test
    fun `device-status saves through the service and reads the rows back`() {
        `when`(deviceService.getDeviceStatuses("sam")).thenReturn(listOf(DeviceStatus(1, "uno", "Uno", "/dev/tty.usb1", "http://10.0.0.5")))

        mockMvc.perform(post("/device-status").param("username", "sam").contentType(MediaType.APPLICATION_JSON).content(devices))
            .andExpect(status().isOk)
            .andExpect(content().string("Device statuses saved successfully"))
        verify(deviceService).saveDeviceStatuses("sam", mapOf("uno" to DeviceDto("Uno", "/dev/tty.usb1", "http://10.0.0.5")))

        mockMvc.perform(get("/device-status").param("username", "sam"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].deviceKey").value("uno"))
            .andExpect(jsonPath("$[0].port").value("/dev/tty.usb1"))
            .andExpect(jsonPath("$[0].user").doesNotExist())
    }

    @Test
    fun `device-status without a body or a username is 400 with the reason`() {
        mockMvc.perform(post("/device-status").contentType(MediaType.APPLICATION_JSON).content(devices))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Missing parameter: username"))

        mockMvc.perform(post("/device-status").param("username", "sam").contentType(MediaType.APPLICATION_JSON).content("[1, 2"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("bad_request"))
    }

    @Test
    fun `endpoints are read from the store and a failed publish is swallowed`() {
        `when`(store.fetchEndpoints()).thenReturn(mapOf("uno" to "Arduino Uno"))
        mockMvc.perform(get("/fetch-endpoints"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uno").value("Arduino Uno"))

        `when`(store.postEndpoints()).thenThrow(PackageStoreException("The package store at http://localhost:8088 is unreachable"))
        mockMvc.perform(post("/post-endpoints")).andExpect(status().isOk)
    }

    @Test
    fun `a successful repository action passes its answer through`() {
        `when`(deviceService.repositoryDeviceController(eq(DeviceActions.GET), eq("sam"), ArgumentMatchers.anyMap()))
            .thenReturn(CompletableFuture.completedFuture(ResponseEntity.ok(devices)))

        mockMvc.perform(action("GET")).andReturn().dispatched()
            .andExpect(status().isOk)
            .andExpect(content().json(devices))
    }

    @Test
    fun `an unknown action is 400 before the service is called`() {
        mockMvc.perform(action("UPSERT"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Parameter 'action' has the wrong value: UPSERT"))
    }

    @Test
    fun `each kind of failure in the repository action has its own status`() {
        val cases = listOf(
            IllegalArgumentException("Unknown device") to HttpStatus.BAD_REQUEST,
            NoSuchElementException("No such user") to HttpStatus.NOT_FOUND,
            SecurityException("Not yours") to HttpStatus.FORBIDDEN,
            IllegalStateException("Already syncing") to HttpStatus.CONFLICT,
            UnsupportedOperationException("Not yet") to HttpStatus.NOT_IMPLEMENTED,
            RuntimeException("boom") to HttpStatus.INTERNAL_SERVER_ERROR,
        )
        for ((cause, expected) in cases) {
            failingWith(cause)
            mockMvc.perform(action("SET")).andReturn().dispatched()
                .andExpect(status().`is`(expected.value()))
                .andExpect(content().string("Action Failed: ${cause.message}"))
        }
    }

    @Test
    fun `a provider's HTTP error keeps its status and body`() {
        failingWith(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", org.springframework.http.HttpHeaders(), "rate limited".toByteArray(), null))

        mockMvc.perform(action("SET")).andReturn().dispatched()
            .andExpect(status().isTooManyRequests)
            .andExpect(content().string("Action Failed: Repository Provider Error: rate limited"))
    }

    @Test
    fun `a failure without a message still explains itself`() {
        failingWith(RuntimeException())

        mockMvc.perform(action("SET")).andReturn().dispatched()
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("Action Failed: An unexpected error occurred"))
    }
}
