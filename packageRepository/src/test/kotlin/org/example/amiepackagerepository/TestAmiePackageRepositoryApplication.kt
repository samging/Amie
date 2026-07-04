package org.example.amiepackagerepository

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<AmiePackageRepositoryApplication>().with(TestcontainersConfiguration::class).run(*args)
}
