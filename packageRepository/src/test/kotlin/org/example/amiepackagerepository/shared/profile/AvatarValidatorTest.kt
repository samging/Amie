package org.example.amiepackagerepository.shared.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** What may become a profile picture: type by signature, size, dimensions. */
class AvatarValidatorTest {
    private fun image(width: Int, height: Int, format: String): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, format, out)
        return out.toByteArray()
    }

    @Test
    fun `png, jpeg and gif are recognised by their bytes and measured`() {
        val png = AvatarValidator.validate(image(32, 24, "png"))
        assertEquals("image/png", png.contentType)
        assertEquals("image/jpeg", AvatarValidator.validate(image(16, 16, "jpg")).contentType)
        assertEquals("image/gif", AvatarValidator.validate(image(8, 8, "gif")).contentType)
        // WebP is accepted by its RIFF signature; ImageIO cannot measure it, so size is the only limit
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray() + ByteArray(16)
        assertEquals("image/webp", AvatarValidator.validate(webp).contentType)
    }

    @Test
    fun `the type comes from the bytes, never from a name or header`() {
        assertNull(AvatarValidator.detect("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".toByteArray()))
        assertNull(AvatarValidator.detect("<!doctype html><html>".toByteArray()))
        assertNull(AvatarValidator.detect("GIF8".toByteArray()))
        assertNull(AvatarValidator.detect(ByteArray(0)))
        val svg = assertThrows(IllegalArgumentException::class.java) { AvatarValidator.validate("<svg/>".toByteArray()) }
        assertTrue(svg.message!!.startsWith("Only PNG, JPEG, GIF and WebP"))
    }

    @Test
    fun `empty, oversized and huge pictures are refused`() {
        assertThrows(IllegalArgumentException::class.java) { AvatarValidator.validate(ByteArray(0)) }
        val big = ByteArray(AvatarValidator.MAX_BYTES + 1).also { System.arraycopy(image(4, 4, "png"), 0, it, 0, 8) }
        assertEquals("A picture may be at most 512 KB", assertThrows(IllegalArgumentException::class.java) { AvatarValidator.validate(big) }.message)
        // A PNG header claiming 20000 x 20000 pixels: a few bytes on disk, gigabytes once decoded
        val bomb = pngHeader(20_000, 20_000)
        assertEquals("A picture may be at most 2048 × 2048 pixels", assertThrows(IllegalArgumentException::class.java) { AvatarValidator.validate(bomb) }.message)
        // A PNG signature with garbage behind it is refused, not decoded into an error page
        assertThrows(IllegalArgumentException::class.java) { AvatarValidator.validate(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)) }
    }

    private fun pngHeader(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArrayOutputStream()
        ihdr.write("IHDR".toByteArray())
        for (v in listOf(width, height)) for (shift in listOf(24, 16, 8, 0)) ihdr.write((v ushr shift) and 0xFF)
        ihdr.write(byteArrayOf(8, 2, 0, 0, 0))
        out.write(byteArrayOf(0, 0, 0, 13))
        out.write(ihdr.toByteArray())
        val crc = java.util.zip.CRC32().also { it.update(ihdr.toByteArray()) }.value.toInt()
        for (shift in listOf(24, 16, 8, 0)) out.write((crc ushr shift) and 0xFF)
        return out.toByteArray()
    }
}
