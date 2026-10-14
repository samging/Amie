package org.example.awss3.jar

import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.reflect.KClass

/** Builds small jars in memory out of classes compiled for the tests. */
object TestJars {
    fun classBytes(type: KClass<*>): ByteArray {
        val resource = type.java.simpleName + ".class"
        return type.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("No class file for ${type.java.name}")
    }

    fun jarOf(vararg types: KClass<*>, mainClass: String? = null, extraEntries: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            if (mainClass != null) mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
        }
        JarOutputStream(out, manifest).use { jar ->
            for (type in types) {
                jar.putNextEntry(JarEntry(type.java.name.replace('.', '/') + ".class"))
                jar.write(classBytes(type))
                jar.closeEntry()
            }
            for ((name, bytes) in extraEntries) {
                jar.putNextEntry(JarEntry(name))
                jar.write(bytes)
                jar.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
