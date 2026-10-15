package org.example.amiepackagerepository.shared.stats

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
import jakarta.persistence.UniqueConstraint
import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.requireSubject
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** How often a package was downloaded through the website. */
@Entity
@Table(name = "package_stats", indexes = [Index(name = "idx_package_stats_path", columnList = "packagePath", unique = true)])
data class PackageStat(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(length = 512, unique = true) val packagePath: String = "",
    val downloads: Long = 0,
)

/** A person saved (liked) a package; one row per person and package. */
@Entity
@Table(name = "package_likes", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "packagePath"])], indexes = [Index(name = "idx_package_likes_path", columnList = "packagePath")])
data class PackageLike(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(length = 512) val packagePath: String = "",
    val createdAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") @JsonIgnore val user: RestUserEntity? = null,
)

interface PackageStatRepository : JpaRepository<PackageStat, Long> {
    fun findByPackagePath(packagePath: String): PackageStat?
    fun findByPackagePathStartingWith(prefix: String): List<PackageStat>
    fun findByPackagePathIn(paths: Collection<String>): List<PackageStat>
}

interface PackageLikeRepository : JpaRepository<PackageLike, Long> {
    fun findByUserAndPackagePath(user: RestUserEntity, packagePath: String): PackageLike?
    fun findByUser(user: RestUserEntity): List<PackageLike>
    fun countByPackagePath(packagePath: String): Long
    fun findByPackagePathStartingWith(prefix: String): List<PackageLike>
    fun findByPackagePathIn(paths: Collection<String>): List<PackageLike>
    fun deleteByPackagePath(packagePath: String): Long
}

/** Downloads and likes of one package. */
data class PackageStatsDto(val path: String, val downloads: Long, val likes: Long)

/**
 * Download counts and likes ("saves"). A download is counted when the website hands out a file
 * (`POST /stats/download`), a like is one row per person and package, toggled with the person's token.
 * Counts are read per author (every package below `uploads/<author>/`) so a profile can sort by them.
 */
@Service
class StatsService(private val stats: PackageStatRepository, private val likes: PackageLikeRepository, private val users: RestUserRepository) {
    companion object { const val MAX_LIKES_PER_USER = 2000L }

    @Transactional
    fun trackDownload(path: String): Long {
        require(PackageStoreClient.isAllowedKey(path)) { "Invalid path" }
        val row = stats.findByPackagePath(path) ?: PackageStat(packagePath = path)
        return stats.save(row.copy(downloads = row.downloads + 1)).downloads
    }

    /** Toggles the caller's like; answers whether it is now liked and the package's count. */
    @Transactional
    fun toggleLike(subject: String, path: String): Pair<Boolean, Long> {
        require(PackageStoreClient.isAllowedKey(path)) { "Invalid path" }
        val user = users.findByUsername(subject) ?: throw NoSuchElementException("No such user")
        val existing = likes.findByUserAndPackagePath(user, path)
        if (existing != null) {
            likes.delete(existing)
            return false to likes.countByPackagePath(path)
        }
        require(likes.findByUser(user).size < MAX_LIKES_PER_USER) { "That is a lot of likes; remove some first" }
        likes.save(PackageLike(packagePath = path, user = user))
        return true to likes.countByPackagePath(path)
    }

    /** The paths the caller liked, newest first. */
    fun likedBy(subject: String): List<String> {
        val user = users.findByUsername(subject) ?: return emptyList()
        return likes.findByUser(user).sortedByDescending { it.createdAt }.map { it.packagePath }
    }

    /** Stats of every package below `uploads/<author>/`; packages nobody touched are absent. */
    fun forAuthor(author: String): List<PackageStatsDto> {
        require(author.isNotBlank() && !author.contains('/') && !author.contains("..")) { "Invalid author" }
        val prefix = "${PackageStoreClient.UPLOADS}$author/"
        val downloads = stats.findByPackagePathStartingWith(prefix).associate { it.packagePath to it.downloads }
        val likeCounts = likes.findByPackagePathStartingWith(prefix).groupingBy { it.packagePath }.eachCount()
        return (downloads.keys + likeCounts.keys).sorted().map { PackageStatsDto(it, downloads[it] ?: 0, (likeCounts[it] ?: 0).toLong()) }
    }

    /** Stats of a few packages, in the same order; zeros for unknown ones. */
    fun forPaths(paths: List<String>): List<PackageStatsDto> {
        if (paths.isEmpty()) return emptyList()
        val downloads = stats.findByPackagePathIn(paths).associate { it.packagePath to it.downloads }
        val likeCounts = likes.findByPackagePathIn(paths).groupingBy { it.packagePath }.eachCount()
        return paths.map { PackageStatsDto(it, downloads[it] ?: 0, (likeCounts[it] ?: 0).toLong()) }
    }

    @Transactional
    fun forgetPackage(path: String) {
        stats.findByPackagePath(path)?.let { stats.delete(it) }
        likes.deleteByPackagePath(path)
    }
}

@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class StatsController(private val statsService: StatsService, private val userService: UserService) {
    /** Stats for an author's packages (`username=`) or for a list of paths (`path=` repeated). */
    @GetMapping("/stats")
    fun stats(@RequestParam(required = false) username: String?, @RequestParam(required = false) path: List<String>?): List<PackageStatsDto> =
        when {
            !username.isNullOrBlank() -> statsService.forAuthor(username)
            !path.isNullOrEmpty() -> statsService.forPaths(path.take(200).filter { PackageStoreClient.isAllowedKey(it) })
            else -> throw IllegalArgumentException("Give username= or path=")
        }

    /** Counts a download; the website calls this when it hands out a file. */
    @PostMapping("/stats/download")
    fun download(@RequestParam path: String): Map<String, Any> = mapOf("path" to path, "downloads" to statsService.trackDownload(path))

    /** The caller's liked packages. */
    @GetMapping("/likes")
    fun likes(@RequestHeader("Authorization") authHeader: String): List<String> = statsService.likedBy(userService.requireSubject(authHeader))

    /** Likes or unlikes a package for the caller. */
    @PostMapping("/likes")
    fun toggle(@RequestParam path: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Map<String, Any>> {
        val (liked, count) = statsService.toggleLike(userService.requireSubject(authHeader), path)
        return ResponseEntity.ok(mapOf("path" to path, "liked" to liked, "likes" to count))
    }
}
