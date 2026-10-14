package org.example.amiepackagerepository.shared.packages

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** The parts of a JVM class file that matter for checking a package contract. */
data class ClassFileInfo(
    /** Internal name, e.g. `org/example/MyPackage`. */
    val name: String,
    val superName: String?,
    val interfaces: List<String>,
    val accessFlags: Int,
    val methods: List<MethodInfo>
) {
    val isInterface: Boolean get() = accessFlags and ACC_INTERFACE != 0
    val isAbstract: Boolean get() = accessFlags and ACC_ABSTRACT != 0

    /** Dotted class name, e.g. `org.example.MyPackage`. */
    val dottedName: String get() = name.replace('/', '.')

    companion object {
        const val ACC_INTERFACE = 0x0200
        const val ACC_ABSTRACT = 0x0400
    }
}

data class MethodInfo(val name: String, val descriptor: String, val accessFlags: Int) {
    val isStatic: Boolean get() = accessFlags and ACC_STATIC != 0
    val isPublic: Boolean get() = accessFlags and ACC_PUBLIC != 0

    companion object {
        const val ACC_PUBLIC = 0x0001
        const val ACC_STATIC = 0x0008
    }
}

/**
 * A minimal, dependency-free reader of the JVM class file format (JVMS §4). It reads the constant pool,
 * the class header and the method table, and skips everything else, which is all that is needed to tell
 * whether a class exposes a given set of methods.
 */
object ClassFileParser {
    private const val MAGIC = 0xCAFEBABE.toInt()

    fun parse(bytes: ByteArray): ClassFileInfo {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Not a class file" }
        input.readUnsignedShort() // minor version
        input.readUnsignedShort() // major version

        val constantPoolCount = input.readUnsignedShort()
        val utf8 = HashMap<Int, String>()
        val classNameIndex = HashMap<Int, Int>()
        var index = 1
        while (index < constantPoolCount) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8[index] = input.readUTF()
                3, 4 -> input.skipNBytes(4)
                5, 6 -> {
                    input.skipNBytes(8)
                    index++ // long and double take two constant pool slots
                }
                7 -> classNameIndex[index] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.skipNBytes(2)
                9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
                15 -> input.skipNBytes(3)
                else -> throw IllegalArgumentException("Unknown constant pool tag $tag")
            }
            index++
        }

        fun className(poolIndex: Int): String? = classNameIndex[poolIndex]?.let { utf8[it] }

        val accessFlags = input.readUnsignedShort()
        val thisClass = input.readUnsignedShort()
        val superClass = input.readUnsignedShort()
        val interfaces = List(input.readUnsignedShort()) { className(input.readUnsignedShort()) ?: "" }

        repeat(input.readUnsignedShort()) { // fields
            input.skipNBytes(6)
            skipAttributes(input)
        }

        val methods = List(input.readUnsignedShort()) {
            val methodAccess = input.readUnsignedShort()
            val name = utf8[input.readUnsignedShort()] ?: ""
            val descriptor = utf8[input.readUnsignedShort()] ?: ""
            skipAttributes(input)
            MethodInfo(name, descriptor, methodAccess)
        }

        return ClassFileInfo(
            name = className(thisClass) ?: "",
            superName = className(superClass),
            interfaces = interfaces,
            accessFlags = accessFlags,
            methods = methods
        )
    }

    private fun skipAttributes(input: DataInputStream) {
        repeat(input.readUnsignedShort()) {
            input.skipNBytes(2) // attribute name index
            input.skipNBytes(input.readInt().toLong())
        }
    }
}
