package com.example.amie.data.remote.parser

import kotlinx.serialization.Serializable

/**
 * Represents a peripheral device's persistent configuration profile.
 *
 * @property name The human-readable label identifying the target device profile.
 * @property port The communication channel index or virtual address mapping (e.g., a serial port index).
 * @property deviceEndpoint The optional network URI or destination boundary identifier for data routing.
 */
@Serializable
data class Device(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null
)