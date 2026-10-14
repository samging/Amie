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

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    LaunchedEffect(Unit) {
        val user = AuthClient.currentUser()
        if (user == null || user != username) ctx.router.navigateTo("/loginpage?next=${encodeURIComponent(window().location.pathname + window().location.search)}")
    }

    fun choose(file: File?) {
        selectedFile = file
        notice = null
        if (file != null && file.name.endsWith(".jar", true)) {
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
            notice = "Uploading the new version…"
            noticeKind = BannerKind.INFO
            try {
                val token = AuthClient.accessToken()
                val form = FormData()
                form.append("file", file)
                form.append("filename", fileName)
                form.append("username", username)
                val result = Http.postBody(AmieConfig.backend("edit"), form, Http.bearer(token))
                if (result.ok) {
                    ClientCache.removePrefix("packages:")
                    runCatching { Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(username)}&refresh=1")) }
                    notice = "$fileName was updated."
                    noticeKind = BannerKind.SUCCESS
                    ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(username)}")
                } else {
                    notice = if (result.status == 401 || result.status == 403) "Please sign in again to update packages." else "The update did not go through."
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
        PageHeading("Replace $fileName", "The new file replaces the current version for everyone.")

        Div(
            Modifier.fillMaxWidth().minHeight(8.cssRem).padding(1.5.cssRem).borderRadius(14.px)
                .border(2.px, LineStyle.Dashed, if (dragging) palette.brand.primary else palette.border)
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
                Text("choose a file")
            }
            Input(InputType.File, attrs = Modifier.display(DisplayStyle.None).toAttrs {
                id("edit-file")
                onChange { evt -> choose((evt.target as HTMLInputElement).files?.item(0)) }
            })
        }

        StatusBanner(notice, noticeKind, Modifier.margin(top = 1.cssRem))

        Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).margin(top = 1.5.cssRem).toAttrs()) {
            PrimaryButton(if (busy) "Updating…" else "Update package", enabled = !busy && selectedFile != null) { submit() }
            LinkButton("Cancel", "/dashboard?username=${encodeURIComponent(username)}", primary = false)
        }
    }
}

private fun window() = kotlinx.browser.window
