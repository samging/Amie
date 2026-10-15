package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
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
)

@Serializable data class OnnxOpset(val domain: String = "", val version: Long = 0)
@Serializable data class OnnxValue(val name: String = "", val type: String = "", val shape: List<String> = emptyList(), val doc: String = "")
@Serializable data class OnnxInitializer(val name: String = "", val type: String = "", val dims: List<Long> = emptyList(), val params: Long = 0, val bytes: Long = 0)
@Serializable data class OnnxAttribute(val name: String = "", val type: String = "", val value: String = "")
@Serializable data class OnnxNode(
    val index: Int = 0, val name: String = "", val opType: String = "", val domain: String = "",
    val inputs: List<String> = emptyList(), val outputs: List<String> = emptyList(), val attributes: List<OnnxAttribute> = emptyList(), val doc: String = "",
)

private val onnxJson = Json { ignoreUnknownKeys = true }

private fun count(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
private fun shape(dims: List<String>) = if (dims.isEmpty()) "scalar" else dims.joinToString(" × ", "[", "]")
private fun shapeOf(dims: List<Long>) = if (dims.isEmpty()) "scalar" else dims.joinToString(" × ", "[", "]")

/**
 * An ONNX model as a tree: what goes in, every node of the graph in order with its inputs, outputs and
 * attributes, what comes out, the weights, and the file's metadata. A chip per operator type filters the
 * graph to that operator. The structure is read on the site's server; nothing of the model runs here.
 */
@Composable
fun OnnxModelView(packagePath: String) {
    var summary by remember { mutableStateOf<OnnxSummary?>(null) }
    var state by remember { mutableStateOf("loading") }
    var reason by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf("inputs", "graph", "outputs")) }
    var onlyOp by remember { mutableStateOf<String?>(null) }

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
            GhostButton("Expand all") { expanded = treeOf(summary!!, onlyOp).folderIds() }
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
            StatRow {
                StatTile("Nodes", count(model.nodes.size.toLong()), "${model.opCounts.size} operator types")
                StatTile("Parameters", count(model.parameters), "${model.initializers.size} tensors")
                StatTile("Inputs → outputs", "${model.inputs.size} → ${model.outputs.size}", model.inputs.firstOrNull()?.let { "${it.type} ${shape(it.shape)}" })
                StatTile("Opset", mainOpset?.version?.toString() ?: "?", "IR version ${model.irVersion}")
                StatTile("Producer", model.producer.ifEmpty { "unknown" }, model.producerVersion.takeIf { it.isNotEmpty() })
            }
            if (model.opCounts.isNotEmpty()) {
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.4.cssRem).margin(bottom = 0.8.cssRem).toAttrs { attr("role", "group"); attr("aria-label", "Filter the graph by operator") }) {
                    Chip("All operators", onlyOp == null, model.nodes.size) { onlyOp = null }
                    for ((op, n) in model.opCounts) Chip(op, onlyOp == op, n) { onlyOp = if (onlyOp == op) null else op; expanded = expanded + "graph" }
                }
            }
            Card(Modifier.padding(0.4.cssRem, 0.6.cssRem)) {
                TreeView(treeOf(model, onlyOp), expanded, onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id })
            }
            if (model.doc.isNotEmpty()) HintText(model.doc, Modifier.margin(top = 0.6.cssRem))
        }
    }
}

private fun treeOf(model: OnnxSummary, onlyOp: String?): List<TreeNode> {
    val inputs = TreeNode("inputs", "Inputs", "${model.inputs.size}", children = model.inputs.map { v ->
        TreeNode("in:${v.name}", v.name, "${v.type} ${shape(v.shape)}" + (if (v.doc.isNotEmpty()) " · ${v.doc}" else ""))
    }.ifEmpty { listOf(TreeNode("in:none", "no inputs declared")) })
    val shown = model.nodes.filter { onlyOp == null || it.opType == onlyOp }
    val graph = TreeNode(
        "graph", if (model.graphName.isEmpty()) "Graph" else "Graph ${model.graphName}",
        if (onlyOp == null) "${model.nodes.size} nodes, in order" else "${shown.size} of ${model.nodes.size} nodes: $onlyOp",
        children = shown.map { node ->
            TreeNode(
                "node:${node.index}", "#${node.index} ${node.opType}", node.name.takeIf { it.isNotEmpty() },
                badge = node.domain.takeIf { it.isNotEmpty() && it != "ai.onnx" },
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
    return listOf(inputs, graph, outputs, weights, meta)
}
