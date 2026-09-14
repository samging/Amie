package org.example.amiepackagerepository.shared.transactionalMiddleware.repository

import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.UserActivity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Repository for managing UserActivity entities.
 */
@Repository
interface UserActivityRepository : JpaRepository<UserActivity, Long> {
    fun findByUserUsername(username: String): List<UserActivity>
}
