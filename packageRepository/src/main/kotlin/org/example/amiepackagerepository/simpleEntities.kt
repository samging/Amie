package org.example.amiepackagerepository

import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val username: String = "",
    val password: String = ""
)

@Entity
@Table(name = "device_statuses")
data class DeviceStatus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val deviceKey: String = "",
    val name: String = "",
    val port: String = "",
    val deviceEndpoint: String? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    val user: User? = null
)