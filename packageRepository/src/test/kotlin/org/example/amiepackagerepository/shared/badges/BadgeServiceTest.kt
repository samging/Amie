package org.example.amiepackagerepository.shared.badges

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor

@ExtendWith(MockitoExtension::class)
class BadgeServiceTest {
    @Mock private lateinit var rows: UserBadgeRepository
    private val sam = RestUserEntity(id = 1, username = "sam")

    @Test
    fun `only catalogue badges can be granted, once, and revoked`() {
        val service = BadgeService(rows)
        assertThrows(IllegalArgumentException::class.java) { service.grant(sam, "<b>king</b>", "root") }
        assertThrows(IllegalArgumentException::class.java) { service.grant(sam, "admin", "root") }
        verify(rows, never()).save(any<UserBadge>())

        `when`(rows.findByUserAndBadge(sam, "verified")).thenReturn(null)
        assertTrue(service.grant(sam, "verified", "root"))
        val saved = argumentCaptor<UserBadge>().also { verify(rows).save(it.capture()) }.firstValue
        assertEquals("verified", saved.badge)
        assertEquals("root", saved.grantedBy)
        assertEquals(sam, saved.user)

        val existing = UserBadge(id = 3, badge = "verified", user = sam)
        `when`(rows.findByUserAndBadge(sam, "verified")).thenReturn(existing)
        assertFalse(service.grant(sam, "verified", "root"))
        assertTrue(service.revoke(sam, "verified"))
        verify(rows).delete(existing)
        `when`(rows.findByUserAndBadge(sam, "helper")).thenReturn(null)
        assertFalse(service.revoke(sam, "helper"))
    }

    @Test
    fun `badges are listed sorted, per user and for many users at once`() {
        val service = BadgeService(rows)
        val eve = RestUserEntity(id = 2, username = "eve")
        `when`(rows.findByUser(sam)).thenReturn(listOf(UserBadge(badge = "verified", user = sam), UserBadge(badge = "contributor", user = sam)))
        assertEquals(listOf("contributor", "verified"), service.of(sam))
        `when`(rows.findByUserIn(listOf(sam, eve))).thenReturn(listOf(UserBadge(badge = "helper", user = eve), UserBadge(badge = "verified", user = sam)))
        assertEquals(mapOf(1L to listOf("verified"), 2L to listOf("helper")), service.ofAll(listOf(sam, eve)))
        assertEquals(emptyMap<Long, List<String>>(), service.ofAll(emptyList()))
        assertEquals("Verified", BadgeCatalog.byKey("verified")!!.label)
        assertTrue(BadgeCatalog.all.map { it.key }.all { it.matches(Regex("[a-z]+")) })
    }
}
