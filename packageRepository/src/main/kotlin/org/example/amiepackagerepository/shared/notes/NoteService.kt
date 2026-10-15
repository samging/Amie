package org.example.amiepackagerepository.shared.notes

import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.profile.ProfileRules
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** Too many notes in too short a time; the controller answers 429. */
class TooManyNotesException(message: String) : RuntimeException(message)

/**
 * Notes on packages. The rules, enforced here whatever the client sends:
 * - a note needs a signed-in author and a package that exists in the store;
 * - 1 to [ProfileRules.NOTE_MAX] characters of plain text after cleaning (see [ProfileRules.text]);
 * - at most [PER_MINUTE] notes per person per minute and [PER_PACKAGE] notes per package;
 * - only the author or the package's owner may remove a note.
 */
@Service
class NoteService(
    private val notes: PackageNoteRepository,
    private val users: RestUserRepository,
    private val store: PackageStoreClient,
) {
    private val log = LoggerFactory.getLogger(NoteService::class.java)

    companion object {
        const val PER_MINUTE = 10L
        const val PER_PACKAGE = 200L
        const val PAGE = 200
    }

    /** The notes on a package, newest first. */
    fun list(packagePath: String): List<NoteDto> {
        require(PackageStoreClient.isAllowedKey(packagePath)) { "Invalid path" }
        return notes.findByPackagePathOrderByCreatedAtDesc(packagePath).take(PAGE).map { NoteDto.of(it) }
    }

    @Transactional
    fun add(subject: String, packagePath: String, rawText: String?): NoteDto {
        require(PackageStoreClient.isAllowedKey(packagePath)) { "Invalid path" }
        val text = ProfileRules.text(rawText, ProfileRules.NOTE_MAX)
        require(text.isNotEmpty()) { "The note is empty" }
        val author = users.findByUsername(subject) ?: throw NoSuchElementException("No such user")
        if (notes.countByAuthorAndCreatedAtAfter(author, Instant.now().minus(Duration.ofMinutes(1))) >= PER_MINUTE) {
            throw TooManyNotesException("At most $PER_MINUTE notes a minute; try again shortly")
        }
        if (store.head(packagePath) == null) throw NoSuchElementException("No such package")
        if (notes.countByPackagePath(packagePath) >= PER_PACKAGE) throw TooManyNotesException("This package already has $PER_PACKAGE notes")
        val saved = notes.save(PackageNote(packagePath = packagePath, text = text, author = author))
        log.info("{} left a note on {}", subject, packagePath)
        return NoteDto.of(saved)
    }

    /** Removes a note; false when there was none. The author and the package's owner may do it, nobody else. */
    @Transactional
    fun delete(subject: String, id: Long): Boolean {
        val note = notes.findById(id).orElse(null) ?: return false
        val mayDelete = note.author?.username == subject || PackageStoreClient.ownerOf(note.packagePath) == subject
        if (!mayDelete) throw SecurityException("Only the author or the package's owner can remove this note")
        notes.delete(note)
        return true
    }

    /** When a package is removed, its notes go with it. */
    @Transactional
    fun deleteForPackage(packagePath: String): Long = notes.deleteByPackagePath(packagePath)
}
