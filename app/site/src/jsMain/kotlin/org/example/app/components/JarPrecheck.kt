package org.example.app.components

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.File
import kotlin.js.Promise

/** What the browser could tell about a file before uploading it. */
class JarPrecheckResult(val problems: List<String>, val entries: Int, val classCount: Int, val hasManifest: Boolean) {
    val ok: Boolean get() = problems.isEmpty()
}

/**
 * Checks in the browser that a chosen file is a jar before it is uploaded: the name, the zip signature
 * and the central directory, which must list at least one class. The server then inspects the classes
 * for the `run(text)` / `close()` package contract.
 */
object JarPrecheck {
    private const val MAX_ENTRIES = 50_000

    suspend fun check(file: File): JarPrecheckResult {
        val problems = mutableListOf<String>()
        if (!file.name.endsWith(".jar", ignoreCase = true)) problems += "Choose a .jar file."
        if (file.size.toDouble() <= 0) {
            problems += "The file is empty."
            return JarPrecheckResult(problems, 0, 0, false)
        }
        val buffer = (file.asDynamic().arrayBuffer() as Promise<ArrayBuffer>).await()
        val bytes = Uint8Array(buffer)
        if (bytes.length < 22 || bytes[0].toInt() != 0x50 || bytes[1].toInt() != 0x4B) {
            problems += "This is not a jar archive."
            return JarPrecheckResult(problems, 0, 0, false)
        }
        val names = entryNames(bytes)
        if (names == null) {
            problems += "The jar archive is damaged or incomplete."
            return JarPrecheckResult(problems, 0, 0, false)
        }
        val classCount = names.count { it.endsWith(".class") }
        val hasManifest = names.any { it.equals("META-INF/MANIFEST.MF", ignoreCase = true) }
        if (classCount == 0) problems += "The jar contains no compiled classes."
        return JarPrecheckResult(problems, names.size, classCount, hasManifest)
    }

    /** The names in the zip central directory, or null when there is no readable directory. */
    private fun entryNames(bytes: Uint8Array): List<String>? {
        val length = bytes.length
        var eocd = -1
        var position = length - 22
        val stop = maxOf(0, length - 22 - 65_535)
        while (position >= stop) {
            if (bytes[position].toInt() == 0x50 && bytes[position + 1].toInt() == 0x4B &&
                bytes[position + 2].toInt() == 0x05 && bytes[position + 3].toInt() == 0x06
            ) {
                eocd = position
                break
            }
            position--
        }
        if (eocd < 0) return null
        val count = u16(bytes, eocd + 10)
        var offset = u32(bytes, eocd + 16)
        if (offset < 0 || offset >= length) return null
        val decoder = js("new TextDecoder()")
        val names = ArrayList<String>(count)
        var read = 0
        while (read < count && read < MAX_ENTRIES && offset + 46 <= length) {
            if (bytes[offset].toInt() != 0x50 || bytes[offset + 1].toInt() != 0x4B ||
                bytes[offset + 2].toInt() != 0x01 || bytes[offset + 3].toInt() != 0x02
            ) return null
            val nameLength = u16(bytes, offset + 28)
            val extraLength = u16(bytes, offset + 30)
            val commentLength = u16(bytes, offset + 32)
            if (offset + 46 + nameLength > length) return null
            val nameBytes = bytes.subarray(offset + 46, offset + 46 + nameLength)
            names += decoder.decode(nameBytes) as String
            offset += 46 + nameLength + extraLength + commentLength
            read++
        }
        return names
    }

    private fun u16(bytes: Uint8Array, at: Int): Int = bytes[at].toInt() and 0xFF or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: Uint8Array, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)
}
