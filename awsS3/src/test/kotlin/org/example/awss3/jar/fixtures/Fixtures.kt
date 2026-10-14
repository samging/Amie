package org.example.awss3.jar.fixtures

/** Satisfies the package contract: public instance `run(String): List` and `close()`. */
class GoodPackage {
    fun run(input: String): List<String> = input.split(' ')
    fun close() {}
}

/** Has `run` but no `close`. */
class NoClosePackage {
    fun run(input: String): List<String> = listOf(input)
}

/** Right names, wrong `run` signature. */
class WrongSignaturePackage {
    fun run(input: String): String = input
    fun close() {}
}

/** Right methods, but not callable from outside. */
class HiddenPackage {
    private fun run(input: String): List<String> = listOf(input)
    private fun close() {}
    fun touch() = run("x").size + close().hashCode()
}

abstract class AbstractPackage {
    abstract fun run(input: String): List<String>
    abstract fun close()
}

interface PackageContract {
    fun run(input: String): List<String>
    fun close()
}
