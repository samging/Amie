package org.example.amiepackagerepository.transactionalMiddleware.repository

import org.example.amiepackagerepository.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.transactionalMiddleware.entities.entities.DeviceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<RestUserEntity, Long> {
    fun findByUsername(username: String): RestUserEntity?
    
    @Query("SELECT u FROM RestUserEntity u WHERE u.username = :username AND u.username LIKE 'guest-%'")
    fun findIfGuest(username: String): List<RestUserEntity>

    fun existsByUsername(username: String): Boolean
}

@Repository
interface DeviceStatusRepository : JpaRepository<DeviceStatus, Long> {
    fun findByUser(user: RestUserEntity): List<DeviceStatus>
    fun findByUserUsername(username: String): List<DeviceStatus>
}
