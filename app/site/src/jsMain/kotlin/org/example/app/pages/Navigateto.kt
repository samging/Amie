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

/** Uploads a package jar: checked in the browser first, then by the backend for the run/close contract. */
@NoLiveLiterals
@Page
@Composable
fun Navigateto() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()

    var user by remember { mutableStateOf<String?>(null) }
    var checkedAuth by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var precheck by remember { mutableStateOf<JarPrecheckResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    LaunchedEffect(Unit) {
        user = AuthClient.currentUser()
        checkedAuth = true
        if (user == null) ctx.router.navigateTo("/loginpage?next=${encodeURIComponent("/navigateto")}")
    }

    fun choose(file: File?) {
        selectedFile = file
        precheck = null
        notice = null
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
        notice = "Uploading ${file.name}…"
        noticeKind = BannerKind.INFO
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
            val result = Http.postBody(AmieConfig.backend("upload"), form, Http.bearer(token))
            when {
                result.ok -> {
                    ClientCache.removePrefix("packages:")
                    ClientCache.remove("jars:list")
                    // Let the site's server forget its copies too, so the new jar shows up at once
                    runCatching { Http.get(AmieConfig.api("jars?refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(owner)}&refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("packages?refresh=1")) }
                    notice = "${file.name} was uploaded."
                    noticeKind = BannerKind.SUCCESS
                    ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(owner)}")
                }
                result.status == 400 -> {
                    notice = "The jar was not accepted: ${result.text.ifBlank { "it does not look like an Amie package." }}"
                    noticeKind = BannerKind.ERROR
                }
                result.status == 401 || result.status == 403 -> {
                    notice = "Please sign in again before uploading."
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

    PageLayout("Upload") {
        PageHeading("Upload a package", user?.let { "Publishing as $it" })

        Card(Modifier.margin(bottom = 1.cssRem)) {
            Div(Modifier.fontWeight(FontWeight.SemiBold).margin(bottom = 0.4.cssRem).toAttrs()) { Text("What a package jar needs") }
            P(Modifier.margin(0.px).toAttrs()) {
                Text("A jar with at least one public class that has both ")
                Code { Text("run(input: String): List<String>") }
                Text(" and ")
                Code { Text("close()") }
                Text(". run handles one text input and returns the lines to show; close releases whatever run opened. Anything else is refused.")
            }
        }

        // Drop zone
        Div(
            Modifier.fillMaxWidth().minHeight(9.cssRem).padding(1.5.cssRem).borderRadius(14.px)
                .border(2.px, LineStyle.Dashed, if (dragging) palette.brand.primary else palette.border)
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
            Text(selectedFile?.let { "Selected: ${it.name} (${formatSize(it.size.toDouble())})" } ?: "Drop a .jar here, or")
            Label(forId = "jar-file", attrs = Modifier.cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer).color(palette.brand.primary).fontWeight(FontWeight.SemiBold).toAttrs()) {
                Text(if (selectedFile == null) "choose a file" else "choose another file")
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
                    "Looks like a jar: ${result.classCount} classes" + (if (result.hasManifest) ", with a manifest" else "") + ". The run/close check happens when you submit.",
                    BannerKind.SUCCESS, Modifier.margin(top = 1.cssRem)
                )
            } else {
                StatusBanner(result.problems.joinToString(" "), BannerKind.ERROR, Modifier.margin(top = 1.cssRem))
            }
        }

        StatusBanner(notice, noticeKind, Modifier.margin(top = 1.cssRem))

        Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).margin(top = 1.5.cssRem).toAttrs()) {
            PrimaryButton(
                if (uploading) "Uploading…" else "Upload package",
                enabled = !uploading && !checking && precheck?.ok == true && user != null
            ) { scope.launch { upload() } }
            if (selectedFile != null) GhostButton("Clear", enabled = !uploading) { choose(null) }
        }
    }
}
