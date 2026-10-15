package org.example.app.api.onnx

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A tiny protobuf writer, enough to build ONNX messages the reader must understand */
private class Pb {
    val out = ByteArrayOutputStream()
    fun varint(field: Int, value: Long) = apply { key(field, 0); writeVarint(value) }
    fun string(field: Int, value: String) = apply { bytes(field, value.toByteArray()) }
    fun bytes(field: Int, value: ByteArray) = apply { key(field, 2); writeVarint(value.size.toLong()); out.write(value) }
    fun message(field: Int, inner: Pb) = bytes(field, inner.out.toByteArray())
    fun fixed32(field: Int, value: Int) = apply { key(field, 5); for (i in 0 until 4) out.write((value ushr (8 * i)) and 0xFF) }
    fun packed(field: Int, values: List<Long>) = apply { val p = Pb(); values.forEach { p.writeVarint(it) }; bytes(field, p.out.toByteArray()) }
    private fun key(field: Int, wire: Int) = writeVarint(((field shl 3) or wire).toLong())
    fun writeVarint(v: Long) { var x = v; while (true) { if (x and 0x7F.inv().toLong() == 0L) { out.write(x.toInt()); return }; out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 } }
    fun bytes() = out.toByteArray()
}

private fun tensorType(elem: Long, vararg dims: Any): Pb {
    val shape = Pb()
    dims.forEach { d -> shape.message(1, Pb().apply { if (d is Long) varint(1, d) else string(2, d.toString()) }) }
    return Pb().message(1, Pb().varint(1, elem).message(2, shape))
}

private fun valueInfo(name: String, elem: Long, vararg dims: Any) = Pb().string(1, name).message(2, tensorType(elem, *dims))

class OnnxReaderTest {
    /** input [batch, 4] → MatMul with a 4×2 weight → Add bias → Softmax(axis = 1) → output [batch, 2] */
    private fun model(): ByteArray {
        val weight = Pb().packed(1, listOf(4, 2)).varint(2, 1).string(8, "W").bytes(9, ByteArray(4 * 2 * 4))
        val bias = Pb().packed(1, listOf(2)).varint(2, 1).string(8, "b").bytes(9, ByteArray(2 * 4))
        val graph = Pb()
            .string(2, "tiny")
            .message(1, Pb().string(1, "x").string(1, "W").string(2, "h").string(3, "matmul").string(4, "MatMul"))
            .message(1, Pb().string(1, "h").string(1, "b").string(2, "z").string(3, "add").string(4, "Add"))
            .message(1, Pb().string(1, "z").string(2, "y").string(3, "soft").string(4, "Softmax")
                .message(5, Pb().string(1, "axis").varint(3, 1).varint(20, 2))
                .message(5, Pb().string(1, "note").string(4, "hello").varint(20, 3))
                .message(5, Pb().string(1, "scale").fixed32(2, 1.5f.toBits()).varint(20, 1))
                .message(5, Pb().string(1, "sizes").packed(8, listOf(1, 2, 3)).varint(20, 7)))
            .message(5, weight).message(5, bias)
            .message(11, valueInfo("x", 1, "batch", 4L)).message(11, valueInfo("W", 1, 4L, 2L))
            .message(12, valueInfo("y", 1, "batch", 2L))
            .message(13, valueInfo("h", 1, "batch", 2L))
        return Pb()
            .varint(1, 8).string(2, "test-producer").string(3, "0.1").string(4, "org.example").varint(5, 3).string(6, "a tiny model")
            .message(7, graph)
            .message(8, Pb().string(1, "").varint(2, 17))
            .message(14, Pb().string(1, "author").string(2, "sam"))
            .bytes()
    }

    @Test
    fun `reads metadata, graph, weights and totals`() {
        val s = OnnxReader.summarize(model())
        assertEquals(8, s.irVersion)
        assertEquals("test-producer", s.producer)
        assertEquals("0.1", s.producerVersion)
        assertEquals("org.example", s.domain)
        assertEquals(3, s.modelVersion)
        assertEquals("a tiny model", s.doc)
        assertEquals(listOf(Opset("ai.onnx", 17)), s.opsets)
        assertEquals(mapOf("author" to "sam"), s.metadata)
        assertEquals("tiny", s.graphName)
        // W is an initializer, so it is not a real input
        assertEquals(listOf(ValueInfo("x", "float32", listOf("batch", "4"))), s.inputs)
        assertEquals(listOf(ValueInfo("y", "float32", listOf("batch", "2"))), s.outputs)
        assertEquals(listOf("MatMul", "Add", "Softmax"), s.nodes.map { it.opType })
        assertEquals(listOf("x", "W"), s.nodes[0].inputs)
        assertEquals(listOf("y"), s.nodes[2].outputs)
        assertEquals(mapOf("MatMul" to 1, "Add" to 1, "Softmax" to 1), s.opCounts)
        assertEquals(listOf(Initializer("W", "float32", listOf(4, 2), 8, 32), Initializer("b", "float32", listOf(2), 2, 8)), s.initializers)
        assertEquals(10, s.parameters)
        assertEquals(listOf(ValueInfo("h", "float32", listOf("batch", "2"))), s.valueInfo)
    }

    @Test
    fun `reads every attribute kind`() {
        val attributes = OnnxReader.summarize(model()).nodes[2].attributes.associateBy { it.name }
        assertEquals(Attribute("axis", "int", "1"), attributes["axis"])
        assertEquals(Attribute("note", "string", "hello"), attributes["note"])
        assertEquals(Attribute("scale", "float", "1.5"), attributes["scale"])
        assertEquals(Attribute("sizes", "ints", "[1, 2, 3]"), attributes["sizes"])
    }

    @Test
    fun `something that is not ONNX is refused with a reason`() {
        assertTrue(assertFailsWith<OnnxFormatException> { OnnxReader.summarize("{\"not\": \"onnx\"}".toByteArray()) }.message!!.startsWith("not an ONNX file"))
        assertFailsWith<OnnxFormatException> { OnnxReader.summarize(ByteArray(0)) }
        // A truncated model: the lengths run past the end
        assertFailsWith<OnnxFormatException> { OnnxReader.summarize(model().copyOf(40)) }
    }
}
