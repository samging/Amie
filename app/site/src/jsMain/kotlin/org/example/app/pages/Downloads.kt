package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.AuthSession
import org.example.app.auth.jwtJson
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Serializable
data class JarInfo(
    val name: String,
    val key: String = "",
    val size: Long = 0,
    val lastModified: String? = null,
    val eTag: String? = null,
    val version: String? = null,
    val kind: String = "package"
)

private const val CACHE_KEY = "jars:list"

private fun parseJars(text: String): List<JarInfo> =
    runCatching { jwtJson.decodeFromString(ListSerializer(JarInfo.serializer()), text) }.getOrDefault(emptyList())

/** The jars in the S3 bucket, with a download link for each; served and cached by this site's server. */
@NoLiveLiterals
@Page("downloads")
@Composable
fun DownloadsPage() {
    val scope = rememberCoroutineScope()
    val username = remember { AuthSession.username }
    var jars by remember { mutableStateOf<List<JarInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(refresh: Boolean) {
        loading = true
        error = null
        try {
            val text = ClientCache.cached(CACHE_KEY, ClientCache.MINUTE, refresh) {
                val result = Http.get(AmieConfig.api("jars" + if (refresh) "?refresh=1" else ""))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            jars = parseJars(text)
        } catch (e: Throwable) {
            jars = ClientCache.getStale(CACHE_KEY)?.let { parseJars(it) } ?: emptyList()
            error = "The download list could not be loaded right now."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(refresh = false) }

    PageLayout("Downloads") {
        PageHeading("Downloads", "The Amie desktop app and the package jars published to Amie.") {
            GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { load(refresh = true) } }
            if (username != null) LinkButton("Upload a jar", "/navigateto?username=${encodeURIComponent(username)}")
        }

        StatusBanner(error, BannerKind.ERROR, Modifier.margin(bottom = 1.cssRem))

        val applications = jars.filter { it.kind == "application" }
        val packages = jars.filter { it.kind != "application" }

        H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Amie application") }
        when {
            loading && jars.isEmpty() -> EmptyState("Loading downloads…")
            applications.isEmpty() -> Card(Modifier.margin(bottom = 2.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).flexWrap(FlexWrap.Wrap)) {
                Mascot(variant = 1, size = 120)
                Div {
                    Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("No application build has been published yet") }
                    MutedText("An administrator publishes builds (dmg, msi, deb or jar) from the admin page.")
                }
            }
            else -> Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.8.cssRem).alignItems(AlignItems.Center).margin(bottom = 2.cssRem).toAttrs()) {
                Mascot(variant = 2, size = 130)
                for (app in applications) {
                    Card(Modifier.width(com.varabyte.kobweb.compose.css.Width.FitContent).minWidth(18.cssRem)) {
                        Div(Modifier.fontSize(12.px).toAttrs()) { Text(app.name) }
                        MutedText(listOfNotNull(app.version?.let { "Version $it" }, platformOf(app.name), formatSize(app.size.toDouble())).joinToString(" · "))
                        Div(Modifier.margin(top = 0.8.cssRem).toAttrs()) {
                            LinkButton("Download", AmieConfig.downloadUrl(app.name, app.kind, app.size), download = true)
                        }
                    }
                }
            }
        }

        H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Package jars") }
        MutedText("Each package was checked on upload to expose run(text) and close().", Modifier.margin(bottom = 0.6.cssRem))
        when {
            loading && jars.isEmpty() -> EmptyState("Loading downloads…")
            packages.isEmpty() -> Card {
                Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("Nothing to download yet") }
                MutedText("Jars show up here once someone uploads a package. ")
                A("/navigateto") { Text("Upload the first one") }
            }
            else -> Table(DataTableStyle.toModifier().toAttrs()) {
                Thead {
                    Tr { Th { Text("Package") }; Th { Text("Version") }; Th { Text("Size") }; Th { Text("Updated") }; Th { Text("") } }
                }
                Tbody {
                    for (jar in packages) {
                        Tr {
                            Td {
                                Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                    SpanText("☕")
                                    SpanText(jar.name, Modifier.fontWeight(FontWeight.Medium))
                                }
                            }
                            Td { Text(jar.version ?: "–") }
                            Td { Text(formatSize(jar.size.toDouble())) }
                            Td { Text(formatDate(jar.lastModified)) }
                            Td {
                                Div(Modifier.display(DisplayStyle.Flex).justifyContent(JustifyContent.FlexEnd).toAttrs()) {
                                    LinkButton("Download", AmieConfig.downloadUrl(jar.name, jar.kind, jar.size), download = true)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (jars.isNotEmpty()) {
            MutedText("Downloads are cached by your browser and by the site, so fetching the same jar twice does not reach the bucket again.", Modifier.margin(top = 1.cssRem))
        }
    }
}

private fun platformOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "dmg" -> "macOS"
    "msi", "exe" -> "Windows"
    "deb" -> "Linux"
    "jar" -> "Any system with Java"
    else -> null
}
