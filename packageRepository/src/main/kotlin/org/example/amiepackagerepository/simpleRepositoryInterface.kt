package org.example.amiepackagerepository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository


//ORM goes brrr here....
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.username LIKE 'guest-%'")
    fun findIfGuest(username: String): List<User>

    fun existsByUsername(username: String): Boolean
}

@Repository
interface DeviceStatusRepository : JpaRepository<DeviceStatus, Long> {
    fun findByUser(user: User): List<DeviceStatus>
    fun findByUserUsername(username: String): List<DeviceStatus>
}

