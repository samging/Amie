package org.example.app.api.onnx

import kotlinx.serialization.Serializable

/** One node's part in a pattern: `scores`, `softmax`, `context`, `up`, `activation`, `down`, … */
@Serializable
data class PatternRole(val role: String, val node: Int, val opType: String)

/**
 * A block of nodes that together form something a reader recognises: an attention head
 * (`kind = "attention"`), a feed-forward block (`feed_forward`) or a normalization (`normalization`).
 * [nodes] are the indices in graph order, [first]..[last] their span, [notes] what is special about it
 * (`scaled`, `masked`, `fused`, `gated`, `gelu (erf)`, `12 heads`, …).
 */
@Serializable
data class Pattern(
    val kind: String,
    val label: String,
    val first: Int,
    val last: Int,
    val nodes: List<Int>,
    val roles: List<PatternRole>,
    val notes: List<String> = emptyList(),
)

/**
 * Finds the attention-driven structure of a graph without running it: every scaled dot-product attention
 * (a `MatMul` of two activations, an optional scale and mask, a `Softmax`, then a `MatMul` with the values),
 * the fused attention operators of onnxruntime, the feed-forward blocks between them (a projection, an
 * activation, a projection back) and the normalizations, fused or spelled out in small operators.
 *
 * Everything is a walk over the tensor names: [detect] builds who produces and who consumes each tensor,
 * then from each anchor node (a `Softmax`, an activation, a `ReduceMean`) walks a few hops backwards and
 * forwards through the operators such a block may contain. A classifier's final `Softmax` is not attention,
 * because nothing multiplies its output again; a plain projection is not a scores `MatMul`, because one of
 * its inputs is a weight.
 */
object OnnxPatterns {
    const val ATTENTION = "attention"
    const val FEED_FORWARD = "feed_forward"
    const val NORMALIZATION = "normalization"

    /** Operators that only move data around and may sit anywhere inside a block */
    private val PASS_THROUGH = setOf("Reshape", "Transpose", "Cast", "Identity", "Squeeze", "Unsqueeze", "Dropout", "Expand", "Flatten", "Slice", "Gather", "Concat")
    private val SCALE = setOf("Div", "Mul", "Sqrt")
    private val MASK = setOf("Add", "Where", "Sub", "Max", "Min")
    private val LINEAR = setOf("MatMul", "Gemm", "FusedMatMul", "MatMulInteger", "QLinearMatMul", "MatMulNBits", "MatMulBnb4", "MatMulFpQ4", "Einsum")
    private val ACTIVATIONS = setOf("Gelu", "FastGelu", "QuickGelu", "BiasGelu", "Relu", "Silu", "Swish", "Sigmoid", "Tanh", "Erf", "LeakyRelu", "Elu", "Selu", "Softplus", "HardSwish", "HardSigmoid", "Mish", "PRelu", "Celu", "ThresholdedRelu")
    private val FUSED_ATTENTION = setOf("Attention", "MultiHeadAttention", "GroupQueryAttention", "PackedAttention", "PackedMultiHeadAttention", "DecoderMaskedMultiHeadAttention", "DecoderMaskedSelfAttention", "SparseAttention", "LongformerAttention", "QAttention")
    private val FUSED_NORM = setOf("LayerNormalization", "SimplifiedLayerNormalization", "SkipLayerNormalization", "SkipSimplifiedLayerNormalization", "RMSNormalization", "BatchNormalization", "InstanceNormalization", "GroupNormalization", "GroupNorm", "LayerNorm", "LpNormalization", "MeanVarianceNormalization", "EmbedLayerNormalization")

    private class Graph(val nodes: List<Node>, val weights: Set<String>) {
        val producer = HashMap<String, Node>()
        val consumers = HashMap<String, MutableList<Node>>()
        init {
            for (n in nodes) {
                for (o in n.outputs) if (o.isNotEmpty()) producer[o] = n
                for (i in n.inputs) if (i.isNotEmpty()) consumers.getOrPut(i) { mutableListOf() } += n
            }
        }
        fun hasWeight(n: Node) = n.inputs.any { it in weights }
        fun before(n: Node) = n.inputs.mapNotNull { producer[it] }
        fun after(n: Node) = n.outputs.flatMap { consumers[it].orEmpty() }
    }

    /** Breadth-first through [through] operators, at most [maxHops] hops, to the first node [target] accepts; the path ends with it. */
    private fun walk(g: Graph, start: Node, backwards: Boolean, through: Set<String>, maxHops: Int, target: (Node) -> Boolean): List<Node>? {
        val parent = HashMap<Int, Node>()
        val seen = hashSetOf(start.index)
        var frontier = listOf(start)
        repeat(maxHops) {
            val next = mutableListOf<Node>()
            for (n in frontier) {
                for (m in (if (backwards) g.before(n) else g.after(n))) {
                    if (!seen.add(m.index)) continue
                    parent[m.index] = n
                    if (target(m)) {
                        val path = mutableListOf(m)
                        var cur: Node? = n
                        while (cur != null && cur.index != start.index) { path += cur; cur = parent[cur.index] }
                        return path.reversed()
                    }
                    if (m.opType in through) next += m
                }
            }
            if (next.isEmpty()) return null
            frontier = next
        }
        return null
    }

    /**
     * [weightNames] are the graph's initializers (a `MatMul` with one is a projection, not a scores product);
     * [shapes] the tensor shapes the exporter recorded (`valueInfo`), used for the head count.
     */
    fun detect(nodes: List<Node>, weightNames: Set<String>, shapes: Map<String, List<String>> = emptyMap()): List<Pattern> {
        if (nodes.isEmpty()) return emptyList()
        val g = Graph(nodes, weightNames)
        val found = mutableListOf<Pattern>()
        val claimed = HashSet<Int>()

        // ---- attention: scores MatMul -> (scale) -> (mask) -> Softmax -> context MatMul ----------------
        for (soft in nodes) {
            if (soft.opType != "Softmax" || soft.index in claimed) continue
            val toScores = walk(g, soft, backwards = true, through = PASS_THROUGH + SCALE + MASK, maxHops = 8) { it.opType in LINEAR && !g.hasWeight(it) } ?: continue
            val toContext = walk(g, soft, backwards = false, through = PASS_THROUGH, maxHops = 5) { it.opType in LINEAR && !g.hasWeight(it) } ?: continue
            val scores = toScores.last(); val context = toContext.last()
            val roles = mutableListOf<PatternRole>()
            val notes = mutableListOf<String>()
            val members = linkedSetOf<Node>()
            // The projections that feed Q and K, and V, and the one after: a MatMul/Gemm with a weight, a few hops away
            val projections = mutableListOf<Node>()
            for ((role, from) in listOf("query" to scores, "key" to scores)) {
                val taken = projections.map { it.index }.toSet()
                walk(g, from, backwards = true, through = PASS_THROUGH + setOf("Add", "Mul", "Div"), maxHops = 5) { it.opType in LINEAR && g.hasWeight(it) && it.index !in taken }?.let { path ->
                    projections += path.last(); members += path.dropLast(1); roles += PatternRole(role, path.last().index, path.last().opType)
                }
            }
            walk(g, context, backwards = true, through = PASS_THROUGH + setOf("Add", "Mul", "Div"), maxHops = 5) { it.opType in LINEAR && g.hasWeight(it) && it.index !in projections.map { p -> p.index } && it.index !in toContext.map { p -> p.index } }?.let { path ->
                projections += path.last(); members += path.dropLast(1); roles += PatternRole("value", path.last().index, path.last().opType)
            }
            members += projections
            roles += PatternRole("scores", scores.index, scores.opType)
            for (n in toScores.dropLast(1).reversed()) {
                when (n.opType) {
                    in SCALE -> { roles += PatternRole("scale", n.index, n.opType); if ("scaled" !in notes) notes += "scaled" }
                    in MASK -> { roles += PatternRole("mask", n.index, n.opType); if ("masked" !in notes) notes += "masked" }
                }
            }
            roles += PatternRole("softmax", soft.index, soft.opType)
            roles += PatternRole("context", context.index, context.opType)
            members += toScores; members += soft; members += toContext
            walk(g, context, backwards = false, through = PASS_THROUGH, maxHops = 4) { it.opType in LINEAR && g.hasWeight(it) }?.let { path ->
                members += path; roles += PatternRole("output", path.last().index, path.last().opType)
            }
            headsFrom(scores, context, shapes)?.let { notes += "$it heads" }
            found += pattern(ATTENTION, members, roles.sortedBy { it.node }, notes, claimed)
        }
        for (n in nodes) {
            if (n.opType !in FUSED_ATTENTION || n.index in claimed) continue
            val notes = mutableListOf("fused")
            n.attributes.firstOrNull { it.name == "num_heads" }?.value?.let { notes += "$it heads" }
            if (n.inputs.size >= 4 && n.inputs[3].isNotEmpty() && n.opType == "Attention") notes += "masked"
            found += pattern(ATTENTION, listOf(n), listOf(PatternRole("attention", n.index, n.opType)), notes, claimed)
        }

        // ---- feed-forward: projection -> activation -> projection -----------------------------------
        for (act in nodes) {
            if (act.opType !in ACTIVATIONS || act.index in claimed) continue
            val up = walk(g, act, backwards = true, through = PASS_THROUGH + setOf("Add", "Mul", "Div"), maxHops = 5) { it.opType in LINEAR && g.hasWeight(it) } ?: continue
            val down = walk(g, act, backwards = false, through = PASS_THROUGH + setOf("Add", "Mul", "Div"), maxHops = 6) { it.opType in LINEAR && g.hasWeight(it) } ?: continue
            if (up.last().index == down.last().index) continue
            val members = linkedSetOf<Node>(); members += up; members += act; members += down
            val notes = mutableListOf<String>()
            val activation = when (act.opType) {
                "Erf" -> "gelu (erf)"
                "Sigmoid" -> if (down.any { it.opType == "Mul" }) "silu (sigmoid)" else "sigmoid"
                else -> act.opType.lowercase()
            }
            notes += activation
            val roles = mutableListOf(PatternRole("up", up.last().index, up.last().opType), PatternRole("activation", act.index, act.opType), PatternRole("down", down.last().index, down.last().opType))
            // A gate: a Mul on the way down whose other input is another projection of the same input (SwiGLU, GeGLU)
            for (mul in down.filter { it.opType == "Mul" }) {
                val inside = members.map { m -> m.index }.toSet()
                val gate = g.before(mul).filter { it.index !in inside }.firstNotNullOfOrNull { o ->
                    if (o.opType in LINEAR && g.hasWeight(o)) o else walk(g, o, backwards = true, through = PASS_THROUGH, maxHops = 3) { it.opType in LINEAR && g.hasWeight(it) }?.last()
                } ?: continue
                if (gate.index == up.last().index || gate.index == down.last().index) continue
                members += gate; roles += PatternRole("gate", gate.index, gate.opType); notes += "gated"
                break
            }
            found += pattern(FEED_FORWARD, members, roles.sortedBy { it.node }, notes, claimed)
        }

        // ---- normalization: fused, or ReduceMean -> Sub -> Pow -> ReduceMean -> Add -> Sqrt -> Div ----
        for (n in nodes) {
            if (n.opType !in FUSED_NORM || n.index in claimed) continue
            found += pattern(NORMALIZATION, listOf(n), listOf(PatternRole("norm", n.index, n.opType)), listOf("fused", n.opType), claimed)
        }
        for (mean in nodes) {
            if (mean.opType != "ReduceMean" || mean.index in claimed) continue
            val sub = g.after(mean).firstOrNull { it.opType == "Sub" && it.inputs.any { i -> i in mean.inputs } }
            val variance: Node; val members = linkedSetOf<Node>()
            val notes = mutableListOf<String>()
            if (sub != null) {
                members += mean; members += sub
                variance = walk(g, sub, backwards = false, through = setOf("Pow", "Mul", "Cast"), maxHops = 3) { it.opType == "ReduceMean" }?.also { members += it }?.last() ?: continue
                notes += "layer norm"
            } else {
                // RMS norm: no centring, the mean is of the squares
                val pow = g.before(mean).firstOrNull { it.opType == "Pow" || (it.opType == "Mul" && it.inputs.distinct().size == 1) } ?: continue
                members += pow; members += mean; variance = mean
                notes += "rms norm"
            }
            val toDivide = walk(g, variance, backwards = false, through = setOf("Add", "Sqrt", "Cast", "Reciprocal", "Pow"), maxHops = 5) { it.opType == "Div" || (it.opType == "Mul" && g.before(it).any { b -> b.opType == "Reciprocal" || b.opType == "Pow" }) } ?: continue
            if (toDivide.none { it.opType == "Sqrt" || it.opType == "Pow" || it.opType == "Reciprocal" }) continue
            members += toDivide
            val roles = mutableListOf(PatternRole("mean", mean.index, mean.opType), PatternRole("variance", variance.index, variance.opType), PatternRole("normalize", toDivide.last().index, toDivide.last().opType))
            // gamma and beta: a Mul then an Add that take the normalized tensor and a weight
            var tail: Node = toDivide.last()
            for (role in listOf("scale", "shift")) {
                val next = g.after(tail).firstOrNull { (it.opType == "Mul" || it.opType == "Add") && g.hasWeight(it) } ?: break
                members += next; roles += PatternRole(role, next.index, next.opType); tail = next
            }
            found += pattern(NORMALIZATION, members, roles, notes, claimed)
        }

        // Number each kind in graph order
        val counters = HashMap<String, Int>()
        return found.sortedBy { it.first }.map { p ->
            val n = (counters[p.kind] ?: 0) + 1; counters[p.kind] = n
            p.copy(label = "${when (p.kind) { ATTENTION -> "Attention"; FEED_FORWARD -> "Feed-forward"; else -> "Normalization" }} $n")
        }
    }

    private fun pattern(kind: String, members: Collection<Node>, roles: List<PatternRole>, notes: List<String>, claimed: MutableSet<Int>): Pattern {
        val indices = members.map { it.index }.distinct().sorted()
        claimed += indices
        return Pattern(kind, kind, indices.first(), indices.last(), indices, roles.distinctBy { it.role to it.node }, notes.distinct())
    }

    /**
     * The head count, when the exporter recorded the shape of the scores or the context tensor: after the
     * usual transpose they are `[batch, heads, seq, …]`, so the second dimension is the number of heads.
     */
    private fun headsFrom(scores: Node, context: Node, shapes: Map<String, List<String>>): Int? {
        val candidates = scores.outputs + context.inputs + scores.inputs + context.outputs
        for (name in candidates) {
            val dims = shapes[name] ?: continue
            if (dims.size != 4) continue
            val heads = dims[1].toIntOrNull() ?: continue
            if (heads in 1..512) return heads
        }
        return null
    }
}
