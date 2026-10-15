package org.example.app.pages

import androidx.compose.runtime.*
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
import org.example.app.components.PackageKind
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

/** Replaces one of the signed-in person's package files with a new version. */
@NoLiveLiterals
@Page
@Composable
fun EditPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()
    val packagePath = ctx.route.params["package"] ?: ""
    val username = ctx.route.params["username"] ?: ""
    val fileName = packagePath.substringAfterLast('/')
    val kind = PackageKind.of(fileName)

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    LaunchedEffect(Unit) {
        val user = AuthClient.currentUser()
        if (user == null || user != username) ctx.router.navigateTo("/loginpage?next=${encodeURIComponent(window().location.pathname + window().location.search)}")
    }

    fun choose(file: File?) {
        selectedFile = file
        notice = null
        progress = 0.0
        if (file == null) return
        if (kind == PackageKind.JAR && !file.name.endsWith(".jar", true)) {
            notice = "${fileName} is a jar, so the new version has to be a .jar file too."
            noticeKind = BannerKind.ERROR
            selectedFile = null
            return
        }
        if (file.name.endsWith(".jar", true)) {
            scope.launch {
                val check = runCatching { JarPrecheck.check(file) }.getOrNull()
                if (check != null && !check.ok) {
                    notice = check.problems.joinToString(" ")
                    noticeKind = BannerKind.ERROR
                    selectedFile = null
                }
            }
        }
    }

    fun submit() {
        val file = selectedFile ?: return
        scope.launch {
            busy = true
            progress = 0.0
            notice = null
            try {
                val token = AuthClient.accessToken()
                val form = FormData()
                form.append("file", file)
                form.append("filename", fileName)
                form.append("username", username)
                val result = Http.upload(AmieConfig.backend("edit"), form, Http.bearer(token)) { progress = it }
                if (result.ok && !result.text.startsWith("Error", ignoreCase = true)) {
                    ClientCache.removePrefix("packages:")
                    ClientCache.remove("jars:list")
                    runCatching { Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(username)}&refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("all-packages?refresh=1")) }
                    runCatching { Http.get(AmieConfig.api("view?package=${packagePath.split("/").joinToString("/") { encodeURIComponent(it) }}&refresh=1")) }
                    Toasts.show("$fileName was updated.", BannerKind.SUCCESS)
                    ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(username)}&updated=${encodeURIComponent(fileName)}")
                } else {
                    notice = when {
                        result.status == 401 || result.status == 403 -> "Please sign in again to update packages."
                        result.status == 0 -> "The upload was interrupted. Check the connection and try again."
                        else -> "The update did not go through."
                    }
                    noticeKind = BannerKind.ERROR
                }
            } catch (e: Throwable) {
                notice = "The update did not go through."
                noticeKind = BannerKind.ERROR
            } finally {
                busy = false
            }
        }
    }

    PageLayout("Replace $fileName") {
        Breadcrumbs(Crumb("Packages", "/"), Crumb(username, "/dashboard?username=${encodeURIComponent(username)}"), Crumb(fileName), Crumb("Replace"))
        PageHeading("Replace $fileName", "The new file keeps this name and replaces the current version for everyone. Earlier versions are not kept.")

        Div(
            Modifier.fillMaxWidth().minHeight(8.cssRem).padding(1.5.cssRem).borderRadius(14.px)
                .border(2.px, LineStyle.Dashed, if (dragging || selectedFile != null) palette.brand.primary else palette.border)
                .backgroundColor(if (dragging) palette.infoBackground else palette.nearBackground)
                .display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center).gap(0.6.cssRem).toAttrs {
                    onDragOver { it.preventDefault(); dragging = true }
                    onDragLeave { dragging = false }
                    onDrop { evt -> evt.preventDefault(); dragging = false; choose(evt.dataTransfer?.files?.item(0)) }
                }
        ) {
            Text(selectedFile?.let { "Selected: ${it.name} (${formatSize(it.size.toDouble())})" } ?: "Drop the new version here, or")
            Label(forId = "edit-file", attrs = Modifier.cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer).color(palette.brand.primary).toAttrs()) {
                Text(if (selectedFile == null) "choose a file" else "choose another file")
            }
            Input(InputType.File, attrs = Modifier.display(DisplayStyle.None).toAttrs {
                id("edit-file")
                if (kind == PackageKind.JAR) attr("accept", ".jar,application/java-archive")
                onChange { evt -> choose((evt.target as HTMLInputElement).files?.item(0)) }
            })
        }

        selectedFile?.let { file ->
            if (file.name != fileName) {
                StatusBanner("The file is called ${file.name}, but it will be stored as $fileName. To publish it under its own name, upload it as a new package instead.", BannerKind.INFO, Modifier.margin(top = 1.cssRem))
            }
        }
        if (busy) ProgressBar(progress, label = if (progress >= 1.0) "Storing the new version…" else "Uploading ${selectedFile?.name ?: "the file"}", Modifier.margin(top = 1.cssRem))
        StatusBanner(notice, noticeKind, Modifier.margin(top = 1.cssRem))

        Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).margin(top = 1.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
            PrimaryButton(if (busy) "Updating…" else "Replace package", enabled = !busy && selectedFile != null) { submit() }
            LinkButton("Cancel", "/dashboard?username=${encodeURIComponent(username)}", primary = false)
            LinkButton("Upload as a new package instead", "/navigateto", primary = false)
        }
    }
}

private fun window() = kotlinx.browser.window
