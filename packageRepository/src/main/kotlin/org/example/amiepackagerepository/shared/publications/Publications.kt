package org.example.amiepackagerepository.shared.publications

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.example.amiepackagerepository.shared.packagestore.requireSubject
import org.example.amiepackagerepository.shared.profile.ProfileRules
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

/** A paper, thesis or article a person lists on their profile. Plain text; the URL is the only link. */
@Entity
@Table(name = "publications")
data class Publication(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(length = 200)
    val title: String = "",
    @Column(length = 200)
    val venue: String = "",
    val year: Int? = null,
    @Column(length = 500)
    val url: String = "",
    @Column(length = 100)
    val doi: String = "",
    val createdAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    val user: RestUserEntity? = null,
)

interface PublicationRepository : JpaRepository<Publication, Long> {
    fun findByUserOrderByYearDescCreatedAtDesc(user: RestUserEntity): List<Publication>
    fun countByUser(user: RestUserEntity): Long
}

data class PublicationDto(val id: Long, val title: String, val venue: String, val year: Int?, val url: String, val doi: String, val author: String) {
    companion object {
        fun of(p: Publication) = PublicationDto(p.id, p.title, p.venue, p.year, p.url, p.doi, p.user?.username ?: "")
    }
}

data class NewPublicationDto(val title: String = "", val venue: String = "", val year: Int? = null, val url: String = "", val doi: String = "")

/**
 * Publications: anyone reads, the person adds and removes their own, at most [MAX_PER_USER]. Every field
 * is cleaned ([ProfileRules]); the URL must be `http(s)` so a `javascript:` link can never be stored,
 * and a DOI must look like one (`10.xxxx/...`).
 */
@Service
class PublicationService(private val publications: PublicationRepository, private val users: RestUserRepository) {
    companion object {
        const val MAX_PER_USER = 50L
        const val TITLE_MAX = 200
        const val VENUE_MAX = 200
        const val URL_MAX = 500
        const val DOI_MAX = 100
        private val DOI = Regex("""10\.\d{4,9}/[-._;()/:A-Za-z0-9]+""")

        /** A cleaned `http(s)` address, or an empty string; throws for any other scheme. */
        fun url(value: String?): String {
            val cleaned = ProfileRules.line(value, URL_MAX + 1)
            if (cleaned.isEmpty()) return ""
            require(cleaned.length <= URL_MAX) { "The link is too long" }
            val uri = runCatching { URI(cleaned) }.getOrNull() ?: throw IllegalArgumentException("That is not a valid link")
            require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()) { "A link must start with http:// or https://" }
            return cleaned
        }

        fun doi(value: String?): String {
            val cleaned = ProfileRules.line(value, DOI_MAX + 1).removePrefix("https://doi.org/").removePrefix("doi.org/").removePrefix("doi:").trim()
            if (cleaned.isEmpty()) return ""
            require(cleaned.length <= DOI_MAX && DOI.matches(cleaned)) { "That is not a DOI (10.xxxx/...)" }
            return cleaned
        }
    }

    fun list(username: String): List<PublicationDto> {
        val user = users.findByUsername(username) ?: return emptyList()
        return publications.findByUserOrderByYearDescCreatedAtDesc(user).map { PublicationDto.of(it) }
    }

    @Transactional
    fun add(subject: String, dto: NewPublicationDto): PublicationDto {
        val user = users.findByUsername(subject) ?: throw NoSuchElementException("No such user")
        val title = ProfileRules.line(dto.title, TITLE_MAX)
        require(title.isNotEmpty()) { "A publication needs a title" }
        dto.year?.let { require(it in 1900..2100) { "The year must be between 1900 and 2100" } }
        require(publications.countByUser(user) < MAX_PER_USER) { "At most $MAX_PER_USER publications per person" }
        val saved = publications.save(Publication(title = title, venue = ProfileRules.line(dto.venue, VENUE_MAX), year = dto.year, url = url(dto.url), doi = doi(dto.doi), user = user))
        return PublicationDto.of(saved)
    }

    /** Removes a publication; false when there is none. Only its owner (or an administrator through [force]) may. */
    @Transactional
    fun delete(subject: String, id: Long, force: Boolean = false): Boolean {
        val publication = publications.findById(id).orElse(null) ?: return false
        if (!force && publication.user?.username != subject) throw SecurityException("Only the owner can remove this publication")
        publications.delete(publication)
        return true
    }
}

@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class PublicationController(private val publications: PublicationService, private val userService: UserService) {
    @GetMapping("/publications")
    fun list(@RequestParam username: String): List<PublicationDto> = publications.list(username)

    @PostMapping("/publications")
    fun add(@RequestBody body: NewPublicationDto, @RequestHeader("Authorization") authHeader: String): ResponseEntity<PublicationDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(publications.add(userService.requireSubject(authHeader), body))

    @DeleteMapping("/publications/{id}")
    fun delete(@PathVariable id: Long, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> =
        if (publications.delete(userService.requireSubject(authHeader), id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}
