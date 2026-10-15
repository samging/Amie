package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.coroutines.launch
import org.example.app.auth.AuthClient
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.JarPrecheck
import org.example.app.components.JarPrecheckResult
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.xhr.FormData
import kotlin.js.Json

private fun namesIn(text: String): Set<String> {
    if (text.isBlank()) return emptySet()
    val json = JSON.parse<dynamic>(text)
    if (!(js("Array.isArray(json)") as Boolean)) return emptySet()
    return json.unsafeCast<Array<Json>>().mapNotNull { it["name"] as? String }.toSet()
}

/** Uploads a package jar: checked in the browser first, then by the backend for the run/close contract. */
@NoLiveLiterals
@Page
@Composable
fun Navigateto() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()

    var user by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var precheck by remember { mutableStateOf<JarPrecheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    LaunchedEffect(Unit) {
        user = AuthClient.currentUser()
        val owner = user
        if (owner == null) {
            ctx.router.navigateTo("/loginpage?next=${encodeURIComponent("/navigateto")}")
            return@LaunchedEffect
        }
        // What the person already published, to warn before a file replaces an older one with the same name
        existing = runCatching {
            namesIn(ClientCache.cached("packages:user:$owner", ClientCache.MINUTE) {
                Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(owner)}")).takeIf { it.ok }?.text ?: "[]"
            })
        }.getOrDefault(emptySet())
    }

    fun choose(file: File?) {
        selectedFile = file
        precheck = null
        notice = null
        progress = 0.0
        if (file == null) return
        checking = true
        scope.launch {
            precheck = runCatching { JarPrecheck.check(file) }.getOrElse { JarPrecheckResult(listOf("The file could not be read."), 0, 0, false) }
            checking = false
        }
    }

    suspend fun upload() {
        val file = selectedFile ?: return
        val owner = user ?: return
        uploading = true
        progress = 0.0
        notice = null
        try {
            val token = AuthClient.accessToken()
            if (token == null) {
                notice = "Please sign in again before uploading."
                noticeKind = BannerKind.ERROR
                return
            }
            val form = FormData()
            form.append("file", file)
            form.append("username", owner)
            form.append("progLanguage", "jar")
            val result = Http.upload(AmieConfig.api("upload"), form, Http.bearer(token)) { progress = it }
            when {
                result.ok -> {
                    progress = 1.0
                    ClientCache.removePrefix("packages:")
                    ClientCache.remove("jars:list")
                    // Let the site's server forget its copies too, so the new jar shows up at once
                    runCatching { Http.get(AmieConfig.api("jars?refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(owner)}&refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("all-packages?refresh=1")) }
                    Toasts.show("${file.name} was uploaded.", BannerKind.SUCCESS)
                    ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(owner)}&uploaded=${encodeURIComponent(file.name)}")
                }
                result.status == 400 -> {
                    notice = "The jar was not accepted: ${result.text.ifBlank { "it does not look like an Amie package." }}"
                    noticeKind = BannerKind.ERROR
                }
                result.status == 401 || result.status == 403 -> {
                    notice = "Please sign in again before uploading."
                    noticeKind = BannerKind.ERROR
                }
                result.status == 0 -> {
                    notice = "The upload was interrupted. Check the connection and try again."
                    noticeKind = BannerKind.ERROR
                }
                else -> {
                    notice = "The upload did not go through. Please try again."
                    noticeKind = BannerKind.ERROR
                }
            }
        } catch (e: Throwable) {
            notice = "The upload did not go through. Please try again."
            noticeKind = BannerKind.ERROR
        } finally {
            uploading = false
        }
    }

    val file = selectedFile
    val ready = precheck?.ok == true
    val step = when {
        uploading -> 2
        ready -> 2
        file != null -> 1
        else -> 0
    }
    val replaces = file != null && file.name in existing

    PageLayout("Upload") {
        Breadcrumbs(Crumb("Packages", "/"), user?.let { Crumb(it, "/dashboard?username=${encodeURIComponent(it)}") } ?: Crumb("You"), Crumb("Upload"))
        PageHeading("Upload a package", user?.let { "Publishing as $it" })
        Steps(listOf("Choose a jar", "Check it", "Upload"), step)

        Card(Modifier.margin(bottom = 1.cssRem)) {
            Div(Modifier.fontWeight(FontWeight.SemiBold).margin(bottom = 0.4.cssRem).toAttrs()) { Text("What a package jar needs") }
            P(Modifier.margin(0.px).toAttrs()) {
                Text("A jar with at least one public class that has both ")
                Code { Text("run(input: String): List<String>") }
                Text(" and ")
                Code { Text("close()") }
                Text(". run handles one text input and returns the lines to show; close releases whatever run opened. Anything else is refused.")
            }
            Div(Modifier.margin(top = 0.6.cssRem).display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                LinkButton("See published jars", "/downloads", primary = false)
                LinkButton("How the app runs a package", "/docs/sessions", primary = false)
            }
        }

        // Drop zone
        Div(
            Modifier.fillMaxWidth().minHeight(9.cssRem).padding(1.5.cssRem).borderRadius(14.px)
                .border(2.px, LineStyle.Dashed, if (dragging || file != null) palette.brand.primary else palette.border)
                .backgroundColor(if (dragging) palette.infoBackground else palette.nearBackground)
                .display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center).gap(0.6.cssRem).toAttrs {
                    onDragOver { it.preventDefault(); dragging = true }
                    onDragLeave { dragging = false }
                    onDrop { evt ->
                        evt.preventDefault()
                        dragging = false
                        choose(evt.dataTransfer?.files?.item(0))
                    }
                }
        ) {
            Text(file?.let { "Selected: ${it.name} (${formatSize(it.size.toDouble())})" } ?: "Drop a .jar here, or")
            Label(forId = "jar-file", attrs = Modifier.cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer).color(palette.brand.primary).fontWeight(FontWeight.SemiBold).toAttrs()) {
                Text(if (file == null) "choose a file" else "choose another file")
            }
            Input(InputType.File, attrs = Modifier.display(DisplayStyle.None).toAttrs {
                id("jar-file")
                attr("accept", ".jar,application/java-archive")
                onChange { evt -> choose((evt.target as HTMLInputElement).files?.item(0)) }
            })
        }

        // Precheck result
        if (checking) {
            StatusBanner("Checking the file…", BannerKind.INFO, Modifier.margin(top = 1.cssRem))
        }
        precheck?.let { result ->
            if (result.ok) {
                StatusBanner(
                    "Looks like a jar: ${plural(result.classCount, "class", "classes")}" + (if (result.hasManifest) ", with a manifest" else "") + ". The run/close check happens when you upload.",
                    BannerKind.SUCCESS, Modifier.margin(top = 1.cssRem)
                )
                Card(Modifier.margin(top = 0.8.cssRem)) {
                    Div(Modifier.display(DisplayStyle.Grid).gridTemplateColumns { size(minContent); size(1.fr) }.gap(0.3.cssRem, 1.cssRem).fontSize(10.px).lineHeight(1.9).toAttrs()) {
                        MutedText("File"); Div { Text(file?.name ?: "–") }
                        MutedText("Size"); Div { Text(formatSize(file?.size?.toDouble() ?: 0.0)) }
                        MutedText("Entries"); Div { Text("${result.entries} in the archive, ${result.classCount} compiled classes") }
                        MutedText("Manifest"); Div { Text(if (result.hasManifest) "present" else "none (that is fine for a package)") }
                    }
                }
                if (replaces) {
                    StatusBanner("You already published a file called ${file?.name}. Uploading replaces it for everyone who downloads it.", BannerKind.INFO, Modifier.margin(top = 0.8.cssRem))
                }
                if ((file?.size?.toDouble() ?: 0.0) > 50.0 * 1024 * 1024) {
                    HintText("Large file: keep this page open while the bar fills.", Modifier.margin(top = 0.5.cssRem))
                }
            } else {
                StatusBanner(result.problems.joinToString(" "), BannerKind.ERROR, Modifier.margin(top = 1.cssRem))
            }
        }

        if (uploading) {
            ProgressBar(progress, label = if (progress >= 1.0) "Checking run/close on the server…" else "Uploading ${file?.name ?: "the file"}", Modifier.margin(top = 1.cssRem))
        }
        StatusBanner(notice, noticeKind, Modifier.margin(top = 1.cssRem))

        Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).margin(top = 1.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
            PrimaryButton(
                when {
                    uploading -> "Uploading…"
                    replaces -> "Upload and replace"
                    else -> "Upload package"
                },
                enabled = !uploading && !checking && ready && user != null
            ) { scope.launch { upload() } }
            if (file != null) GhostButton("Clear", enabled = !uploading) { choose(null) }
            user?.let { LinkButton("Your packages", "/dashboard?username=${encodeURIComponent(it)}", primary = false) }
        }
        if (file == null) HintText("Nothing is sent until you press Upload. The file is first read in your browser to make sure it is a jar with compiled classes.", Modifier.margin(top = 0.8.cssRem))
    }
}
