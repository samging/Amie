package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user

import io.jsonwebtoken.Jwts
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.ProfileUpdateDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.UserActivity
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserActivityRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.keycloak.KeycloakTokenVerifier
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * [UserService] over mocked repositories: the password hashing, the tokens it issues and reads back, the guest
 * and Keycloak paths, and the order a delete must run in.
 */
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock private lateinit var users: RestUserRepository
    @Mock private lateinit var activities: UserActivityRepository
    @Mock private lateinit var verifier: KeycloakTokenVerifier
    @Mock private lateinit var devices: DeviceStatusRepository

    private lateinit var service: UserService
    private val encoder = BCryptPasswordEncoder()

    @BeforeEach
    fun setUp() {
        service = UserService(users, activities, verifier, devices)
    }

    private fun userRow(username: String = "sam", password: String = "pw", id: Long = 5) =
        RestUserEntity(id = id, username = username, password = encoder.encode(password)!!)

    @Test
    fun `createUser stores a bcrypt hash, never the password, and names a blank user anon`() {
        val saved = argumentCaptor<RestUserEntity>()

        service.createUser("sam", "secret")
        service.createUser("", "other")

        verify(users, org.mockito.Mockito.times(2)).save(saved.capture())
        val sam = saved.allValues[0]
        assertEquals("sam", sam.username)
        assertNotEquals("secret", sam.password)
        assertTrue(encoder.matches("secret", sam.password))
        assertEquals("anon", saved.allValues[1].username)
    }

    @Test
    fun `loginAsUser issues a token this service reads back, and refuses a wrong password or an unknown user`() {
        `when`(users.findByUsername("sam")).thenReturn(userRow())
        `when`(users.findByUsername("nobody")).thenReturn(null)
        `when`(verifier.verify(anyString())).thenReturn(null)

        val token = service.loginAsUser("sam", "pw")
        assertNotNull(token)
        val claims = service.validateToken(token!!)
        assertNotNull(claims)
        assertEquals("sam", claims!!.subject)
        assertEquals(5L, (claims["userId"] as Number).toLong())

        assertNull(service.loginAsUser("sam", "wrong"))
        assertNull(service.loginAsUser("nobody", "pw"))
    }

    @Test
    fun `a token from another signing key or garbage is rejected`() {
        `when`(verifier.verify(anyString())).thenReturn(null)
        val foreign = Jwts.builder().subject("sam").signWith(Jwts.SIG.HS512.key().build()).compact()

        assertNull(service.validateToken(foreign))
        assertNull(service.validateToken("not.a.jwt"))
    }

    @Test
    fun `grantGuestToken is a guest subject and creates the guest row once`() {
        `when`(users.existsByUsername("guest-desktop-user")).thenReturn(false)
        `when`(verifier.verify(anyString())).thenReturn(null)

        val token = service.grantGuestToken("guest-desktop-user")
        val claims = service.validateToken(token)!!
        assertEquals("guest", claims.subject)
        assertEquals(-1L, (claims["userId"] as Number).toLong())

        val saved = argumentCaptor<RestUserEntity>()
        verify(users).save(saved.capture())
        assertEquals("guest-desktop-user", saved.lastValue.username)
        assertEquals("", saved.lastValue.password)
    }

    @Test
    fun `grantUserToken names the user as subject and creates the row when it is missing`() {
        `when`(users.existsByUsername("sam")).thenReturn(false)
        `when`(verifier.verify(anyString())).thenReturn(null)

        val claims = service.validateToken(service.grantUserToken("sam"))!!
        assertEquals("sam", claims.subject)
        val saved = argumentCaptor<RestUserEntity>()
        verify(users, org.mockito.Mockito.atLeastOnce()).save(saved.capture())
        assertEquals("sam", saved.lastValue.username)
    }

    @Test
    fun `validateToken prefers the Keycloak verifier and creates the backend user for its subject`() {
        val keycloak = Jwts.claims().subject("kc-sam").add("userId", -1L).build()
        `when`(verifier.verify("kc-token")).thenReturn(keycloak)
        `when`(users.existsByUsername("kc-sam")).thenReturn(false)

        val claims = service.validateToken("kc-token")
        assertEquals("kc-sam", claims!!.subject)
        val saved = argumentCaptor<RestUserEntity>()
        verify(users).save(saved.capture())
        assertEquals("kc-sam", saved.lastValue.username)
    }

    @Test
    fun `ensureUser does nothing for a blank name or an existing user`() {
        `when`(users.existsByUsername("sam")).thenReturn(true)

        service.ensureUser("")
        service.ensureUser("sam")

        verify(users, never()).save(any())
    }

    @Test
    fun `deleteUser removes devices and activities before the user, only with the right password`() {
        val sam = userRow()
        `when`(users.findByUsername("sam")).thenReturn(sam)
        val device = DeviceStatus()
        val activity = UserActivity(actionType = "LOGIN", user = sam)
        `when`(devices.findByUser(sam)).thenReturn(listOf(device))
        `when`(activities.findByUserUsername("sam")).thenReturn(listOf(activity))

        service.deleteUser("sam", "pw")

        val order = org.mockito.Mockito.inOrder(devices, activities, users)
        order.verify(devices).deleteAll(listOf(device))
        order.verify(activities).deleteAll(listOf(activity))
        order.verify(users).delete(sam)

        service.deleteUser("sam", "wrong")
        verify(users, org.mockito.Mockito.times(1)).delete(sam)
    }

    @Test
    fun `updateProfile copies the fields, keeps the hash and logs the change, and an unknown user fails`() {
        val sam = userRow()
        `when`(users.findByUsername("sam")).thenReturn(sam)
        `when`(users.findByUsername("nobody")).thenReturn(null)

        service.updateProfile("sam", ProfileUpdateDto(fullName = "Sam Uel", email = "sam@x", bio = "hi"))

        val saved = argumentCaptor<RestUserEntity>()
        verify(users).save(saved.capture())
        assertEquals("Sam Uel", saved.lastValue.fullName)
        assertEquals("sam@x", saved.lastValue.email)
        assertEquals("hi", saved.lastValue.bio)
        assertEquals(sam.password, saved.lastValue.password)
        val logged = argumentCaptor<UserActivity>()
        verify(activities).save(logged.capture())
        assertEquals("UPDATE_PROFILE", logged.lastValue.actionType)

        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("nobody", ProfileUpdateDto("a", "b", "c")) }
    }
}
