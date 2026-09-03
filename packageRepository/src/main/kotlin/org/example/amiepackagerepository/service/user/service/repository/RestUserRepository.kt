package org.example.amiepackagerepository.service.user.service.repository

import org.example.amiepackagerepository.service.user.service.entities.RestUserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RestUserRepository : JpaRepository<RestUserEntity, Long> {

    fun save(entity: RestUserEntity)
    override fun delete(entity: RestUserEntity)

    fun existsByUsername(username: String): Boolean?
    fun findByUsername(username: String): RestUserEntity?
}