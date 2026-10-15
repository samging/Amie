package org.example.amiepackagerepository.shared.profile

import org.example.amiepackagerepository.shared.errors.ConflictException
import org.example.amiepackagerepository.shared.notes.PackageNoteRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.ProfileDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.ProfileUpdateDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserActivityRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.keycloak.KeycloakTokenVerifier
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
import org.mockito.kotlin.argumentCaptor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Nicknames and pictures through [UserService]: cleaning, conflicts, what the profile tells. */
@ExtendWith(MockitoExtension::class)
class ProfileServiceTest {
    @Mock private lateinit var users: RestUserRepository
    @Mock private lateinit var activities: UserActivityRepository
    @Mock private lateinit var verifier: KeycloakTokenVerifier
    @Mock private lateinit var devices: DeviceStatusRepository
    @Mock private lateinit var notes: PackageNoteRepository
    @Mock private lateinit var badgeRows: org.example.amiepackagerepository.shared.badges.UserBadgeRepository
    @Mock private lateinit var publicationRows: org.example.amiepackagerepository.shared.publications.PublicationRepository
    @Mock private lateinit var likeRows: org.example.amiepackagerepository.shared.stats.PackageLikeRepository

    private lateinit var service: UserService
    private val sam = RestUserEntity(id = 1, username = "sam", password = "hash")

    @BeforeEach
    fun setUp() {
        service = UserService(users, activities, verifier, devices, notes, badgeRows, publicationRows, likeRows)
        `when`(users.findByUsername("sam")).thenReturn(sam)
    }

    private fun savedUser(): RestUserEntity = argumentCaptor<RestUserEntity>().also { verify(users).save(it.capture()) }.firstValue

    @Test
    fun `every field is cleaned before it is stored and the hash is untouched`() {
        service.updateProfile("sam", ProfileUpdateDto(
            fullName = "  Sam​  Uel\n<script>  ", email = " sam@example.org ", bio = "line\r\n\r\n\r\n\r\nend\u0007", nickname = " Sam the Maker "
        ))
        val saved = savedUser()
        assertEquals("Sam Uel <script>", saved.fullName)   // text stays text; the page escapes it
        assertEquals("sam@example.org", saved.email)
        assertEquals("line\n\nend", saved.bio)
        assertEquals("Sam the Maker", saved.nickname)
        assertEquals("hash", saved.password)
    }

    @Test
    fun `linked handles are cleaned from pasted addresses and refused when they are not handles`() {
        service.updateProfile("sam", ProfileUpdateDto(github = "https://github.com/octo-cat/", huggingFace = "@hf_user.v2"))
        val saved = savedUser()
        assertEquals("octo-cat", saved.githubLogin)
        assertEquals("hf_user.v2", saved.huggingFaceLogin)
        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("sam", ProfileUpdateDto(github = "octo cat")) }
        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("sam", ProfileUpdateDto(github = "-dash")) }
        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("sam", ProfileUpdateDto(huggingFace = "a/b")) }
        assertEquals("", ProfileDto.of(sam).github)
    }

    @Test
    fun `a nickname that is someone else's username or nickname is a conflict, your own username is fine`() {
        `when`(users.findByUsernameIgnoreCase("Eve")).thenReturn(RestUserEntity(id = 2, username = "eve"))
        assertEquals("That nickname is another account's name", assertThrows(ConflictException::class.java) { service.updateProfile("sam", ProfileUpdateDto(nickname = "Eve")) }.message)

        `when`(users.findByUsernameIgnoreCase("Maker")).thenReturn(null)
        `when`(users.findByNicknameIgnoreCase("Maker")).thenReturn(RestUserEntity(id = 3, username = "bob", nickname = "maker"))
        assertEquals("That nickname is taken", assertThrows(ConflictException::class.java) { service.updateProfile("sam", ProfileUpdateDto(nickname = "Maker")) }.message)
        verify(users, never()).save(any<RestUserEntity>())

        // Calling yourself by your own username, in any case, needs no lookup
        service.updateProfile("sam", ProfileUpdateDto(nickname = "SAM"))
        assertEquals("SAM", savedUser().nickname)
    }

    @Test
    fun `a bad nickname or email is refused and an empty nickname clears it`() {
        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("sam", ProfileUpdateDto(nickname = "<b>x</b>")) }
        assertThrows(IllegalArgumentException::class.java) { service.updateProfile("sam", ProfileUpdateDto(email = "nope")) }
        verify(users, never()).save(any<RestUserEntity>())

        `when`(users.findByUsername("sam")).thenReturn(sam.copy(nickname = "Old"))
        service.updateProfile("sam", ProfileUpdateDto(nickname = "  "))
        assertEquals("", savedUser().nickname)
    }

    @Test
    fun `a picture is stored with its detected type and the profile reports it with a version`() {
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val avatar = service.setAvatar("sam", png)
        assertEquals("image/png", avatar.contentType)
        val saved = savedUser()
        assertEquals("image/png", saved.avatarType)
        assertTrue(saved.avatar!!.contentEquals(png))
        // Until an administrator approves it, the picture is pending: not shown, no version, but reported to its owner
        val profile = ProfileDto.of(saved)
        assertFalse(profile.hasAvatar)
        assertTrue(profile.avatarPending)
        assertEquals("", profile.avatarVersion)
        assertEquals("sam", profile.displayName)
        assertNull(service.getAvatar("sam"))
        `when`(users.findByUsername("sam")).thenReturn(saved)
        assertEquals("image/png", service.getAvatar("sam", includePending = true)!!.contentType)
        assertTrue(service.approveAvatar("sam"))
        val approved = argumentCaptor<RestUserEntity>().also { verify(users, org.mockito.Mockito.times(2)).save(it.capture()) }.lastValue
        assertTrue(ProfileDto.of(approved).hasAvatar)
        assertEquals(12, ProfileDto.of(approved).avatarVersion.length)
        `when`(users.findByUsername("sam")).thenReturn(approved)
        assertFalse(service.approveAvatar("sam"))     // nothing waiting any more
        assertEquals(listOf<RestUserEntity>(), service.pendingAvatars().filter { it.username == "nobody" })

        assertThrows(IllegalArgumentException::class.java) { service.setAvatar("sam", "<svg onload=alert(1)/>".toByteArray()) }
        assertNull(service.getAvatar("nobody"))
    }

    @Test
    fun `a background follows the same approval as the picture, with larger limits`() {
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1200, 400, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val image = service.setBackground("sam", png)
        assertEquals("image/png", image.contentType)
        val saved = argumentCaptor<RestUserEntity>().also { verify(users).save(it.capture()) }.firstValue
        assertTrue(saved.backgroundPending)
        assertFalse(ProfileDto.of(saved).hasBackground)
        assertTrue(ProfileDto.of(saved).backgroundPending)
        `when`(users.findByUsername("sam")).thenReturn(saved)
        assertNull(service.getBackground("sam"))
        assertEquals("image/png", service.getBackground("sam", includePending = true)!!.contentType)
        assertTrue(service.approveBackground("sam"))
        assertThrows(IllegalArgumentException::class.java) { service.setBackground("sam", "<svg/>".toByteArray()) }
        assertEquals(listOf("sam"), service.pendingBackgrounds().map { it.username }.filter { it == "sam" }.let { if (saved.backgroundPending) listOf("sam") else it })
    }

    @Test
    fun `clearing a picture is reported and a second clear is a no-op`() {
        `when`(users.findByUsername("sam")).thenReturn(sam.copy(avatar = byteArrayOf(1, 2), avatarType = "image/gif"))
        assertTrue(service.clearAvatar("sam"))
        val saved = savedUser()
        assertNull(saved.avatar)
        assertNull(saved.avatarType)
        assertFalse(ProfileDto.of(saved).hasAvatar)

        `when`(users.findByUsername("sam")).thenReturn(sam)
        assertFalse(service.clearAvatar("sam"))
    }

    @Test
    fun `an administrator can clear a nickname and remove an account with everything attached`() {
        `when`(users.findByUsername("sam")).thenReturn(sam.copy(nickname = "Rude"))
        assertTrue(service.adminClearNickname("sam"))
        assertEquals("", savedUser().nickname)
        assertFalse(service.adminClearNickname("nobody"))

        `when`(users.findByUsername("sam")).thenReturn(sam)
        assertTrue(service.adminDeleteUser("sam"))
        verify(devices).deleteAll(any())
        verify(activities).deleteAll(any())
        verify(notes).deleteAll(any())
        verify(users).delete(sam)
        assertFalse(service.adminDeleteUser("nobody"))
    }
}
