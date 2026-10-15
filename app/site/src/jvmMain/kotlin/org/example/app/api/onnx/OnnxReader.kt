package org.example.app.api.onnx

import kotlinx.serialization.Serializable

/** What the site shows of an ONNX file: the model's metadata, its graph as inputs, nodes, initializers and outputs, and a few totals. */
@Serializable
data class OnnxSummary(
    val irVersion: Long,
    val producer: String,
    val producerVersion: String,
    val domain: String,
    val modelVersion: Long,
    val doc: String,
    val opsets: List<Opset>,
    val metadata: Map<String, String>,
    val graphName: String,
    val inputs: List<ValueInfo>,
    val outputs: List<ValueInfo>,
    val initializers: List<Initializer>,
    val nodes: List<Node>,
    val valueInfo: List<ValueInfo>,
    val opCounts: Map<String, Int>,
    val parameters: Long,
    val fileSize: Long,
    /** Attention, feed-forward and normalization blocks found in the graph (see [OnnxPatterns]) */
    val patterns: List<Pattern> = emptyList(),
)

@Serializable data class Opset(val domain: String, val version: Long)
@Serializable data class ValueInfo(val name: String, val type: String, val shape: List<String>, val doc: String = "")
@Serializable data class Initializer(val name: String, val type: String, val dims: List<Long>, val params: Long, val bytes: Long)
@Serializable data class Attribute(val name: String, val type: String, val value: String)
@Serializable data class Node(
    val index: Int,
    val name: String,
    val opType: String,
    val domain: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val attributes: List<Attribute>,
    val doc: String = "",
)

class OnnxFormatException(message: String) : RuntimeException(message)

/**
 * Reads an ONNX file's structure without the protobuf library: ONNX is protocol buffers, and the handful of
 * messages the page shows (ModelProto, GraphProto, NodeProto, ValueInfoProto, TensorProto, AttributeProto)
 * are walked field by field over the wire format. Tensor contents are never copied, only measured, so a
 * large model costs its file size in memory and nothing more.
 */
object OnnxReader {
    const val MAX_BYTES = 256L * 1024 * 1024

    fun summarize(bytes: ByteArray): OnnxSummary {
        if (bytes.size > MAX_BYTES) throw OnnxFormatException("the file is larger than ${MAX_BYTES / (1024 * 1024)} MB")
        if (bytes.isEmpty()) throw OnnxFormatException("the file is empty")
        val model = try {
            readModel(Reader(bytes, 0, bytes.size))
        } catch (e: OnnxFormatException) {
            throw e
        } catch (e: Exception) {
            throw OnnxFormatException("not an ONNX file: ${e.message ?: e.javaClass.simpleName}")
        }
        if (model.graph == null && model.irVersion == 0L) throw OnnxFormatException("not an ONNX file: no graph and no IR version")
        val graph = model.graph ?: ReadGraph()
        val opCounts = graph.nodes.groupingBy { it.opType }.eachCount().toList().sortedByDescending { it.second }.toMap()
        val parameters = graph.initializers.sumOf { it.params }
        return OnnxSummary(
            irVersion = model.irVersion,
            producer = model.producer,
            producerVersion = model.producerVersion,
            domain = model.domain,
            modelVersion = model.modelVersion,
            doc = model.doc,
            opsets = model.opsets,
            metadata = model.metadata,
            graphName = graph.name,
            inputs = graph.inputs.filter { input -> graph.initializers.none { it.name == input.name } },
            outputs = graph.outputs,
            initializers = graph.initializers,
            nodes = graph.nodes,
            valueInfo = graph.valueInfo,
            opCounts = opCounts,
            parameters = parameters,
            fileSize = bytes.size.toLong(),
            patterns = OnnxPatterns.detect(graph.nodes, graph.initializers.map { it.name }.toSet(), (graph.valueInfo + graph.inputs + graph.outputs).associate { it.name to it.shape }),
        )
    }

    // ---- the messages ----------------------------------------------------------------------------

    private class ReadModel {
        var irVersion = 0L; var producer = ""; var producerVersion = ""; var domain = ""; var modelVersion = 0L; var doc = ""
        val opsets = mutableListOf<Opset>(); val metadata = linkedMapOf<String, String>(); var graph: ReadGraph? = null
    }

    private class ReadGraph {
        var name = ""
        val nodes = mutableListOf<Node>(); val initializers = mutableListOf<Initializer>()
        val inputs = mutableListOf<ValueInfo>(); val outputs = mutableListOf<ValueInfo>(); val valueInfo = mutableListOf<ValueInfo>()
    }

    private fun readModel(r: Reader): ReadModel {
        val m = ReadModel()
        r.fields { field, wire ->
            when (field) {
                1 -> m.irVersion = r.varint()
                2 -> m.producer = r.string()
                3 -> m.producerVersion = r.string()
                4 -> m.domain = r.string()
                5 -> m.modelVersion = r.varint()
                6 -> m.doc = r.string()
                7 -> m.graph = readGraph(r.sub(), depth = 0)
                8 -> m.opsets += readOpset(r.sub())
                14 -> readPair(r.sub())?.let { (k, v) -> m.metadata[k] = v }
                else -> r.skip(wire)
            }
        }
        return m
    }

    private fun readOpset(r: Reader): Opset {
        var domain = ""; var version = 0L
        r.fields { field, wire -> when (field) { 1 -> domain = r.string(); 2 -> version = r.varint(); else -> r.skip(wire) } }
        return Opset(domain.ifEmpty { "ai.onnx" }, version)
    }

    private fun readPair(r: Reader): Pair<String, String>? {
        var k: String? = null; var v = ""
        r.fields { field, wire -> when (field) { 1 -> k = r.string(); 2 -> v = r.string(); else -> r.skip(wire) } }
        return k?.let { it to v }
    }

    private fun readGraph(r: Reader, depth: Int): ReadGraph {
        val g = ReadGraph()
        var index = 0
        r.fields { field, wire ->
            when (field) {
                1 -> g.nodes += readNode(r.sub(), index++, depth)
                2 -> g.name = r.string()
                5 -> g.initializers += readTensor(r.sub())
                11 -> g.inputs += readValueInfo(r.sub())
                12 -> g.outputs += readValueInfo(r.sub())
                13 -> g.valueInfo += readValueInfo(r.sub())
                else -> r.skip(wire)
            }
        }
        return g
    }

    private fun readNode(r: Reader, index: Int, depth: Int): Node {
        val inputs = mutableListOf<String>(); val outputs = mutableListOf<String>(); val attributes = mutableListOf<Attribute>()
        var name = ""; var opType = ""; var domain = ""; var doc = ""
        r.fields { field, wire ->
            when (field) {
                1 -> inputs += r.string()
                2 -> outputs += r.string()
                3 -> name = r.string()
                4 -> opType = r.string()
                5 -> attributes += readAttribute(r.sub(), depth)
                6 -> doc = r.string()
                7 -> domain = r.string()
                else -> r.skip(wire)
            }
        }
        return Node(index, name, opType, domain, inputs, outputs, attributes, doc)
    }

    private fun readAttribute(r: Reader, depth: Int): Attribute {
        var name = ""; var type = 0L
        var f: Float? = null; var i: Long? = null; var s: String? = null; var tensor: Initializer? = null; var graphNodes: Int? = null
        val floats = mutableListOf<Float>(); val ints = mutableListOf<Long>(); val strings = mutableListOf<String>()
        var tensors = 0; var graphs = 0
        r.fields { field, wire ->
            when (field) {
                1 -> name = r.string()
                2 -> f = Float.fromBits(r.fixed32())
                3 -> i = r.varint()
                4 -> s = r.string()
                5 -> tensor = readTensor(r.sub())
                6 -> graphNodes = if (depth < 4) readGraph(r.sub(), depth + 1).nodes.size else { r.skip(2); -1 }
                7 -> if (wire == 2) floats += r.packedFixed32().map { Float.fromBits(it) } else floats += Float.fromBits(r.fixed32())
                8 -> if (wire == 2) ints += r.packedVarints() else ints += r.varint()
                9 -> strings += r.string()
                10 -> { r.skip(wire); tensors++ }
                11 -> { r.skip(wire); graphs++ }
                20 -> type = r.varint()
                else -> r.skip(wire)
            }
        }
        val typeName = when (type) { 1L -> "float"; 2L -> "int"; 3L -> "string"; 4L -> "tensor"; 5L -> "graph"; 6L -> "floats"; 7L -> "ints"; 8L -> "strings"; 9L -> "tensors"; 10L -> "graphs"; 11L -> "sparse tensor"; 13L -> "type"; else -> when {
            f != null -> "float"; i != null -> "int"; s != null -> "string"; tensor != null -> "tensor"; graphNodes != null -> "graph"
            floats.isNotEmpty() -> "floats"; ints.isNotEmpty() -> "ints"; strings.isNotEmpty() -> "strings"; else -> "?" } }
        val value = when (typeName) {
            "float" -> f?.toString() ?: ""
            "int" -> i?.toString() ?: ""
            "string" -> s?.let { short(it) } ?: ""
            "tensor" -> tensor?.let { "${it.type}${it.dims}" } ?: ""
            "graph" -> graphNodes?.let { if (it < 0) "a nested graph" else "a graph of $it nodes" } ?: ""
            "floats" -> list(floats.map { it.toString() })
            "ints" -> list(ints.map { it.toString() })
            "strings" -> list(strings.map { short(it) })
            "tensors" -> "$tensors tensors"
            "graphs" -> "$graphs graphs"
            else -> ""
        }
        return Attribute(name, typeName, value)
    }

    private fun readTensor(r: Reader): Initializer {
        val dims = mutableListOf<Long>(); var dataType = 0L; var name = ""; var bytes = 0L
        r.fields { field, wire ->
            when (field) {
                1 -> if (wire == 2) dims += r.packedVarints() else dims += r.varint()
                2 -> dataType = r.varint()
                8 -> name = r.string()
                // the tensor's content, in whichever field it was stored: measured, never read
                4, 5, 6, 7, 9, 10, 11, 12, 16 -> bytes += r.measure(wire)
                else -> r.skip(wire)
            }
        }
        val params = if (dims.isEmpty()) 1L else dims.fold(1L) { acc, d -> if (d <= 0) acc else acc * d }
        return Initializer(name, typeName(dataType), dims, params, bytes)
    }

    private fun readValueInfo(r: Reader): ValueInfo {
        var name = ""; var doc = ""; var type = "?"; var shape: List<String> = emptyList()
        r.fields { field, wire ->
            when (field) {
                1 -> name = r.string()
                2 -> readType(r.sub()).let { (t, s) -> type = t; shape = s }
                3 -> doc = r.string()
                else -> r.skip(wire)
            }
        }
        return ValueInfo(name, type, shape, doc)
    }

    /** TypeProto: tensor_type(1) {elem_type(1), shape(2)}, sequence_type(4), map_type(5), optional_type(9), sparse_tensor_type(8) */
    private fun readType(r: Reader): Pair<String, List<String>> {
        var type = "?"; var shape: List<String> = emptyList()
        r.fields { field, wire ->
            when (field) {
                1, 8 -> {
                    val t = r.sub()
                    t.fields { f, w -> when (f) { 1 -> type = typeName(t.varint()); 2 -> shape = readShape(t.sub()); else -> t.skip(w) } }
                    if (field == 8) type = "sparse $type"
                }
                4 -> { val inner = readType(r.sub()); type = "sequence of ${inner.first}"; shape = inner.second }
                5 -> { r.skip(wire); type = "map" }
                9 -> { val inner = readType(r.sub()); type = "optional ${inner.first}"; shape = inner.second }
                else -> r.skip(wire)
            }
        }
        return type to shape
    }

    private fun readShape(r: Reader): List<String> {
        val dims = mutableListOf<String>()
        r.fields { field, wire ->
            if (field == 1) {
                val d = r.sub(); var text = "?"
                d.fields { f, w -> when (f) { 1 -> text = d.varint().toString(); 2 -> text = d.string(); else -> d.skip(w) } }
                dims += text
            } else r.skip(wire)
        }
        return dims
    }

    private fun typeName(code: Long): String = when (code) {
        1L -> "float32"; 2L -> "uint8"; 3L -> "int8"; 4L -> "uint16"; 5L -> "int16"; 6L -> "int32"; 7L -> "int64"; 8L -> "string"
        9L -> "bool"; 10L -> "float16"; 11L -> "float64"; 12L -> "uint32"; 13L -> "uint64"; 14L -> "complex64"; 15L -> "complex128"
        16L -> "bfloat16"; 17L -> "float8e4m3fn"; 18L -> "float8e4m3fnuz"; 19L -> "float8e5m2"; 20L -> "float8e5m2fnuz"; 21L -> "uint4"; 22L -> "int4"
        else -> "type $code"
    }

    private fun short(s: String) = s.replace('\n', ' ').let { if (it.length > 80) it.take(77) + "…" else it }
    private fun list(items: List<String>) = if (items.size <= 16) items.joinToString(", ", "[", "]") else items.take(16).joinToString(", ", "[", ", … ${items.size} values]")

    // ---- the wire format --------------------------------------------------------------------------

    private class Reader(val bytes: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end

        inline fun fields(block: (field: Int, wire: Int) -> Unit) {
            while (pos < end) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val wire = (key and 7).toInt()
                if (field == 0) throw OnnxFormatException("not an ONNX file: a field number of zero")
                block(field, wire)
            }
        }

        fun varint(): Long {
            var shift = 0; var result = 0L
            while (true) {
                if (pos >= end) throw OnnxFormatException("not an ONNX file: it ends inside a number")
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) throw OnnxFormatException("not an ONNX file: a number is too long")
            }
        }

        fun fixed32(): Int {
            need(4)
            val v = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> { need(8); pos += 8 }
                2 -> { val len = length(); pos += len }
                5 -> { need(4); pos += 4 }
                else -> throw OnnxFormatException("not an ONNX file: unknown wire type $wire")
            }
        }

        /** The size of a field's content, skipping it */
        fun measure(wire: Int): Long = when (wire) {
            2 -> { val len = length(); pos += len; len.toLong() }
            1 -> { need(8); pos += 8; 8L }
            5 -> { need(4); pos += 4; 4L }
            else -> { varint(); 0L }
        }

        fun sub(): Reader {
            val len = length()
            val s = Reader(bytes, pos, pos + len)
            pos += len
            return s
        }

        fun string(): String {
            val len = length()
            val s = String(bytes, pos, len, Charsets.UTF_8)
            pos += len
            return s
        }

        fun packedVarints(): List<Long> { val s = sub(); val out = mutableListOf<Long>(); while (s.hasMore()) out += s.varint(); return out }
        fun packedFixed32(): List<Int> { val s = sub(); val out = mutableListOf<Int>(); while (s.hasMore()) out += s.fixed32(); return out }

        private fun length(): Int {
            val len = varint()
            if (len < 0 || pos + len > end) throw OnnxFormatException("not an ONNX file: a length runs past the end")
            return len.toInt()
        }

        private fun need(n: Int) { if (pos + n > end) throw OnnxFormatException("not an ONNX file: it ends early") }
    }
}
