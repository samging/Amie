package org.example.amiepackagerepository.shared.transactionalMiddleware.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity

/**
 * JPA Entity representing the status of a device.
 * Stores information about the device's unique key, name, port, and endpoint.
 * Each status is associated with a specific user.
 */
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
    @JsonIgnore
    val user: RestUserEntity? = null
)