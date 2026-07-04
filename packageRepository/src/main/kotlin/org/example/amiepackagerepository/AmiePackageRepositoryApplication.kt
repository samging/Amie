package org.example.amiepackagerepository

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class AmiePackageRepositoryApplication

fun main(args: Array<String>) {
	runApplication<AmiePackageRepositoryApplication>(*args)
}