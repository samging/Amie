package org.example.amiepackagerepository.shared.transactionalMiddleware.repository

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * JPA Repository for DeviceStatus entities.
 * Handles database operations for retrieving device statuses by user or specific username.
 */
@Repository
interface DeviceStatusRepository : JpaRepository<DeviceStatus, Long> {
    fun findByUser(user: RestUserEntity): List<DeviceStatus>
    fun findByUserUsername(username: String): List<DeviceStatus>
}