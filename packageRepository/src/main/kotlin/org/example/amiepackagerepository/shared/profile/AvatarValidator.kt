package org.example.amiepackagerepository.shared.profile

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** A profile picture that passed [AvatarValidator]: its bytes and the type they really are. */
class Avatar(val bytes: ByteArray, val contentType: String)

/**
 * Accepts only what a profile picture may be, and decides the type from the bytes, never from the
 * file name or the `Content-Type` the browser sent:
 *
 * - PNG, JPEG, GIF or WebP, recognised by their signature; anything else is refused, in particular SVG,
 *   which can carry scripts, and HTML renamed to `.png`;
 * - at most [MAX_BYTES];
 * - at most [MAX_SIDE] pixels on either side when the format can be measured without decoding it
 *   (a 20,000 × 20,000 PNG is a few kilobytes on disk and gigabytes in memory).
 *
 * The picture is served back with the detected type and `X-Content-Type-Options: nosniff`, so a browser
 * never guesses a different type for it.
 */
object AvatarValidator {
    const val MAX_BYTES = 512 * 1024
    const val MAX_SIDE = 2048
    val ALLOWED_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

    const val BACKGROUND_MAX_BYTES = 2 * 1024 * 1024
    const val BACKGROUND_MAX_SIDE = 4096

    fun validate(bytes: ByteArray, maxBytes: Int = MAX_BYTES, maxSide: Int = MAX_SIDE): Avatar {
        require(bytes.isNotEmpty()) { "The picture is empty" }
        require(bytes.size <= maxBytes) { "A picture may be at most ${maxBytes / 1024} KB" }
        val type = detect(bytes) ?: throw IllegalArgumentException("Only PNG, JPEG, GIF and WebP pictures are accepted")
        measure(bytes)?.let { (w, h) ->
            require(w in 1..maxSide && h in 1..maxSide) { "A picture may be at most $maxSide × $maxSide pixels" }
        }
        return Avatar(bytes, type)
    }

    /** The image type by its signature, or null. */
    fun detect(b: ByteArray): String? {
        fun at(i: Int) = b.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            at(0) == 0x89 && at(1) == 'P'.code && at(2) == 'N'.code && at(3) == 'G'.code && at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A && at(7) == 0x0A -> "image/png"
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == '8'.code && (at(4) == '7'.code || at(4) == '9'.code) && at(5) == 'a'.code -> "image/gif"
            at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code && at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> "image/webp"
            else -> null
        }
    }

    /** Width and height from the header, without decoding the pixels; null when no reader knows the format (WebP). */
    private fun measure(bytes: ByteArray): Pair<Int, Int>? = try {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = input
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("The picture could not be read")
    }
}
