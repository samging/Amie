package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Interface for repository operations on RestUserEntity.
 * Extends JpaRepository to provide standard database access patterns for users.
 */
interface RestUserRepository : JpaRepository<RestUserEntity, Long> {

    fun save(entity: RestUserEntity)
    override fun delete(entity: RestUserEntity)

    fun existsByUsername(username: String): Boolean?
    fun findByUsername(username: String): RestUserEntity?
}