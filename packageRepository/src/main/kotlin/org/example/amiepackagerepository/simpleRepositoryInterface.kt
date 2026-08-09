package org.example.amiepackagerepository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository


//ORM goes brrr here....
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
}

