package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The site server's answer to `/api/model-info`: an ONNX file's structure (see the server's OnnxReader) */
@Serializable
data class OnnxSummary(
    val irVersion: Long = 0,
    val producer: String = "",
    val producerVersion: String = "",
    val domain: String = "",
    val modelVersion: Long = 0,
    val doc: String = "",
    val opsets: List<OnnxOpset> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val graphName: String = "",
    val inputs: List<OnnxValue> = emptyList(),
    val outputs: List<OnnxValue> = emptyList(),
    val initializers: List<OnnxInitializer> = emptyList(),
    val nodes: List<OnnxNode> = emptyList(),
    val valueInfo: List<OnnxValue> = emptyList(),
    val opCounts: Map<String, Int> = emptyMap(),
    val parameters: Long = 0,
    val fileSize: Long = 0,
    /** Attention, feed-forward and normalization blocks the server found in the graph */
    val patterns: List<OnnxPattern> = emptyList(),
)

@Serializable data class OnnxOpset(val domain: String = "", val version: Long = 0)
@Serializable data class OnnxValue(val name: String = "", val type: String = "", val shape: List<String> = emptyList(), val doc: String = "")
@Serializable data class OnnxInitializer(val name: String = "", val type: String = "", val dims: List<Long> = emptyList(), val params: Long = 0, val bytes: Long = 0)
@Serializable data class OnnxAttribute(val name: String = "", val type: String = "", val value: String = "")
@Serializable data class OnnxNode(
    val index: Int = 0, val name: String = "", val opType: String = "", val domain: String = "",
    val inputs: List<String> = emptyList(), val outputs: List<String> = emptyList(), val attributes: List<OnnxAttribute> = emptyList(), val doc: String = "",
)
@Serializable data class OnnxPatternRole(val role: String = "", val node: Int = 0, val opType: String = "")
@Serializable data class OnnxPattern(
    val kind: String = "", val label: String = "", val first: Int = 0, val last: Int = 0,
    val nodes: List<Int> = emptyList(), val roles: List<OnnxPatternRole> = emptyList(), val notes: List<String> = emptyList(),
)

private val onnxJson = Json { ignoreUnknownKeys = true }

private fun count(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
private fun shape(dims: List<String>) = if (dims.isEmpty()) "scalar" else dims.joinToString(" × ", "[", "]")
private fun shapeOf(dims: List<Long>) = if (dims.isEmpty()) "scalar" else dims.joinToString(" × ", "[", "]")

// ---- colour: one muted hue per family of operators, readable on the light and the dark theme ---------

object Tones {
    val linear = Tone(124, 138, 214, "linear")           // MatMul, Gemm, attention: a quiet indigo
    val activation = Tone(201, 160, 92, "activation")    // Softmax, Gelu, Relu: sand
    val norm = Tone(104, 176, 166, "normalization")      // layer norm, batch norm: teal
    val shape = Tone(142, 156, 176, "shape")             // Reshape, Transpose, Gather: slate
    val math = Tone(196, 132, 132, "arithmetic")         // Add, Mul, Div: dusty rose
    val conv = Tone(136, 176, 128, "convolution")        // Conv, pooling, resize: moss
    val other = Tone(160, 160, 160, "other")
    val attention = Tone(160, 130, 200, "attention")     // the attention blocks: lavender
    val feedForward = Tone(201, 160, 92, "feed-forward")
    val normalization = norm

    private val linearOps = setOf("MatMul", "Gemm", "FusedMatMul", "MatMulInteger", "QLinearMatMul", "MatMulNBits", "Einsum", "Attention", "MultiHeadAttention", "GroupQueryAttention", "PackedAttention", "DecoderMaskedMultiHeadAttention")
    private val activationOps = setOf("Softmax", "LogSoftmax", "Gelu", "FastGelu", "QuickGelu", "BiasGelu", "Relu", "Silu", "Swish", "Sigmoid", "Tanh", "Erf", "LeakyRelu", "Elu", "Selu", "Softplus", "HardSwish", "HardSigmoid", "Mish", "PRelu")
    private val normOps = setOf("LayerNormalization", "SimplifiedLayerNormalization", "SkipLayerNormalization", "SkipSimplifiedLayerNormalization", "RMSNormalization", "BatchNormalization", "InstanceNormalization", "GroupNormalization", "GroupNorm", "LayerNorm", "LpNormalization", "MeanVarianceNormalization", "EmbedLayerNormalization", "ReduceMean", "ReduceSum", "ReduceMax", "ReduceMin", "ReduceL2")
    private val shapeOps = setOf("Reshape", "Transpose", "Concat", "Split", "Gather", "GatherElements", "GatherND", "Scatter", "ScatterND", "ScatterElements", "Slice", "Squeeze", "Unsqueeze", "Expand", "Flatten", "Shape", "Size", "Cast", "CastLike", "Identity", "Constant", "ConstantOfShape", "Tile", "Where", "Range", "NonZero", "TopK", "ArgMax", "ArgMin", "Pad", "Dropout", "Trilu", "OneHot", "Compress", "Resize", "Upsample")
    private val mathOps = setOf("Add", "Sub", "Mul", "Div", "Pow", "Sqrt", "Exp", "Log", "Neg", "Abs", "Reciprocal", "Max", "Min", "Sum", "Mean", "Clip", "Equal", "Greater", "Less", "Not", "And", "Or", "Mod", "Floor", "Ceil", "Round", "Sin", "Cos", "CumSum")
    private val convOps = setOf("Conv", "ConvTranspose", "ConvInteger", "QLinearConv", "MaxPool", "AveragePool", "GlobalAveragePool", "GlobalMaxPool", "LpPool", "DepthToSpace", "SpaceToDepth")

    fun of(opType: String): Tone = when (opType) {
        in linearOps -> linear
        in activationOps -> activation
        in normOps -> norm
        in shapeOps -> shape
        in mathOps -> math
        in convOps -> conv
        else -> other
    }

    fun ofKind(kind: String): Tone = when (kind) {
        "attention" -> attention
        "feed_forward" -> feedForward
        "normalization" -> normalization
        else -> other
    }
}

/** What the graph is narrowed to: one operator type, or the nodes of one kind of block */
private sealed class GraphFilter {
    class Op(val opType: String) : GraphFilter()
    class Kind(val kind: String) : GraphFilter()
}

/**
 * An ONNX model as a tree, every branch closed until opened: what goes in, the blocks the server recognised
 * (attention heads, feed-forward blocks, normalizations), every node of the graph in order, what comes out,
 * the weights, and the file's metadata. Chips filter the graph by operator type or by block kind. Each
 * operator family and each block kind has one muted colour so the structure can be read at a glance. The
 * structure is read on the site's server; nothing of the model runs here.
 */
@Composable
fun OnnxModelView(packagePath: String) {
    var summary by remember { mutableStateOf<OnnxSummary?>(null) }
    var state by remember { mutableStateOf("loading") }
    var reason by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf<GraphFilter?>(null) }

    LaunchedEffect(packagePath) {
        state = "loading"
        try {
            val encodedPath = packagePath.split("/").joinToString("/") { encodeURIComponent(it) }
            val text = ClientCache.cached("model-info:$packagePath", 5 * ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("model-info?package=$encodedPath"))
                if (!result.ok) throw IllegalStateException("${result.status}:${result.text}")
                result.text
            }
            summary = onnxJson.decodeFromString(OnnxSummary.serializer(), text)
            state = "ready"
        } catch (e: Throwable) {
            val message = e.message.orEmpty()
            state = if (message.startsWith("422")) "not-onnx" else "failed"
            reason = runCatching { onnxJson.parseToJsonElement(message.substringAfter(':')).let { (it as kotlinx.serialization.json.JsonObject)["error_description"]?.toString()?.trim('"') } }.getOrNull().orEmpty()
        }
    }

    SectionHeading("Inside the model") {
        if (state == "ready") {
            GhostButton("Expand all") { expanded = treeOf(summary!!, filter).folderIds() }
            GhostButton("Collapse all") { expanded = emptySet() }
        }
    }
    when (state) {
        "loading" -> SkeletonRows(rows = 4, label = "Reading the model…")
        "not-onnx" -> Card { MutedText("This file could not be read as an ONNX model" + (if (reason.isNotEmpty()) ": $reason" else ".") + " Download it to inspect it elsewhere.") }
        "failed" -> Card { MutedText("The model could not be read right now. Download it to inspect it elsewhere.") }
        else -> {
            val model = summary!!
            val mainOpset = model.opsets.firstOrNull { it.domain == "ai.onnx" || it.domain.isEmpty() } ?: model.opsets.firstOrNull()
            val attention = model.patterns.count { it.kind == "attention" }
            val feedForward = model.patterns.count { it.kind == "feed_forward" }
            val norms = model.patterns.count { it.kind == "normalization" }
            StatRow {
                StatTile("Nodes", count(model.nodes.size.toLong()), "${model.opCounts.size} operator types")
                StatTile("Parameters", count(model.parameters), "${model.initializers.size} tensors")
                StatTile(
                    "Attention", attention.toString(),
                    if (attention == 0 && feedForward == 0 && norms == 0) "no blocks recognised"
                    else listOf("$feedForward feed-forward", "$norms norm").joinToString(" · ")
                )
                StatTile("Inputs → outputs", "${model.inputs.size} → ${model.outputs.size}", model.inputs.firstOrNull()?.let { "${it.type} ${shape(it.shape)}" })
                StatTile("Opset", mainOpset?.version?.toString() ?: "?", "IR version ${model.irVersion}")
                StatTile("Producer", model.producer.ifEmpty { "unknown" }, model.producerVersion.takeIf { it.isNotEmpty() })
            }
            if (model.patterns.isNotEmpty()) {
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.4.cssRem).margin(bottom = 0.5.cssRem).toAttrs { attr("role", "group"); attr("aria-label", "Show the nodes of one kind of block") }) {
                    for ((kind, label) in listOf("attention" to "Attention blocks", "feed_forward" to "Feed-forward blocks", "normalization" to "Normalizations")) {
                        val n = model.patterns.count { it.kind == kind }
                        if (n == 0) continue
                        val on = (filter as? GraphFilter.Kind)?.kind == kind
                        ToneChip(label, on, Tones.ofKind(kind), n) { filter = if (on) null else GraphFilter.Kind(kind); expanded = expanded + "graph" + "patterns" }
                    }
                }
            }
            if (model.opCounts.isNotEmpty()) {
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.4.cssRem).margin(bottom = 0.8.cssRem).toAttrs { attr("role", "group"); attr("aria-label", "Filter the graph by operator") }) {
                    ToneChip("All operators", filter == null, Tones.other, model.nodes.size) { filter = null }
                    for ((op, n) in model.opCounts) {
                        val on = (filter as? GraphFilter.Op)?.opType == op
                        ToneChip(op, on, Tones.of(op), n) { filter = if (on) null else GraphFilter.Op(op); expanded = expanded + "graph" }
                    }
                }
            }
            Card(Modifier.padding(0.4.cssRem, 0.6.cssRem)) {
                TreeView(treeOf(model, filter), expanded, onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id })
            }
            Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.8.cssRem).margin(top = 0.6.cssRem).fontSize(9.px).toAttrs { attr("aria-label", "Colours") }) {
                for (tone in listOf(Tones.linear, Tones.activation, Tones.norm, Tones.shape, Tones.math, Tones.conv)) {
                    Span(Modifier.display(DisplayStyle.LegacyInlineFlex).alignItems(AlignItems.Center).gap(0.3.cssRem).toAttrs()) { ToneDot(tone, 8.px); MutedText(tone.name) }
                }
            }
            if (model.doc.isNotEmpty()) HintText(model.doc, Modifier.margin(top = 0.6.cssRem))
        }
    }
}

private fun treeOf(model: OnnxSummary, filter: GraphFilter?): List<TreeNode> {
    val blockOf = HashMap<Int, OnnxPattern>()
    for (p in model.patterns) for (i in p.nodes) blockOf[i] = p
    val shownIndices: Set<Int>? = when (filter) {
        null -> null
        is GraphFilter.Op -> model.nodes.filter { it.opType == filter.opType }.map { it.index }.toSet()
        is GraphFilter.Kind -> model.patterns.filter { it.kind == filter.kind }.flatMap { it.nodes }.toSet()
    }

    val inputs = TreeNode("inputs", "Inputs", "${model.inputs.size}", children = model.inputs.map { v ->
        TreeNode("in:${v.name}", v.name, "${v.type} ${shape(v.shape)}" + (if (v.doc.isNotEmpty()) " · ${v.doc}" else ""))
    }.ifEmpty { listOf(TreeNode("in:none", "no inputs declared")) })

    val patterns = TreeNode(
        "patterns", "Blocks",
        if (model.patterns.isEmpty()) "nothing recognised"
        else listOf("attention" to "attention", "feed_forward" to "feed-forward", "normalization" to "normalization")
            .mapNotNull { (k, name) -> model.patterns.count { it.kind == k }.takeIf { it > 0 }?.let { "$it $name" } }.joinToString(" · "),
        children = model.patterns.map { p ->
            val tone = Tones.ofKind(p.kind)
            TreeNode(
                "pat:${p.first}:${p.kind}", p.label,
                "nodes #${p.first}–#${p.last}" + (if (p.notes.isNotEmpty()) " · ${p.notes.joinToString(", ")}" else ""),
                icon = { ToneDot(tone) },
                children = p.roles.map { r ->
                    val node = model.nodes.getOrNull(r.node)
                    TreeNode("pat:${p.first}:${p.kind}:${r.role}:${r.node}", r.role, "#${r.node} ${r.opType}" + (node?.name?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), icon = { ToneDot(Tones.of(r.opType), 7.px) })
                }
            )
        }.ifEmpty { listOf(TreeNode("pat:none", "no attention, feed-forward or normalization blocks found")) }
    )

    val shown = model.nodes.filter { shownIndices == null || it.index in shownIndices }
    val graphDetail = when (filter) {
        null -> "${model.nodes.size} nodes, in order"
        is GraphFilter.Op -> "${shown.size} of ${model.nodes.size} nodes: ${filter.opType}"
        is GraphFilter.Kind -> "${shown.size} of ${model.nodes.size} nodes in ${filter.kind.replace('_', '-')} blocks"
    }
    val graph = TreeNode(
        "graph", if (model.graphName.isEmpty()) "Graph" else "Graph ${model.graphName}", graphDetail,
        children = shown.map { node ->
            val block = blockOf[node.index]
            TreeNode(
                "node:${node.index}", "#${node.index} ${node.opType}", node.name.takeIf { it.isNotEmpty() },
                badge = block?.label ?: node.domain.takeIf { it.isNotEmpty() && it != "ai.onnx" },
                icon = { ToneDot(Tones.of(node.opType)) },
                children = buildList {
                    add(TreeNode("node:${node.index}:in", "inputs", "${node.inputs.size}", children = node.inputs.mapIndexed { i, name ->
                        val weight = model.initializers.firstOrNull { it.name == name }
                        TreeNode("node:${node.index}:in:$i", name.ifEmpty { "(none)" }, weight?.let { "weight ${it.type} ${shapeOf(it.dims)}" })
                    }))
                    add(TreeNode("node:${node.index}:out", "outputs", "${node.outputs.size}", children = node.outputs.mapIndexed { i, name ->
                        val info = model.valueInfo.firstOrNull { it.name == name } ?: model.outputs.firstOrNull { it.name == name }
                        TreeNode("node:${node.index}:out:$i", name, info?.let { "${it.type} ${shape(it.shape)}" })
                    }))
                    if (node.attributes.isNotEmpty()) add(TreeNode("node:${node.index}:attr", "attributes", "${node.attributes.size}", children = node.attributes.map { a ->
                        TreeNode("node:${node.index}:attr:${a.name}", a.name, listOf(a.type, a.value).filter { it.isNotEmpty() }.joinToString(" = "))
                    }))
                    if (block != null) add(TreeNode("node:${node.index}:block", "part of", "${block.label}: ${block.roles.filter { it.node == node.index }.joinToString(", ") { it.role }.ifEmpty { "member" }}", icon = { ToneDot(Tones.ofKind(block.kind), 7.px) }))
                }
            )
        }.ifEmpty { listOf(TreeNode("graph:none", "no nodes")) }
    )
    val outputs = TreeNode("outputs", "Outputs", "${model.outputs.size}", children = model.outputs.map { v ->
        TreeNode("out:${v.name}", v.name, "${v.type} ${shape(v.shape)}")
    }.ifEmpty { listOf(TreeNode("out:none", "no outputs declared")) })
    val weights = TreeNode("weights", "Weights", "${model.initializers.size} tensors · ${count(model.parameters)} parameters", children = model.initializers.map { w ->
        TreeNode("w:${w.name}", w.name, "${w.type} ${shapeOf(w.dims)} · ${count(w.params)} values")
    }.ifEmpty { listOf(TreeNode("w:none", "no weights stored in the file")) })
    val meta = TreeNode("meta", "Metadata", null, children = buildList {
        add(TreeNode("meta:ir", "IR version", model.irVersion.toString()))
        add(TreeNode("meta:producer", "Producer", listOf(model.producer, model.producerVersion).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "unknown" }))
        if (model.domain.isNotEmpty()) add(TreeNode("meta:domain", "Domain", model.domain))
        if (model.modelVersion != 0L) add(TreeNode("meta:version", "Model version", model.modelVersion.toString()))
        add(TreeNode("meta:opsets", "Operator sets", "${model.opsets.size}", children = model.opsets.map { TreeNode("meta:opset:${it.domain}", it.domain, "version ${it.version}") }))
        if (model.metadata.isNotEmpty()) add(TreeNode("meta:props", "Properties", "${model.metadata.size}", children = model.metadata.map { (k, v) -> TreeNode("meta:prop:$k", k, v) }))
    })
    return listOf(inputs, patterns, graph, outputs, weights, meta)
}
