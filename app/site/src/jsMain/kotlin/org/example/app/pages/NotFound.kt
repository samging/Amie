package org.example.app.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.init.InitKobweb
import com.varabyte.kobweb.core.init.InitKobwebContext
import com.varabyte.kobweb.silk.components.navigation.Link
import kotlinx.browser.window
import org.example.app.components.layouts.DocsPages
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** Routes the site does not know render [ErrorPage] instead of the bare "Error code: 404". */
@InitKobweb
fun initErrorPage(ctx: InitKobwebContext) {
    ctx.router.setErrorHandler { code -> ErrorPage(code) }
}

private val knownPages = listOf(
    "/" to "Packages", "/downloads" to "Downloads", "/docs" to "Docs", "/navigateto" to "Upload a package",
    "/loginpage" to "Sign in", "/register" to "Create an account", "/profile" to "Your profile", "/about" to "About Amie",
    "/dashboard" to "Dashboard", "/admin" to "Administration"
)

/** Pages whose route or title shares a word with the path that was typed. */
private fun suggestionsFor(path: String): List<Pair<String, String>> {
    val words = path.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
    if (words.isEmpty()) return emptyList()
    val docs = DocsPages.flatMap { (_, pages) -> pages.map { (route, title) -> "/$route" to title } }
    return (knownPages + docs).filter { (route, title) ->
        val text = (route + " " + title).lowercase()
        words.any { word -> text.contains(word) || word.contains(route.trim('/').takeIf { it.length >= 3 } ?: "\u0000") }
    }.distinctBy { it.first }.take(5)
}

@NoLiveLiterals
@Composable
fun ErrorPage(code: Int) {
    val path = window.location.pathname
    val suggestions = suggestionsFor(path)

    PageLayout(if (code == 404) "Page not found" else "Error $code") {
        Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).gap(2.cssRem).toAttrs()) {
            Div(Modifier.flexGrow(1).minWidth(16.cssRem).maxWidth(36.cssRem).toAttrs()) {
                PageHeading(
                    if (code == 404) "There is nothing at this address" else "Something went wrong",
                    if (code == 404) "$path does not exist on Amie. The link may be old, or a letter is off." else "The page could not be shown (error $code)."
                )
                if (suggestions.isNotEmpty()) {
                    SectionHeading("Were you looking for one of these?")
                    Ul(Modifier.margin(top = 0.px, bottom = 1.2.cssRem).padding(left = 1.2.cssRem).fontSize(10.px).lineHeight(2.2).toAttrs()) {
                        suggestions.forEach { (route, title) -> Li { Link(route, title) } }
                    }
                }
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).toAttrs()) {
                    LinkButton("Go to the packages", "/")
                    GhostButton("Search the site") { Palette.open() }
                    GhostButton("Go back") { window.history.back() }
                }
                HintText("Tip: press ${org.example.app.components.Platform.commandKey} K on any page to search packages, authors and docs.", Modifier.margin(top = 1.cssRem))
            }
        }
    }
}
