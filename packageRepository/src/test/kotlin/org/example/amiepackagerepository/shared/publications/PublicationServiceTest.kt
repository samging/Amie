package org.example.amiepackagerepository.shared.publications

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PublicationServiceTest {
    @Mock private lateinit var rows: PublicationRepository
    @Mock private lateinit var users: RestUserRepository
    private lateinit var service: PublicationService
    private val sam = RestUserEntity(id = 1, username = "sam")

    @BeforeEach
    fun setUp() { service = PublicationService(rows, users) }

    @Test
    fun `a publication is cleaned and stored for its owner`() {
        `when`(users.findByUsername("sam")).thenReturn(sam)
        `when`(rows.countByUser(sam)).thenReturn(2)
        `when`(rows.save(any<Publication>())).thenAnswer { (it.arguments[0] as Publication).copy(id = 9) }
        val dto = service.add("sam", NewPublicationDto(title = "  Edge AI on​ Arduino ", venue = "IoT Conf", year = 2026, url = " https://example.org/paper ", doi = "https://doi.org/10.1234/abc.5"))
        val saved = argumentCaptor<Publication>().also { verify(rows).save(it.capture()) }.firstValue
        assertEquals("Edge AI on Arduino", saved.title)
        assertEquals("https://example.org/paper", saved.url)
        assertEquals("10.1234/abc.5", saved.doi)
        assertEquals(2026, saved.year)
        assertEquals(9, dto.id)
        assertEquals("sam", dto.author)
    }

    @Test
    fun `links must be http or https, DOIs must look like DOIs, titles and years are checked`() {
        assertThrows(IllegalArgumentException::class.java) { PublicationService.url("javascript:alert(1)") }
        assertThrows(IllegalArgumentException::class.java) { PublicationService.url("data:text/html,hi") }
        assertThrows(IllegalArgumentException::class.java) { PublicationService.url("ftp://example.org/x") }
        assertEquals("", PublicationService.url("  "))
        assertEquals("http://example.org/a?b=c", PublicationService.url("http://example.org/a?b=c"))
        assertThrows(IllegalArgumentException::class.java) { PublicationService.doi("not-a-doi") }
        assertEquals("", PublicationService.doi(""))
        assertEquals("10.5555/x.y", PublicationService.doi("doi:10.5555/x.y"))

        `when`(users.findByUsername("sam")).thenReturn(sam)
        assertThrows(IllegalArgumentException::class.java) { service.add("sam", NewPublicationDto(title = "   ")) }
        assertThrows(IllegalArgumentException::class.java) { service.add("sam", NewPublicationDto(title = "x", year = 1800)) }
        `when`(rows.countByUser(sam)).thenReturn(PublicationService.MAX_PER_USER)
        assertThrows(IllegalArgumentException::class.java) { service.add("sam", NewPublicationDto(title = "one more")) }
        verify(rows, never()).save(any<Publication>())
        `when`(users.findByUsername("ghost")).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service.add("ghost", NewPublicationDto(title = "x")) }
    }

    @Test
    fun `only the owner removes a publication, an administrator with force`() {
        val mine = Publication(id = 4, title = "t", user = sam)
        `when`(rows.findById(4)).thenReturn(Optional.of(mine))
        assertThrows(SecurityException::class.java) { service.delete("eve", 4) }
        assertTrue(service.delete("eve", 4, force = true))
        assertTrue(service.delete("sam", 4))
        `when`(rows.findById(5)).thenReturn(Optional.empty())
        assertFalse(service.delete("sam", 5))
        `when`(users.findByUsername("nobody")).thenReturn(null)
        assertEquals(emptyList<PublicationDto>(), service.list("nobody"))
    }
}
