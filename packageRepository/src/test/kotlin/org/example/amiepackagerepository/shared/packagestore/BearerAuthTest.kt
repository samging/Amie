package org.example.amiepackagerepository.shared.packagestore

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class BearerAuthTest {
    @Mock private lateinit var userService: UserService
    @Mock private lateinit var claims: Claims

    private fun statusOf(block: () -> Unit): HttpStatus =
        HttpStatus.valueOf(assertThrows(ResponseStatusException::class.java) { block() }.statusCode.value())

    @Test
    fun `requireSubject accepts a valid bearer and rejects everything else with 401`() {
        `when`(claims.subject).thenReturn("sam")
        `when`(userService.validateToken("ok")).thenReturn(claims)
        `when`(userService.validateToken("bad")).thenReturn(null)

        assertEquals("sam", userService.requireSubject("Bearer ok"))
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf { userService.requireSubject("Bearer bad") })
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf { userService.requireSubject(null) })
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf { userService.requireSubject("Bearer ") })
    }

    @Test
    fun `requireUser is 403 for another user's token`() {
        `when`(claims.subject).thenReturn("sam")
        `when`(userService.validateToken("ok")).thenReturn(claims)

        assertEquals("sam", userService.requireUser("Bearer ok", "sam"))
        assertEquals(HttpStatus.FORBIDDEN, statusOf { userService.requireUser("Bearer ok", "tom") })
    }
}
