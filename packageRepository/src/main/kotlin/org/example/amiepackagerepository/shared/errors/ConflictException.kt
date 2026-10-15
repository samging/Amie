package org.example.amiepackagerepository.shared.errors

/** Something already holds what the caller wants (a nickname, a username); answered as 409. */
class ConflictException(message: String) : RuntimeException(message)
