package org.example.amiepackagerepository.shared.badges

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
import jakarta.persistence.UniqueConstraint
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** The badges an administrator may grant; the key is stored, the label and meaning are shown. */
object BadgeCatalog {
    data class Badge(val key: String, val label: String, val meaning: String)

    val all: List<Badge> = listOf(
        Badge("verified", "Verified", "The administrators checked who this person is"),
        Badge("maintainer", "Maintainer", "Keeps packages others rely on up to date"),
        Badge("contributor", "Contributor", "Contributed to Amie itself"),
        Badge("researcher", "Researcher", "Publishes research with Amie"),
        Badge("helper", "Helper", "Answers questions and leaves useful notes"),
        Badge("early", "Early adopter", "Used Amie before most people"),
    )
    private val byKey = all.associateBy { it.key }
    fun byKey(key: String): Badge? = byKey[key]
    fun isKnown(key: String) = key in byKey
}

@Entity
@Table(name = "user_badges", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "badge"])])
data class UserBadge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(length = 32)
    val badge: String = "",
    @Column(length = 64)
    val grantedBy: String = "",
    val grantedAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    val user: RestUserEntity? = null,
)

interface UserBadgeRepository : JpaRepository<UserBadge, Long> {
    fun findByUser(user: RestUserEntity): List<UserBadge>
    fun findByUserAndBadge(user: RestUserEntity, badge: String): UserBadge?
    fun findByUserIn(users: Collection<RestUserEntity>): List<UserBadge>
}

/** Who has which badge; only administrators grant and revoke (see `AdminController`). */
@Service
class BadgeService(private val badges: UserBadgeRepository) {
    fun of(user: RestUserEntity): List<String> = badges.findByUser(user).map { it.badge }.sorted()

    /** The badge keys of many users at once, for lists. */
    fun ofAll(users: List<RestUserEntity>): Map<Long, List<String>> =
        if (users.isEmpty()) emptyMap() else badges.findByUserIn(users).groupBy({ it.user?.id ?: -1L }, { it.badge }).mapValues { it.value.sorted() }

    /** Grants [badge]; false when the person already had it. Throws for a key that is not in the catalogue. */
    @Transactional
    fun grant(user: RestUserEntity, badge: String, grantedBy: String): Boolean {
        require(BadgeCatalog.isKnown(badge)) { "No such badge: $badge" }
        if (badges.findByUserAndBadge(user, badge) != null) return false
        badges.save(UserBadge(badge = badge, grantedBy = grantedBy, user = user))
        return true
    }

    /** Revokes [badge]; false when the person did not have it. */
    @Transactional
    fun revoke(user: RestUserEntity, badge: String): Boolean {
        val existing = badges.findByUserAndBadge(user, badge) ?: return false
        badges.delete(existing)
        return true
    }

    @Transactional
    fun revokeAll(user: RestUserEntity) = badges.deleteAll(badges.findByUser(user))
}
