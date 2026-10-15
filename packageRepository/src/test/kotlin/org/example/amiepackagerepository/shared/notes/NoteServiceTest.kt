package org.example.amiepackagerepository.shared.notes

import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObjectMeta
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
import org.mockito.kotlin.eq
import java.time.Instant
import java.util.Optional

/** The rules a note must satisfy, and who may remove one. */
@ExtendWith(MockitoExtension::class)
class NoteServiceTest {
    @Mock private lateinit var notes: PackageNoteRepository
    @Mock private lateinit var users: RestUserRepository
    @Mock private lateinit var store: PackageStoreClient

    private lateinit var service: NoteService
    private val sam = RestUserEntity(id = 1, username = "sam", nickname = "Sam the Maker")
    private val eve = RestUserEntity(id = 2, username = "eve")
    private val path = "uploads/sam/jar/echo.jar"
    private val meta = StoredObjectMeta("echo.jar", path, 10, "\"e\"", "application/java-archive", "http://s3/x")

    @BeforeEach
    fun setUp() {
        service = NoteService(notes, users, store)
    }

    private fun stored(id: Long, author: RestUserEntity, text: String = "hi", on: String = path) =
        PackageNote(id = id, packagePath = on, text = text, author = author, createdAt = Instant.parse("2026-10-15T10:00:00Z"))

    @Test
    fun `a note is cleaned, stored for the signed-in author and answered with the author's display name`() {
        `when`(users.findByUsername("eve")).thenReturn(eve)
        `when`(notes.countByAuthorAndCreatedAtAfter(eq(eve), any())).thenReturn(0)
        `when`(store.head(path)).thenReturn(meta)
        `when`(notes.countByPackagePath(path)).thenReturn(3)
        `when`(notes.save(any<PackageNote>())).thenAnswer { (it.arguments[0] as PackageNote).copy(id = 7) }

        val note = service.add("eve", path, "  Works on my Uno!​\r\n\r\n\r\n\r\nThanks <b>a lot</b>  ")

        val saved = argumentCaptor<PackageNote>()
        verify(notes).save(saved.capture())
        assertEquals("Works on my Uno!\n\nThanks <b>a lot</b>", saved.firstValue.text)   // markup stays text; invisible chars and blank runs go
        assertEquals(eve, saved.firstValue.author)
        assertEquals(7, note.id)
        assertEquals("eve", note.author)
        assertEquals("", note.authorNickname)
        assertEquals(path, note.packagePath)
    }

    @Test
    fun `empty, oversized and badly addressed notes are refused before anything is stored`() {
        assertThrows(IllegalArgumentException::class.java) { service.add("eve", path, "   \n​ ") }
        assertThrows(IllegalArgumentException::class.java) { service.add("eve", "../etc/passwd", "hi") }
        assertThrows(IllegalArgumentException::class.java) { service.add("eve", "uploads/sam/../eve/x", "hi") }
        verify(notes, never()).save(any<PackageNote>())

        // Too long is cut, not refused: the first 2000 characters are kept
        `when`(users.findByUsername("eve")).thenReturn(eve)
        `when`(notes.countByAuthorAndCreatedAtAfter(eq(eve), any())).thenReturn(0)
        `when`(store.head(path)).thenReturn(meta)
        `when`(notes.save(any<PackageNote>())).thenAnswer { it.arguments[0] as PackageNote }
        val note = service.add("eve", path, "x".repeat(5000))
        assertEquals(2000, note.text.length)
    }

    @Test
    fun `a note needs a known author and an existing package`() {
        `when`(users.findByUsername("ghost")).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service.add("ghost", path, "hi") }

        `when`(users.findByUsername("eve")).thenReturn(eve)
        `when`(notes.countByAuthorAndCreatedAtAfter(eq(eve), any())).thenReturn(0)
        `when`(store.head("uploads/sam/jar/gone.jar")).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service.add("eve", "uploads/sam/jar/gone.jar", "hi") }
        verify(notes, never()).save(any<PackageNote>())
    }

    @Test
    fun `more than ten notes a minute, or two hundred on one package, is too many`() {
        `when`(users.findByUsername("eve")).thenReturn(eve)
        `when`(notes.countByAuthorAndCreatedAtAfter(eq(eve), any())).thenReturn(NoteService.PER_MINUTE)
        assertThrows(TooManyNotesException::class.java) { service.add("eve", path, "spam") }
        verify(store, never()).head(any())

        `when`(notes.countByAuthorAndCreatedAtAfter(eq(eve), any())).thenReturn(0)
        `when`(store.head(path)).thenReturn(meta)
        `when`(notes.countByPackagePath(path)).thenReturn(NoteService.PER_PACKAGE)
        assertThrows(TooManyNotesException::class.java) { service.add("eve", path, "one more") }
        verify(notes, never()).save(any<PackageNote>())
    }

    @Test
    fun `the author and the package owner may remove a note, nobody else`() {
        `when`(notes.findById(5)).thenReturn(Optional.of(stored(5, eve)))
        assertTrue(service.delete("eve", 5))      // the author
        assertTrue(service.delete("sam", 5))      // the package's owner (uploads/sam/...)
        assertThrows(SecurityException::class.java) { service.delete("mallory", 5) }
        verify(notes, org.mockito.Mockito.times(2)).delete(any<PackageNote>())

        `when`(notes.findById(6)).thenReturn(Optional.empty())
        assertFalse(service.delete("eve", 6))
    }

    @Test
    fun `listing checks the path and answers newest first with the authors' names`() {
        `when`(notes.findByPackagePathOrderByCreatedAtDesc(path)).thenReturn(listOf(stored(2, sam, "second"), stored(1, eve, "first")))
        val listed = service.list(path)
        assertEquals(listOf(2L, 1L), listed.map { it.id })
        assertEquals("Sam the Maker", listed[0].authorNickname)
        assertEquals("eve", listed[1].author)
        assertThrows(IllegalArgumentException::class.java) { service.list("etc/passwd") }
    }
}
