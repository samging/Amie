package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.app.auth.AuthClient
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

private val extrasJson = Json { ignoreUnknownKeys = true }

// ---- badges -------------------------------------------------------------------------------------------

/** The badges administrators can grant, with what each means (mirrors the backend's BadgeCatalog). */
val BADGE_LABELS: Map<String, Pair<String, String>> = mapOf(
    "verified" to ("Verified" to "The administrators checked who this person is"),
    "maintainer" to ("Maintainer" to "Keeps packages others rely on up to date"),
    "contributor" to ("Contributor" to "Contributed to Amie itself"),
    "researcher" to ("Researcher" to "Publishes research with Amie"),
    "helper" to ("Helper" to "Answers questions and leaves useful notes"),
    "early" to ("Early adopter" to "Used Amie before most people"),
)

/** A row of badge labels; unknown keys are shown as they are (they are plain text either way). */
@Composable
fun BadgeRow(badges: List<String>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    Div(Modifier.display(DisplayStyle.Flex).gap(0.35.cssRem).flexWrap(FlexWrap.Wrap).then(modifier).toAttrs { attr("aria-label", "Badges") }) {
        for (key in badges) {
            val (label, meaning) = BADGE_LABELS[key] ?: (key to "")
            Span(Modifier.toAttrs { if (meaning.isNotEmpty()) attr("title", meaning) }) { Badge(label, color = SiteTones.ofBadge(key)) }
        }
    }
}

// ---- linked accounts ----------------------------------------------------------------------------------

@Serializable data class LinkedRepo(val name: String = "", val description: String = "", val language: String = "", val stars: Int = 0, val url: String = "", val updated: String = "")
@Serializable data class LinkedModel(val id: String = "", val pipeline: String = "", val downloads: Int = 0, val likes: Int = 0, val url: String = "")
@Serializable data class LinkedAccounts(val github: String = "", val huggingFace: String = "", val repos: List<LinkedRepo> = emptyList(), val models: List<LinkedModel> = emptyList())

private fun safeHttps(url: String, host: String): String? = url.takeIf { it.startsWith("https://$host/") }

/**
 * What a person's linked GitHub and Hugging Face accounts hold, read through the site's server
 * (`/api/linked`). Only `https://github.com/...` and `https://huggingface.co/...` links are rendered, and
 * every name is a text node. Handles are what the person typed; nothing is verified, which the heading says.
 */
@Composable
fun LinkedAccountsView(username: String, github: String, huggingFace: String) {
    if (github.isEmpty() && huggingFace.isEmpty()) return
    var linked by remember { mutableStateOf<LinkedAccounts?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(username, github, huggingFace) {
        try {
            val text = ClientCache.cached("linked:$username", 10 * ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("linked?username=${encodeURIComponent(username)}"))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            linked = extrasJson.decodeFromString(LinkedAccounts.serializer(), text)
        } catch (e: Throwable) { failed = true }
    }
    SectionHeading("Elsewhere", Modifier.margin(top = 2.cssRem)) { MutedText("as stated by the author", Modifier.fontSize(8.px)) }
    Div(Modifier.display(DisplayStyle.Flex).gap(0.8.cssRem).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.FlexStart).toAttrs()) {
        if (github.isNotEmpty()) Card(Modifier.flexGrow(1).minWidth(16.cssRem).maxWidth(32.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).margin(bottom = 0.5.cssRem).toAttrs()) {
                Badge("GitHub"); A("https://github.com/${encodeURIComponent(github)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text(github) }
            }
            when {
                linked == null && !failed -> SkeletonRows(rows = 2, label = "Reading repositories…")
                failed || linked!!.repos.isEmpty() -> MutedText(if (failed) "GitHub could not be reached right now." else "No public repositories.")
                else -> Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.35.cssRem).toAttrs()) {
                    for (repo in linked!!.repos) {
                        val url = safeHttps(repo.url, "github.com") ?: continue
                        Div(Modifier.fontSize(9.px).toAttrs()) {
                            A(url, attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text(repo.name) }
                            Div(Modifier.fontSize(8.px).toAttrs()) { MutedText(listOfNotNull(repo.language.takeIf { it.isNotEmpty() }, plural(repo.stars, "star"), repo.updated.takeIf { it.isNotEmpty() }?.let { "updated ${formatRelative(it)}" }).joinToString(" · "), Modifier.fontSize(8.px)) }
                            if (repo.description.isNotEmpty()) Div(Modifier.fontSize(8.px).margin(bottom = 0.2.cssRem).toAttrs()) { MutedText(repo.description, Modifier.fontSize(8.px)) }
                        }
                    }
                }
            }
        }
        if (huggingFace.isNotEmpty()) Card(Modifier.flexGrow(1).minWidth(16.cssRem).maxWidth(32.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).margin(bottom = 0.5.cssRem).toAttrs()) {
                Badge("Hugging Face"); A("https://huggingface.co/${encodeURIComponent(huggingFace)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text(huggingFace) }
            }
            when {
                linked == null && !failed -> SkeletonRows(rows = 2, label = "Reading models…")
                failed || linked!!.models.isEmpty() -> MutedText(if (failed) "Hugging Face could not be reached right now." else "No public models.")
                else -> Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.35.cssRem).toAttrs()) {
                    for (model in linked!!.models) {
                        val url = safeHttps(model.url, "huggingface.co") ?: continue
                        Div(Modifier.fontSize(9.px).toAttrs()) {
                            A(url, attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text(model.id) }
                            Div(Modifier.fontSize(8.px).toAttrs()) { MutedText(listOfNotNull(model.pipeline.takeIf { it.isNotEmpty() }, plural(model.downloads, "download"), plural(model.likes, "like")).joinToString(" · "), Modifier.fontSize(8.px)) }
                        }
                    }
                }
            }
        }
    }
}

// ---- publications -------------------------------------------------------------------------------------

@Serializable data class PublicationEntry(val id: Long = 0, val title: String = "", val venue: String = "", val year: Int? = null, val url: String = "", val doi: String = "", val author: String = "")

suspend fun loadPublications(username: String, refresh: Boolean = false): List<PublicationEntry> {
    if (refresh) ClientCache.remove("publications:$username")
    val text = ClientCache.cached("publications:$username", ClientCache.MINUTE) {
        val result = Http.get(AmieConfig.api("publications?username=${encodeURIComponent(username)}" + (if (refresh) "&refresh=1" else "")), noStore = true)
        if (!result.ok) throw IllegalStateException()
        result.text
    }
    return extrasJson.decodeFromString(ListSerializer(PublicationEntry.serializer()), text)
}

/** One publication: title (a link only when the stored address is http(s)), venue, year, DOI. */
@Composable
fun PublicationRow(p: PublicationEntry, actions: (@Composable () -> Unit)? = null) {
    Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.FlexStart).gap(0.6.cssRem).fontSize(10.px).toAttrs()) {
        Div(Modifier.minWidth(0.px).flexGrow(1).toAttrs()) {
            val href = p.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            Div { if (href != null) A(href, attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text(p.title) } else Span(Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(p.title) } }
            val meta = listOfNotNull(p.venue.takeIf { it.isNotEmpty() }, p.year?.toString()).joinToString(", ")
            if (meta.isNotEmpty()) Div(Modifier.fontSize(8.px).toAttrs()) { MutedText(meta, Modifier.fontSize(8.px)) }
            if (p.doi.isNotEmpty()) Div(Modifier.fontSize(8.px).toAttrs()) { A("https://doi.org/${p.doi}", attrs = Modifier.fontSize(8.px).toAttrs { attr("rel", "noopener nofollow"); attr("target", "_blank") }) { Text("doi:${p.doi}") } }
        }
        actions?.let { Div(Modifier.flexShrink(0).toAttrs()) { it() } }
    }
}

/** A person's publications, read-only, for the dashboard. */
@Composable
fun PublicationsView(username: String) {
    var publications by remember { mutableStateOf<List<PublicationEntry>?>(null) }
    LaunchedEffect(username) { publications = runCatching { loadPublications(username) }.getOrDefault(emptyList()) }
    val list = publications ?: return
    if (list.isEmpty()) return
    SectionHeading("Publications", Modifier.margin(top = 2.cssRem)) { MutedText(plural(list.size, "entry", "entries"), Modifier.fontSize(8.px)) }
    Card { Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.7.cssRem).toAttrs()) { for (p in list) PublicationRow(p) } }
}

/** The owner's publications with a form to add one and a Remove on each (profile page). */
@Composable
fun PublicationsEditor(username: String) {
    val scope = rememberCoroutineScope()
    var publications by remember { mutableStateOf<List<PublicationEntry>?>(null) }
    var title by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var doi by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) { publications = runCatching { loadPublications(username) }.getOrDefault(emptyList()) }

    fun add() {
        if (title.isBlank() || busy) return
        scope.launch {
            busy = true; notice = null
            try {
                val yearValue = year.trim().toIntOrNull()
                val body = """{"title":${JSON.stringify(title.trim())},"venue":${JSON.stringify(venue.trim())},"year":${yearValue ?: "null"},"url":${JSON.stringify(url.trim())},"doi":${JSON.stringify(doi.trim())}}"""
                val result = Http.postJson(AmieConfig.api("publications"), body, Http.bearer(AuthClient.accessToken()))
                if (result.ok) {
                    title = ""; venue = ""; year = ""; url = ""; doi = ""
                    publications = loadPublications(username, refresh = true)
                    Toasts.show("Publication added.", BannerKind.SUCCESS)
                } else notice = when (result.status) {
                    400 -> "Check the entry: a title is needed, the year must be between 1900 and 2100, the link must start with http(s):// and a DOI looks like 10.xxxx/…"
                    401 -> "Your session has ended. Sign in again."
                    429 -> "You are adding quickly; wait a moment."
                    else -> "The publication could not be saved right now."
                }
            } catch (e: Throwable) { notice = "The publication could not be saved right now." } finally { busy = false }
        }
    }

    fun remove(p: PublicationEntry) {
        scope.launch {
            val result = runCatching { Http.postForm(AmieConfig.api("publications-delete?id=${p.id}"), emptyMap(), Http.bearer(AuthClient.accessToken())) }.getOrNull()
            if (result?.ok == true || result?.status == 404) { publications = loadPublications(username, refresh = true); Toasts.show("Publication removed.", BannerKind.SUCCESS) }
            else Toasts.show("The publication could not be removed right now.", BannerKind.ERROR)
        }
    }

    Card(Modifier.maxWidth(32.cssRem)) {
        Div(Modifier.fontSize(12.px).margin(bottom = 0.4.cssRem).toAttrs()) { Text("Publications") }
        MutedText("Papers, theses and articles shown on your public page. Links must start with http(s)://.")
        val list = publications
        if (list == null) SkeletonRows(rows = 2, label = "Loading…")
        else if (list.isNotEmpty()) Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.6.cssRem).margin(topBottom = 0.8.cssRem).toAttrs()) {
            for (p in list) PublicationRow(p) { ConfirmDangerButton("Remove", "Remove this publication?", "Yes, remove") { remove(p) } }
        }
        Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.5.cssRem).margin(top = 0.8.cssRem).toAttrs()) {
            TextField(title, { if (it.length <= 200) title = it }, "Title", attrs = { attr("maxlength", "200") })
            Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                TextField(venue, { if (it.length <= 200) venue = it }, "Venue or journal", modifier = Modifier.flexGrow(1))
                TextField(year, { if (it.length <= 4 && it.all { c -> c.isDigit() }) year = it }, "Year", modifier = Modifier.width(6.cssRem), attrs = { attr("inputmode", "numeric") })
            }
            TextField(url, { if (it.length <= 500) url = it }, "https://… (optional)", InputType.Url)
            TextField(doi, { if (it.length <= 100) doi = it }, "DOI, e.g. 10.1234/abcd (optional)")
            StatusBanner(notice, BannerKind.ERROR)
            Div { PrimaryButton(if (busy) "Adding…" else "Add publication", enabled = !busy && title.isNotBlank()) { add() } }
        }
    }
}
