package org.example.amiepackagerepository.shared.packages

/**
 * The contract an uploaded package jar has to satisfy. A jar is accepted by `POST /upload` when at least
 * one of its classes is a concrete, public class with these two instance methods; implementing this
 * interface is the easiest way to get there, but any class with the same signatures qualifies, so a
 * package does not need to depend on Amie to be valid.
 *
 * ```kotlin
 * class EchoPackage : AmiePackage {
 *     override fun run(input: String): List<String> = input.split(" ")
 *     override fun close() { /* release what run() opened */ }
 * }
 * ```
 */
interface AmiePackage : AutoCloseable {
    /** Handles one text input and returns the lines the caller should show. */
    fun run(input: String): List<String>

    /** Called once when the host is done with the package; it should release what [run] opened. */
    override fun close()
}
