package org.example.amiepackagerepository.shared.notes

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** A note a signed-in person left on a package (`uploads/<owner>/<lang>/<file>`). Plain text, never HTML. */
@Entity
@Table(name = "package_notes", indexes = [Index(name = "idx_package_notes_path", columnList = "packagePath")])
data class PackageNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(length = 512)
    val packagePath: String = "",
    @Column(length = 2048)
    val text: String = "",
    val createdAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @JsonIgnore
    val author: RestUserEntity? = null,
)

interface PackageNoteRepository : JpaRepository<PackageNote, Long> {
    fun findByPackagePathOrderByCreatedAtDesc(packagePath: String): List<PackageNote>
    fun countByPackagePath(packagePath: String): Long
    fun countByAuthorAndCreatedAtAfter(author: RestUserEntity, after: Instant): Long
    fun findByAuthor(author: RestUserEntity): List<PackageNote>
    fun countByAuthor(author: RestUserEntity): Long
    fun deleteByPackagePath(packagePath: String): Long
}

/** What a client sees of a note: the author by username and display name, never the account row. */
data class NoteDto(
    val id: Long,
    val packagePath: String,
    val author: String,
    val authorNickname: String,
    val authorHasAvatar: Boolean,
    val text: String,
    val createdAt: String,
) {
    companion object {
        fun of(note: PackageNote) = NoteDto(
            id = note.id,
            packagePath = note.packagePath,
            author = note.author?.username ?: "",
            authorNickname = note.author?.nickname.orEmpty(),
            authorHasAvatar = note.author?.avatar != null,
            text = note.text,
            createdAt = note.createdAt.toString(),
        )
    }
}
