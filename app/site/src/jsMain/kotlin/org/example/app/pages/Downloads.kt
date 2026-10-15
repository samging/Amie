package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.AuthSession
import org.example.app.auth.jwtJson
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.Platform
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
    @SerialName("etag") val eTag: String? = null,
    val version: String? = null,
    val kind: String = "package"
) {
    /** The MD5 of the file when the bucket stored it in one piece (then the ETag is exactly that). */
    val md5: String? get() = eTag?.trim('"')?.takeIf { it.length == 32 && it.all { c -> c.isLetterOrDigit() } }
}

private const val CACHE_KEY = "jars:list"

private fun parseJars(text: String): List<JarInfo> =
    runCatching { jwtJson.decodeFromString(ListSerializer(JarInfo.serializer()), text) }.getOrDefault(emptyList())

/** A link the person can paste anywhere: absolute even when the download goes through this site. */
private fun absolute(url: String): String = if (url.startsWith("http")) url else window.location.origin + url

private fun installHint(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "dmg" -> "Open the disk image and drag Amie into Applications. The first start may ask you to allow the app under System Settings › Privacy & Security."
    "pkg" -> "Open the installer and follow its steps."
    "msi", "exe" -> "Run the installer. Windows may ask you to confirm an app from an unknown publisher."
    "deb" -> "Install it with sudo dpkg -i <file>, or open it in your software center."
    "rpm" -> "Install it with sudo rpm -i <file>."
    "appimage" -> "Make the file executable and start it."
    "jar" -> "Needs Java 21 or newer: java -jar <file>."
    "zip" -> "Unpack it and start the app inside."
    else -> "Download it and start the app inside."
}

/** The jars in the S3 bucket, with a download link for each; served and cached by this site's server. */
@NoLiveLiterals
@Page("downloads")
@Composable
fun DownloadsPage() {
    val scope = rememberCoroutineScope()
    val username = remember { AuthSession.username }
    var jars by remember { mutableStateOf(ClientCache.getStale(CACHE_KEY)?.let { parseJars(it) }.orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

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
            error = "The download list could not be loaded right now." + if (jars.isNotEmpty()) " This is the last list this browser saw." else ""
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(refresh = false) }

    val applications = jars.filter { it.kind == "application" }
        .sortedWith(compareByDescending<JarInfo> { Platform.recommends(it.name) }.thenByDescending { it.lastModified ?: "" })
    val allPackages = jars.filter { it.kind != "application" }
    val packages = allPackages.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
    val recommended = applications.firstOrNull { Platform.recommends(it.name) }

    PageLayout("Downloads") {
        PageHeading("Downloads", "The Amie desktop app and the package jars published to Amie.") {
            GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { load(refresh = true) } }
            if (username != null) LinkButton("Upload a jar", "/navigateto?username=${encodeURIComponent(username)}")
        }

        StatusBanner(error, BannerKind.ERROR, Modifier.margin(bottom = 1.cssRem))

        if (jars.isNotEmpty()) {
            StatRow {
                StatTile("App builds", applications.size.toString(), recommended?.let { "one is for your ${Platform.name}" } ?: Platform.name?.let { "none yet for $it" })
                StatTile("Package jars", allPackages.size.toString(), "checked on upload")
                StatTile("Latest update", allPackages.plus(applications).mapNotNull { it.lastModified }.maxOrNull()?.let { formatRelative(it) } ?: "–")
            }
        }

        SectionHeading("Amie application", id = "app")
        when {
            loading && jars.isEmpty() -> SkeletonRows(rows = 2, label = "Loading downloads…")
            applications.isEmpty() -> Card(Modifier.margin(bottom = 2.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).flexWrap(FlexWrap.Wrap)) {
                Div {
                    Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("No application build has been published yet") }
                    MutedText("Builds for macOS, Windows and Linux appear here as soon as one is published.")
                }
            }
            else -> Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.8.cssRem).alignItems(AlignItems.Center).margin(bottom = 2.cssRem).toAttrs()) {
                for (app in applications) {
                    val forYou = Platform.recommends(app.name)
                    Card(
                        Modifier.width(com.varabyte.kobweb.compose.css.Width.FitContent).minWidth(18.cssRem).maxWidth(32.cssRem)
                            .then(if (forYou) Modifier.boxShadow(6.px, 6.px, 0.px, 0.px, Colors.White) else Modifier)
                    ) {
                        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                            Div(Modifier.fontSize(12.px).toAttrs()) { Text(app.name) }
                            if (forYou) Badge("For your ${Platform.name}", BadgeTone.FILLED)
                            else Platform.platformOf(app.name)?.let { Badge(it) }
                        }
                        MutedText(listOfNotNull(app.version?.let { "Version $it" }, formatSize(app.size.toDouble()), app.lastModified?.let { "updated ${formatRelative(it)}" }).joinToString(" · "))
                        HintText(installHint(app.name), Modifier.margin(top = 0.4.cssRem).maxWidth(28.cssRem))
                        Div(Modifier.margin(top = 0.8.cssRem).display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                            LinkButton("Download", AmieConfig.downloadUrl(app.name, app.kind, app.size), download = true)
                            CopyButton(absolute(AmieConfig.downloadUrl(app.name, app.kind, app.size)), label = "Copy link", toast = "Download link copied.")
                        }
                        app.md5?.let { md5 ->
                            Div(Modifier.margin(top = 0.7.cssRem).fontSize(8.px).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                MutedText("MD5", Modifier.fontSize(8.px))
                                Code(attrs = Modifier.fontSize(8.px).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(md5) }
                                CopyButton(md5, label = "Copy", done = "Copied", toast = "Checksum copied. Compare it with md5sum or shasum after the download.")
                            }
                        }
                    }
                }
            }
        }

        SectionHeading("Package jars", id = "jars") {
            if (allPackages.size > 3) {
                TextField(filter, { filter = it }, placeholder = "Filter jars…", modifier = Modifier.width(16.cssRem).height(2.cssRem).attrsModifier { attr("data-search", ""); attr("aria-label", "Filter jars") })
            }
        }
        MutedText("Each package was checked on upload to expose run(text) and close(), so the Amie app can open it as a session.", Modifier.margin(bottom = 0.6.cssRem))
        when {
            loading && jars.isEmpty() -> SkeletonRows(rows = 3, label = "Loading downloads…")
            allPackages.isEmpty() -> EmptyAction(
                "Nothing to download yet",
                "Jars show up here once someone uploads a package.",
                action = { if (username != null) LinkButton("Upload the first one", "/navigateto") else LinkButton("Sign in to upload", "/loginpage?next=%2Fnavigateto") }
            )
            packages.isEmpty() -> EmptyAction("No jar matches “${filter.trim()}”.", action = { GhostButton("Clear the filter") { filter = "" } })
            else -> TableScroll {
                Table(DataTableStyle.toModifier().toAttrs()) {
                    Thead {
                        Tr { Th { Text("Package") }; Th { Text("Version") }; Th { Text("Size") }; Th { Text("Updated") }; Th { Text("") } }
                    }
                    Tbody {
                        for (jar in packages) {
                            val url = AmieConfig.downloadUrl(jar.name, jar.kind, jar.size)
                            Tr {
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                        SpanText("☕")
                                        SpanText(jar.name, Modifier.fontWeight(FontWeight.Medium))
                                    }
                                }
                                Td { Text(jar.version ?: "–") }
                                Td { Text(formatSize(jar.size.toDouble())) }
                                Td { SpanText(formatRelative(jar.lastModified), Modifier.whiteSpace(WhiteSpace.NoWrap).attrsModifier { attr("title", formatDate(jar.lastModified)) }) }
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                        LinkButton("Download", url, download = true)
                                        CopyButton(absolute(url), label = "Copy link", toast = "Download link for ${jar.name} copied.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (allPackages.isNotEmpty()) {
            Card(Modifier.margin(top = 1.5.cssRem)) {
                Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("Using a package jar") }
                Ol(Modifier.margin(0.px).padding(left = 1.2.cssRem).fontSize(10.px).lineHeight(2).toAttrs()) {
                    Li { Text("Download the jar, or let the Amie app fetch it: the app lists what is published here.") }
                    Li { Text("In the app's sandbox, open it with "); Code { Text("initMod(\"name.jar\")") }; Text(" and ask it something with "); Code { Text("inference(\"text\")") }; Text(".") }
                    Li { Text("Close it with "); Code { Text("close()") }; Text(" when you are done, or terminate it on the Sessions tab.") }
                }
                Div(Modifier.margin(top = 0.6.cssRem).toAttrs()) { LinkButton("Read about sessions", "/docs/sessions", primary = false) }
            }
            MutedText("Downloads are cached by your browser and by the site, so fetching the same jar twice is instant.", Modifier.margin(top = 1.cssRem))
        }
    }
}
