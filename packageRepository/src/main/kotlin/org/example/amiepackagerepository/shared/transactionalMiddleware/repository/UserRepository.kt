package org.example.amiepackagerepository.shared.transactionalMiddleware.repository

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * JPA Repository for user management.
 * Provides standard CRUD operations and custom queries for finding users by username
 * and identifying guest accounts.
 */
@Repository
interface UserRepository : JpaRepository<RestUserEntity, Long> {
    fun findByUsername(username: String): RestUserEntity?

    @Query("SELECT u FROM RestUserEntity u WHERE u.username = :username AND u.username LIKE 'guest-%'")
    fun findIfGuest(username: String): List<RestUserEntity>

    fun existsByUsername(username: String): Boolean
}