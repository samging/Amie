package org.example.awss3.jar

import org.example.awss3.jar.fixtures.AbstractPackage
import org.example.awss3.jar.fixtures.GoodPackage
import org.example.awss3.jar.fixtures.HiddenPackage
import org.example.awss3.jar.fixtures.NoClosePackage
import org.example.awss3.jar.fixtures.PackageContract
import org.example.awss3.jar.fixtures.WrongSignaturePackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JarInspectorTest {
    private val inspector = JarInspector()

    @Test
    fun `accepts a jar with a class that has run and close`() {
        val result = inspector.inspect("good.jar", TestJars.jarOf(GoodPackage::class, mainClass = "org.example.Main"))

        assertTrue(result.valid, result.problems.toString())
        assertEquals(listOf("org.example.awss3.jar.fixtures.GoodPackage"), result.entryClasses)
        assertEquals("org.example.Main", result.mainClass)
        assertEquals(1, result.classCount)
    }

    @Test
    fun `only concrete public implementations count as entry classes`() {
        val jar = TestJars.jarOf(
            GoodPackage::class, NoClosePackage::class, WrongSignaturePackage::class,
            HiddenPackage::class, AbstractPackage::class, PackageContract::class
        )

        val result = inspector.inspect("mixed.jar", jar)

        assertTrue(result.valid)
        assertEquals(listOf("org.example.awss3.jar.fixtures.GoodPackage"), result.entryClasses)
        assertEquals(6, result.classCount)
    }

    @Test
    fun `rejects a jar without a matching class`() {
        val result = inspector.inspect("bad.jar", TestJars.jarOf(NoClosePackage::class, AbstractPackage::class))

        assertFalse(result.valid)
        assertEquals(listOf("No class in the jar has both run(String): List and close() methods"), result.problems)
    }

    @Test
    fun `rejects a jar with no classes at all`() {
        val result = inspector.inspect("empty.jar", TestJars.jarOf(extraEntries = mapOf("readme.txt" to "hi".toByteArray())))

        assertFalse(result.valid)
        assertEquals(listOf("The jar contains no classes"), result.problems)
    }

    @Test
    fun `rejects a file that is not named jar`() {
        val result = inspector.inspect("script.py", TestJars.jarOf(GoodPackage::class))

        assertFalse(result.valid)
        assertEquals(listOf("The file must be a .jar archive"), result.problems)
    }

    @Test
    fun `rejects bytes that are not a zip archive`() {
        val result = inspector.inspect("fake.jar", "print('hello')".toByteArray())

        assertFalse(result.valid)
        assertEquals(listOf("The file is not a valid jar archive"), result.problems)
        assertNull(result.mainClass)
    }

    @Test
    fun `rejects a truncated archive`() {
        val truncated = TestJars.jarOf(GoodPackage::class).copyOf(60)

        val result = inspector.inspect("truncated.jar", truncated)

        assertFalse(result.valid)
        assertTrue(result.problems.any { it.contains("could not be read") || it.contains("no classes") }, result.problems.toString())
    }

    @Test
    fun `isJarName ignores case and surrounding whitespace`() {
        assertTrue(JarInspector.isJarName(" Amie.JAR "))
        assertFalse(JarInspector.isJarName("amie.zip"))
        assertFalse(JarInspector.isJarName(null))
    }
}
