package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table


/**
 * JPA Entity representing a user in the system.
 * Contains core authentication data including username and encrypted password.
 */
@Entity
@Table(name = "users")
data class RestUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val username: String = "",
    val password: String = ""
)

