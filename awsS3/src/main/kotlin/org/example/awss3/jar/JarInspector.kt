package org.example.awss3.jar

import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.jar.JarInputStream

/** What [JarInspector] found out about an uploaded archive. */
data class JarInspection(
    /** The dotted names of the classes that satisfy the package contract. */
    val entryClasses: List<String>,
    /** The `Main-Class` manifest attribute, when the archive has one. */
    val mainClass: String?,
    val classCount: Int,
    /** Human readable reasons the archive was rejected; empty when it is valid. */
    val problems: List<String>
) {
    val valid: Boolean get() = problems.isEmpty()
}

/**
 * Checks that an uploaded file is a jar containing at least one Amie package: a concrete class with
 * `run(String): List` and `close()` instance methods, which is what the website and the desktop app
 * call to execute a package and to shut it down. Classes are inspected structurally from bytecode, so
 * nothing from the archive is ever loaded or executed here.
 */
@Component
class JarInspector {
    fun inspect(fileName: String?, bytes: ByteArray): JarInspection {
        val problems = mutableListOf<String>()
        val name = fileName?.trim().orEmpty()

        if (!name.endsWith(JAR_SUFFIX, ignoreCase = true)) {
            problems += "The file must be a .jar archive"
        }
        if (!looksLikeZip(bytes)) {
            problems += "The file is not a valid jar archive"
            return JarInspection(emptyList(), null, 0, problems)
        }

        val entryClasses = mutableListOf<String>()
        var mainClass: String? = null
        var classCount = 0
        try {
            JarInputStream(ByteArrayInputStream(bytes)).use { jar ->
                mainClass = jar.manifest?.mainAttributes?.getValue("Main-Class")
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(CLASS_SUFFIX)) continue
                    if (entry.name.endsWith("module-info.class") || entry.name.endsWith("package-info.class")) continue
                    classCount++
                    val info = runCatching { ClassFileParser.parse(jar.readBytes()) }.getOrNull() ?: continue
                    if (satisfiesContract(info)) entryClasses += info.dottedName
                }
            }
        } catch (e: Exception) {
            problems += "The jar archive could not be read"
            return JarInspection(emptyList(), mainClass, classCount, problems)
        }

        if (classCount == 0) {
            problems += "The jar contains no classes"
        } else if (entryClasses.isEmpty()) {
            problems += "No class in the jar has both run(String): List and close() methods"
        }
        return JarInspection(entryClasses, mainClass, classCount, problems)
    }

    private fun satisfiesContract(info: ClassFileInfo): Boolean {
        if (info.isInterface || info.isAbstract) return false
        val run = info.methods.any { it.name == RUN_METHOD && it.descriptor == RUN_DESCRIPTOR && !it.isStatic && it.isPublic }
        val close = info.methods.any { it.name == CLOSE_METHOD && it.descriptor == CLOSE_DESCRIPTOR && !it.isStatic && it.isPublic }
        return run && close
    }

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) || (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))

    companion object {
        const val JAR_SUFFIX = ".jar"
        const val CLASS_SUFFIX = ".class"
        const val RUN_METHOD = "run"
        const val CLOSE_METHOD = "close"
        /** `List<String> run(String input)` */
        const val RUN_DESCRIPTOR = "(Ljava/lang/String;)Ljava/util/List;"
        /** `void close()` */
        const val CLOSE_DESCRIPTOR = "()V"

        fun isJarName(fileName: String?): Boolean = fileName?.trim()?.endsWith(JAR_SUFFIX, ignoreCase = true) == true
    }
}
