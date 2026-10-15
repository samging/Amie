package org.example.app.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.TextDecorationLine
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * One line of a tree: a label, a muted detail after it, a small badge, children (a folder when there are any)
 * and, for a file, an optional link and its own buttons. [id] must be unique in the tree; it keys the open state.
 */
class TreeNode(
    val id: String,
    val label: String,
    val detail: String? = null,
    val badge: String? = null,
    val icon: (@Composable () -> Unit)? = null,
    val href: String? = null,
    val children: List<TreeNode> = emptyList(),
    val actions: (@Composable () -> Unit)? = null,
    /** Colours the badge (a file kind, an author) */
    val tone: Tone? = null,
) {
    val isFolder: Boolean get() = children.isNotEmpty()

    /** This node and everything under it */
    fun all(): List<TreeNode> = listOf(this) + children.flatMap { it.all() }
}

/** Every folder id in [nodes], for "expand all" */
fun List<TreeNode>.folderIds(): Set<String> = flatMap { it.all() }.filter { it.isFolder }.map { it.id }.toSet()

/**
 * A tree of folders and files, like a file browser: folders open and close on their arrow, their label or
 * Enter and Space; files link to their page and carry their buttons at the right. The whole tree is one
 * `role="tree"` with `treeitem`s, so screen readers read it as one.
 */
@Composable
fun TreeView(nodes: List<TreeNode>, expanded: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Div(modifier.fillMaxWidth().toAttrs { attr("role", "tree") }) {
        for (node in nodes) TreeItem(node, expanded, onToggle, depth = 0)
    }
}

@Composable
private fun TreeItem(node: TreeNode, expanded: Set<String>, onToggle: (String) -> Unit, depth: Int) {
    val palette = ColorMode.current.toSitePalette()
    val open = node.id in expanded
    Div(Modifier.toAttrs { attr("role", "treeitem"); if (node.isFolder) attr("aria-expanded", open.toString()) }) {
        Div(
            Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).fillMaxWidth().minHeight(2.cssRem)
                .padding(topBottom = 0.25.cssRem, leftRight = 0.4.cssRem)
                .then(if (node.isFolder) Modifier.cursor(Cursor.Pointer).userSelect(UserSelect.None) else Modifier)
                .styleModifier { property("border-bottom", "1px solid ${palette.border}") }
                .toAttrs {
                    if (node.isFolder) {
                        attr("tabindex", "0")
                        onClick { onToggle(node.id) }
                        onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); onToggle(node.id) } }
                    }
                }
        ) {
            // The arrow of a folder, or a dot that keeps a file's label aligned with the folders
            Span(Modifier.width(1.cssRem).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center).fontSize(10.px).color(palette.muted).flexShrink(0).toAttrs { attr("aria-hidden", "true") }) {
                Text(if (node.isFolder) (if (open) "▾" else "▸") else "·")
            }
            node.icon?.invoke()
            if (node.href != null) {
                A(node.href, attrs = Modifier.fontWeight(com.varabyte.kobweb.compose.css.FontWeight.Medium).textDecorationLine(TextDecorationLine.None).styleModifier { property("word-break", "break-all") }.toAttrs { onClick { it.stopPropagation() } }) { Text(node.label) }
            } else {
                Span(Modifier.then(if (node.isFolder) Modifier.fontWeight(com.varabyte.kobweb.compose.css.FontWeight.SemiBold) else Modifier).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(node.label) }
            }
            node.detail?.let { MutedText(it, Modifier.fontSize(9.px).minWidth(0.px)) }
            node.badge?.let { Badge(it, color = node.tone) }
            node.actions?.let { actions ->
                Div(Modifier.margin(left = 0.5.cssRem).styleModifier { property("margin-left", "auto") }.display(DisplayStyle.Flex).gap(0.4.cssRem).flexWrap(FlexWrap.Wrap).justifyContent(JustifyContent.FlexEnd).toAttrs { onClick { it.stopPropagation() } }) { actions() }
            }
        }
        if (node.isFolder && open) {
            Div(Modifier.padding(left = 1.1.cssRem).styleModifier { property("border-left", "1px solid ${palette.border}"); property("margin-left", "0.85rem") }.toAttrs { attr("role", "group") }) {
                for (child in node.children) TreeItem(child, expanded, onToggle, depth + 1)
            }
        }
    }
}
