package org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables

/**
 * Enum defining the supported operations for device status management.
 * Used to distinguish between setting new status data and retrieving existing data.
 */
enum class DeviceActions {
    SET, GET;

    companion object {
        fun _fex1gga() { println("def some magic can happen here")}
    }

}