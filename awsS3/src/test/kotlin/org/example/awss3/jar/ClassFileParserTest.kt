package org.example.awss3.jar

import org.example.awss3.jar.fixtures.AbstractPackage
import org.example.awss3.jar.fixtures.GoodPackage
import org.example.awss3.jar.fixtures.HiddenPackage
import org.example.awss3.jar.fixtures.PackageContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassFileParserTest {

    @Test
    fun `reads the class name, super class and the method table`() {
        val info = ClassFileParser.parse(TestJars.classBytes(GoodPackage::class))

        assertEquals("org/example/awss3/jar/fixtures/GoodPackage", info.name)
        assertEquals("org.example.awss3.jar.fixtures.GoodPackage", info.dottedName)
        assertEquals("java/lang/Object", info.superName)
        assertFalse(info.isInterface)
        assertFalse(info.isAbstract)

        val run = info.methods.single { it.name == "run" }
        assertEquals(JarInspector.RUN_DESCRIPTOR, run.descriptor)
        assertTrue(run.isPublic)
        assertFalse(run.isStatic)

        val close = info.methods.single { it.name == "close" }
        assertEquals(JarInspector.CLOSE_DESCRIPTOR, close.descriptor)
    }

    @Test
    fun `flags interfaces and abstract classes`() {
        assertTrue(ClassFileParser.parse(TestJars.classBytes(PackageContract::class)).isInterface)
        assertTrue(ClassFileParser.parse(TestJars.classBytes(AbstractPackage::class)).isAbstract)
    }

    @Test
    fun `private methods are not public`() {
        val info = ClassFileParser.parse(TestJars.classBytes(HiddenPackage::class))
        assertFalse(info.methods.single { it.name == "run" }.isPublic)
    }

    @Test
    fun `rejects bytes that are not a class file`() {
        assertThrows(IllegalArgumentException::class.java) { ClassFileParser.parse("not a class".toByteArray()) }
    }
}
