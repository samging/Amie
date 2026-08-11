package org.example.amiepackagerepository

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class AmiePackageRepositoryApplication

fun main(args: Array<String>) {
	runApplication<AmiePackageRepositoryApplication>(*args)
}
