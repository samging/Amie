package org.example.app.api.onnx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The blocks [OnnxPatterns] finds in graphs built by hand, node by node, the way exporters write them. */
class OnnxPatternsTest {
    /** A graph builder: `n("MatMul", "x", "Wq", out = "q")` appends a node in order. */
    private class G {
        val nodes = mutableListOf<Node>()
        val weights = mutableSetOf<String>()
        val shapes = mutableMapOf<String, List<String>>()
        fun weight(vararg names: String) = apply { weights += names }
        fun shape(name: String, vararg dims: String) = apply { shapes[name] = dims.toList() }
        fun n(op: String, vararg inputs: String, out: String, attrs: List<Attribute> = emptyList()): String {
            nodes += Node(nodes.size, "$op${nodes.size}", op, "", inputs.toList(), listOf(out), attrs)
            return out
        }
        fun detect() = OnnxPatterns.detect(nodes, weights, shapes)
    }

    private fun G.attentionHead(x: String, prefix: String, masked: Boolean = true, scaled: Boolean = true): String {
        weight("${prefix}Wq", "${prefix}Wk", "${prefix}Wv", "${prefix}Wo", "shape4", "scale")
        val q = n("MatMul", x, "${prefix}Wq", out = "${prefix}q")
        val k = n("MatMul", x, "${prefix}Wk", out = "${prefix}k")
        val v = n("MatMul", x, "${prefix}Wv", out = "${prefix}v")
        val qh = n("Transpose", n("Reshape", q, "shape4", out = "${prefix}q4"), out = "${prefix}qt")
        val kh = n("Transpose", n("Reshape", k, "shape4", out = "${prefix}k4"), out = "${prefix}kt")
        val vh = n("Transpose", n("Reshape", v, "shape4", out = "${prefix}v4"), out = "${prefix}vt")
        var scores = n("MatMul", qh, kh, out = "${prefix}scores")
        if (scaled) scores = n("Div", scores, "scale", out = "${prefix}scaled")
        if (masked) scores = n("Add", scores, "mask", out = "${prefix}masked")
        val probs = n("Softmax", scores, out = "${prefix}probs")
        val ctx = n("MatMul", probs, vh, out = "${prefix}ctx")
        val merged = n("Reshape", n("Transpose", ctx, out = "${prefix}ctxT"), "shape3", out = "${prefix}ctx3")
        return n("MatMul", merged, "${prefix}Wo", out = "${prefix}out")
    }

    private fun G.feedForward(x: String, prefix: String): String {
        weight("${prefix}W1", "${prefix}b1", "${prefix}W2", "${prefix}b2")
        val h = n("Add", n("MatMul", x, "${prefix}W1", out = "${prefix}h"), "${prefix}b1", out = "${prefix}hb")
        val a = n("Gelu", h, out = "${prefix}a")
        return n("Add", n("MatMul", a, "${prefix}W2", out = "${prefix}o"), "${prefix}b2", out = "${prefix}ob")
    }

    private fun G.layerNorm(x: String, prefix: String): String {
        weight("${prefix}gamma", "${prefix}beta", "eps", "two")
        val mean = n("ReduceMean", x, out = "${prefix}mean")
        val centred = n("Sub", x, mean, out = "${prefix}centred")
        val variance = n("ReduceMean", n("Pow", centred, "two", out = "${prefix}sq"), out = "${prefix}var")
        val std = n("Sqrt", n("Add", variance, "eps", out = "${prefix}vareps"), out = "${prefix}std")
        val normalized = n("Div", centred, std, out = "${prefix}norm")
        return n("Add", n("Mul", normalized, "${prefix}gamma", out = "${prefix}scaled"), "${prefix}beta", out = "${prefix}ln")
    }

    @Test
    fun `a scaled, masked attention head is found with every role`() {
        val g = G()
        g.attentionHead("x", "a.")
        val found = g.detect()
        assertEquals(listOf(OnnxPatterns.ATTENTION), found.map { it.kind })
        val attention = found.single()
        assertEquals("Attention 1", attention.label)
        assertEquals(listOf("scaled", "masked"), attention.notes)
        val roles = attention.roles.associate { it.role to it.opType }
        assertEquals(mapOf("query" to "MatMul", "key" to "MatMul", "value" to "MatMul", "scores" to "MatMul", "scale" to "Div", "mask" to "Add", "softmax" to "Softmax", "context" to "MatMul", "output" to "MatMul"), roles)
        // Every node of the head, from the projections to the output projection, belongs to the block
        assertEquals(0, attention.first)
        assertEquals(g.nodes.size - 1, attention.last)
        assertEquals(g.nodes.indices.toList(), attention.nodes)
    }

    @Test
    fun `a classifier's final softmax is not attention and a lone projection is not a feed-forward`() {
        val g = G().weight("W", "b")
        val z = g.n("Add", g.n("MatMul", "x", "W", out = "h"), "b", out = "z")
        g.n("Softmax", z, out = "y")
        assertEquals(emptyList(), g.detect())

        val h = G().weight("W")
        h.n("Relu", h.n("MatMul", "x", "W", out = "h"), out = "a")
        assertEquals(emptyList(), h.detect())
    }

    @Test
    fun `a transformer layer yields attention, feed-forward and two normalizations in graph order`() {
        val g = G()
        val ln1 = g.layerNorm("x", "ln1.")
        val attn = g.attentionHead(ln1, "attn.", masked = false)
        val residual = g.n("Add", "x", attn, out = "res1")
        val ln2 = g.layerNorm(residual, "ln2.")
        val ff = g.feedForward(ln2, "ff.")
        g.n("Add", residual, ff, out = "res2")

        val found = g.detect()
        assertEquals(listOf("Normalization 1", "Attention 1", "Normalization 2", "Feed-forward 1"), found.map { it.label })
        assertEquals(listOf("scaled"), found[1].notes)
        assertEquals(listOf("layer norm"), found[0].notes)
        assertEquals(listOf("mean", "variance", "normalize", "scale", "shift"), found[0].roles.map { it.role })
        assertEquals(listOf("gelu"), found[3].notes)
        assertEquals(listOf("up", "activation", "down"), found[3].roles.map { it.role })
        // Blocks do not overlap, and the residual adds belong to none of them
        val all = found.flatMap { it.nodes }
        assertEquals(all.distinct().size, all.size)
        val residualAdds = g.nodes.filter { it.outputs == listOf("res1") || it.outputs == listOf("res2") }.map { it.index }
        assertTrue(residualAdds.none { it in all })
    }

    @Test
    fun `fused onnxruntime operators count too, with their head count`() {
        val g = G().weight("qkv", "bias")
        g.n("SkipLayerNormalization", "x", "skip", "gamma", "beta", out = "ln")
        g.n("Attention", "ln", "qkv", "bias", "mask", out = "ctx", attrs = listOf(Attribute("num_heads", "int", "12")))
        val found = g.detect()
        assertEquals(listOf(OnnxPatterns.NORMALIZATION, OnnxPatterns.ATTENTION), found.map { it.kind })
        assertEquals(listOf("fused", "12 heads", "masked"), found[1].notes)
        assertEquals(listOf("fused", "SkipLayerNormalization"), found[0].notes)
    }

    @Test
    fun `an erf-based gelu and a gated silu feed-forward are recognised`() {
        val g = G().weight("W1", "W2", "sqrt2", "one", "half")
        val h = g.n("MatMul", "x", "W1", out = "h")
        val erf = g.n("Erf", g.n("Div", h, "sqrt2", out = "hs"), out = "erf")
        val gelu = g.n("Mul", g.n("Mul", h, g.n("Add", erf, "one", out = "erf1"), out = "he"), "half", out = "gelu")
        g.n("MatMul", gelu, "W2", out = "o")
        val ff = g.detect().single()
        assertEquals(OnnxPatterns.FEED_FORWARD, ff.kind)
        assertEquals(listOf("gelu (erf)"), ff.notes)
        assertEquals(g.nodes.indices.toList(), ff.nodes)

        val s = G().weight("Wg", "Wu", "Wd")
        val gate = s.n("MatMul", "x", "Wg", out = "gate")
        val up = s.n("MatMul", "x", "Wu", out = "up")
        val act = s.n("Mul", gate, s.n("Sigmoid", gate, out = "sig"), out = "silu")
        s.n("MatMul", s.n("Mul", act, up, out = "gated"), "Wd", out = "down")
        val swiglu = s.detect().single()
        assertEquals(listOf("silu (sigmoid)", "gated"), swiglu.notes)
        assertEquals(setOf("up", "activation", "down", "gate"), swiglu.roles.map { it.role }.toSet())
    }

    @Test
    fun `an rms norm spelled out in small operators is a normalization`() {
        val g = G().weight("two", "eps", "gamma")
        val sq = g.n("Pow", "x", "two", out = "sq")
        val mean = g.n("ReduceMean", sq, out = "mean")
        val r = g.n("Sqrt", g.n("Add", mean, "eps", out = "me"), out = "rms")
        val norm = g.n("Div", "x", r, out = "norm")
        g.n("Mul", norm, "gamma", out = "out")
        val found = g.detect().single()
        assertEquals(OnnxPatterns.NORMALIZATION, found.kind)
        assertEquals(listOf("rms norm"), found.notes)
        assertEquals(listOf("mean", "variance", "normalize", "scale"), found.roles.map { it.role })
    }

    @Test
    fun `the head count is read from the shapes the exporter recorded`() {
        val g = G().weight("Wq", "Wk", "Wv").shape("scores", "batch", "8", "seq", "seq")
        val q = g.n("Transpose", g.n("Reshape", g.n("MatMul", "x", "Wq", out = "q"), "shape4", out = "q4"), out = "qt")
        val k = g.n("Transpose", g.n("Reshape", g.n("MatMul", "x", "Wk", out = "k"), "shape4", out = "k4"), out = "kt")
        val v = g.n("MatMul", "x", "Wv", out = "v")
        val probs = g.n("Softmax", g.n("MatMul", q, k, out = "scores"), out = "probs")
        g.n("MatMul", probs, v, out = "ctx")
        assertEquals(listOf("8 heads"), g.detect().single().notes)
        // Without shapes, or with a symbolic head dimension, no guess is made
        assertEquals(emptyList(), OnnxPatterns.detect(g.nodes, g.weights).single().notes)
        assertEquals(emptyList(), OnnxPatterns.detect(g.nodes, g.weights, mapOf("scores" to listOf("batch", "heads", "seq", "seq"))).single().notes)
    }

    @Test
    fun `the reader fills the summary's patterns`() {
        // x -> MatMul(W) -> Softmax -> MatMul(v): the smallest graph the reader reports an attention block for
        val summary = OnnxReader.summarize(tinyModel(withContext = true))
        assertEquals(listOf("Attention 1"), summary.patterns.map { it.label })
        assertEquals(listOf("scores", "softmax", "context"), summary.patterns.single().roles.map { it.role })
        // Without the second MatMul it is a classifier and nothing is reported
        assertTrue(OnnxReader.summarize(tinyModel(withContext = false)).patterns.isEmpty())
    }

    /** A protobuf writer just big enough for a model with a few nodes (field numbers as in onnx.proto). */
    private class Proto {
        val out = java.io.ByteArrayOutputStream()
        fun varint(field: Int, value: Long) = apply { key(field, 0); writeVarint(value) }
        fun string(field: Int, value: String) = apply { bytes(field, value.toByteArray()) }
        fun bytes(field: Int, value: ByteArray) = apply { key(field, 2); writeVarint(value.size.toLong()); out.write(value) }
        fun message(field: Int, inner: Proto) = bytes(field, inner.out.toByteArray())
        private fun key(field: Int, wire: Int) = writeVarint(((field shl 3) or wire).toLong())
        private fun writeVarint(v: Long) { var x = v; while (true) { if (x and 0x7F.inv().toLong() == 0L) { out.write(x.toInt()); return }; out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 } }
        fun bytes(): ByteArray = out.toByteArray()
    }

    private fun tinyModel(withContext: Boolean): ByteArray {
        val graph = Proto().string(2, "tiny")
            .message(1, Proto().string(1, "x").string(1, "k").string(2, "scores").string(3, "qk").string(4, "MatMul"))
            .message(1, Proto().string(1, "scores").string(2, "probs").string(3, "soft").string(4, "Softmax"))
        if (withContext) graph.message(1, Proto().string(1, "probs").string(1, "v").string(2, "ctx").string(3, "pv").string(4, "MatMul"))
        return Proto().varint(1, 8).message(7, graph).message(8, Proto().string(1, "").varint(2, 17)).bytes()
    }
}
