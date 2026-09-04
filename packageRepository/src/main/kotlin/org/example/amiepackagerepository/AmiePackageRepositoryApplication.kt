package org.example.amiepackagerepository

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Main entry point for the Amie Package Repository application.
 * Enables asynchronous processing and initializes the Spring Boot context.
 */
@SpringBootApplication
@EnableAsync
class AmiePackageRepositoryApplication

fun main(args: Array<String>) {
	runApplication<AmiePackageRepositoryApplication>(*args)
}
