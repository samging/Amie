package org.example.amiepackagerepository.shared.admin

import org.example.amiepackagerepository.shared.badges.BadgeCatalog
import org.example.amiepackagerepository.shared.badges.BadgeService
import org.example.amiepackagerepository.shared.notes.NoteDto
import org.example.amiepackagerepository.shared.publications.PublicationService
import org.example.amiepackagerepository.shared.notes.PackageNoteRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.ProfileDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** One row of the admin's user list. */
data class AdminUserDto(val profile: ProfileDto, val notes: Long, val hasPassword: Boolean)

/** What waits for an administrator: pictures to approve and the notes of the last day, with the counts the inbox badge shows. */
data class AdminInboxDto(val pendingAvatars: Int, val notesLastDay: Long, val total: Long, val pending: List<PendingAvatarDto>, val notes: List<NoteDto>)

/** A picture waiting for approval: who, what type, how big; the bytes come from `GET /avatar?preview=1`. */
data class PendingAvatarDto(val username: String, val nickname: String, val contentType: String, val bytes: Int)

/**
 * Moderation for Keycloak accounts with the admin role ([AdminAccess]): see every account and the latest
 * notes, remove a note, a nickname, a picture or a whole account. Every action is logged with the admin's
 * name. Package files are removed through the website's existing admin routes on the S3 service.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
@RequestMapping("/admin")
class AdminController(
    private val access: AdminAccess,
    private val userService: UserService,
    private val notes: PackageNoteRepository,
    private val badges: BadgeService,
    private val publications: PublicationService,
) {
    private val log = LoggerFactory.getLogger(AdminController::class.java)

    @GetMapping("/me")
    fun me(@RequestHeader("Authorization") authHeader: String): AdminIdentity = access.require(authHeader)

    /** Every account the backend knows, with how many notes each wrote. */
    @GetMapping("/users")
    fun users(@RequestHeader("Authorization") authHeader: String): List<AdminUserDto> {
        access.require(authHeader)
        val users = userService.allUsers()
        val badgesOf = badges.ofAll(users)
        return users.map { user ->
            AdminUserDto(ProfileDto.of(user, badgesOf[user.id].orEmpty()), notes.countByAuthor(user), user.password.isNotEmpty())
        }
    }

    /** The latest notes across every package, newest first, at most [limit]. */
    @GetMapping("/notes")
    fun latestNotes(@RequestParam(defaultValue = "100") limit: Int, @RequestHeader("Authorization") authHeader: String): List<NoteDto> {
        access.require(authHeader)
        return notes.findAll(PageRequest.of(0, limit.coerceIn(1, 500), Sort.by(Sort.Direction.DESC, "createdAt"))).content.map { NoteDto.of(it) }
    }

    @DeleteMapping("/notes/{id}")
    fun deleteNote(@PathVariable id: Long, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val note = notes.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        notes.delete(note)
        log.info("Admin {} removed note {} by {} on {}", admin.username, id, note.author?.username, note.packagePath)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/users/{username}/nickname")
    fun clearNickname(@PathVariable username: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val changed = userService.adminClearNickname(username)
        log.info("Admin {} cleared the nickname of {}: {}", admin.username, username, changed)
        return if (changed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** The admin's inbox: pending pictures and the latest notes of the last 24 hours. */
    @GetMapping("/inbox")
    fun inbox(@RequestHeader("Authorization") authHeader: String): AdminInboxDto {
        access.require(authHeader)
        val pending = userService.pendingAvatars().map { PendingAvatarDto(it.username, it.nickname.orEmpty(), it.avatarType ?: "", it.avatar?.size ?: 0) }
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(1))
        val notesLastDay = notes.countByCreatedAtAfter(since)
        val latest = notes.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))).content.filter { it.createdAt.isAfter(since) }.map { NoteDto.of(it) }
        return AdminInboxDto(pending.size, notesLastDay, pending.size + notesLastDay, pending, latest)
    }

    /** Pictures nobody has approved yet. */
    @GetMapping("/avatars/pending")
    fun pendingAvatars(@RequestHeader("Authorization") authHeader: String): List<PendingAvatarDto> {
        access.require(authHeader)
        return userService.pendingAvatars().map { PendingAvatarDto(it.username, it.nickname.orEmpty(), it.avatarType ?: "", it.avatar?.size ?: 0) }
    }

    /** Approves a waiting picture, after which everyone sees it. */
    @PostMapping("/users/{username}/avatar/approve")
    fun approveAvatar(@PathVariable username: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val approved = userService.approveAvatar(username)
        log.info("Admin {} approved the picture of {}: {}", admin.username, username, approved)
        return if (approved) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** The badges that exist. */
    @GetMapping("/badges")
    fun badgeCatalog(@RequestHeader("Authorization") authHeader: String): List<BadgeCatalog.Badge> {
        access.require(authHeader)
        return BadgeCatalog.all
    }

    /** Grants a badge; 200 with `granted: false` when the person already had it, 400 for an unknown badge. */
    @PostMapping("/users/{username}/badges/{badge}")
    fun grantBadge(@PathVariable username: String, @PathVariable badge: String, @RequestHeader("Authorization") authHeader: String): Map<String, Any> {
        val admin = access.require(authHeader)
        val user = userService.getProfile(username) ?: throw NoSuchElementException("No such user")
        val granted = badges.grant(user, badge, admin.username)
        log.info("Admin {} granted badge {} to {}: {}", admin.username, badge, username, granted)
        return mapOf("granted" to granted, "badges" to badges.of(user))
    }

    @DeleteMapping("/users/{username}/badges/{badge}")
    fun revokeBadge(@PathVariable username: String, @PathVariable badge: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val user = userService.getProfile(username) ?: return ResponseEntity.notFound().build()
        val revoked = badges.revoke(user, badge)
        log.info("Admin {} revoked badge {} from {}: {}", admin.username, badge, username, revoked)
        return if (revoked) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** Removes anyone's publication. */
    @DeleteMapping("/publications/{id}")
    fun deletePublication(@PathVariable id: Long, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val removed = publications.delete(admin.username, id, force = true)
        log.info("Admin {} removed publication {}: {}", admin.username, id, removed)
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/users/{username}/avatar")
    fun clearAvatar(@PathVariable username: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        val removed = userService.getProfile(username) != null && userService.clearAvatar(username)
        log.info("Admin {} removed the picture of {}: {}", admin.username, username, removed)
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** Removes an account and everything that points at it (devices, activities, notes); not the files in the store. */
    @DeleteMapping("/users/{username}")
    fun deleteUser(@PathVariable username: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val admin = access.require(authHeader)
        if (username == admin.username) throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "You cannot remove your own account here")
        val removed = userService.adminDeleteUser(username)
        log.warn("Admin {} removed the account {}: {}", admin.username, username, removed)
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}
