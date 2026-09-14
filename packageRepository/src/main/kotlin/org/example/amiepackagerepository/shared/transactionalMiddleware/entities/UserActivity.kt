package org.example.amiepackagerepository.shared.transactionalMiddleware.entities

import jakarta.persistence.*
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import java.time.LocalDateTime

/**
 * JPA Entity representing an action performed by a user.
 * Tracks the type of action and the time it occurred.
 */
@Entity
@Table(name = "user_activities")
data class UserActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    val actionType: String = "",
    val details: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: RestUserEntity? = null
)
