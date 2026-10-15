package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.example.app.auth.AuthClient
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

/** Downloads and likes of one package, as `/api/stats?username=` lists them. */
@Serializable
data class PackageStats(val path: String = "", val downloads: Long = 0, val likes: Long = 0)

private val statsJson = Json { ignoreUnknownKeys = true }

/** The stats of an author's packages, by path; packages nobody touched are absent. Cached half a minute. */
suspend fun loadStats(username: String, refresh: Boolean = false): Map<String, PackageStats> {
    if (username.isEmpty()) return emptyMap()
    return try {
        if (refresh) ClientCache.remove("stats:$username")
        val text = ClientCache.cached("stats:$username", ClientCache.MINUTE / 2) {
            val result = Http.get(AmieConfig.api("stats?username=${encodeURIComponent(username)}" + (if (refresh) "&refresh=1" else "")), noStore = true)
            if (!result.ok) throw IllegalStateException()
            result.text
        }
        statsJson.decodeFromString(ListSerializer(PackageStats.serializer()), text).associateBy { it.path }
    } catch (e: Throwable) { emptyMap() }
}

/** The paths the signed-in person liked; empty when signed out or on failure. */
suspend fun loadMyLikes(): Set<String> {
    val token = AuthClient.accessToken() ?: return emptySet()
    return try {
        val result = Http.get(AmieConfig.api("likes"), Http.bearer(token), noStore = true)
        if (!result.ok) emptySet() else statsJson.decodeFromString(ListSerializer(String.serializer()), result.text).toSet()
    } catch (e: Throwable) { emptySet() }
}

/** Counts a download; fire and forget, so the file starts without waiting. */
fun trackDownload(path: String) {
    runCatching {
        val url = AmieConfig.api("track-download?package=${encodeURIComponent(path)}")
        if (!window.navigator.asDynamic().sendBeacon(url, "").unsafeCast<Boolean>()) {
            window.fetch(url, org.w3c.fetch.RequestInit(method = "POST"))
        }
    }
}

/** "↓ 12 · ♥ 3": the counts shown after a package's size. */
fun statsLabel(stats: PackageStats?): String? {
    if (stats == null || (stats.downloads == 0L && stats.likes == 0L)) return null
    return listOfNotNull(stats.downloads.takeIf { it > 0 }?.let { plural(it.toInt(), "download") }, stats.likes.takeIf { it > 0 }?.let { plural(it.toInt(), "like") }).joinToString(" · ")
}

/**
 * The like (save) toggle for a package: a plain button that reads "Like · 3" or "Liked · 4". Signed-out
 * visitors are sent to sign in. [onChange] gets the new count and state so the page can resort.
 */
@Composable
fun LikeButton(path: String, likes: Long, liked: Boolean, signedIn: Boolean, onChange: (Boolean, Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    PlainButton((if (liked) "Liked" else "Like") + (if (likes > 0) " · $likes" else ""), enabled = !busy) {
        if (!signedIn) { window.location.href = "/loginpage?next=${encodeURIComponent(window.location.pathname + window.location.search)}"; return@PlainButton }
        scope.launch {
            busy = true
            val result = runCatching { Http.postForm(AmieConfig.api("like?package=${encodeURIComponent(path)}"), emptyMap(), Http.bearer(AuthClient.accessToken())) }.getOrNull()
            if (result?.ok == true) {
                val obj = runCatching { statsJson.parseToJsonElement(result.text) as kotlinx.serialization.json.JsonObject }.getOrNull()
                val nowLiked = obj?.get("liked")?.toString() == "true"
                val count = obj?.get("likes")?.toString()?.toLongOrNull() ?: likes
                onChange(nowLiked, count)
            } else Toasts.show(if (result?.status == 401) "Sign in to like packages." else "That could not be saved right now.", BannerKind.ERROR)
            busy = false
        }
    }
}

/** The address of an approved banner; the version keeps browsers from showing an old one. */
fun backgroundUrl(username: String, version: String): String =
    AmieConfig.api("background?username=${encodeURIComponent(username)}" + (if (version.isNotEmpty()) "&v=$version" else ""))

/**
 * The header of a person's page: their approved background photo behind the content when they have one,
 * their colour otherwise. The content sits on a dark gradient so it stays readable on any photo.
 */
@Composable
fun ProfileBanner(profile: PublicProfile, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tone = SiteTones.ofName(profile.username)
    if (!profile.hasBackground) {
        ToneBand(tone, Modifier.padding(topBottom = 1.2.cssRem, leftRight = 1.2.cssRem).margin(bottom = 1.8.cssRem).then(modifier)) { content() }
        return
    }
    Div(
        Modifier.fillMaxWidth().minHeight(11.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.FlexEnd).margin(bottom = 1.8.cssRem)
            .styleModifier {
                property("background-image", "linear-gradient(rgba(0,0,0,0.15), rgba(0,0,0,0.8)), url(\"${backgroundUrl(profile.username, profile.backgroundVersion)}\")")
                property("background-size", "cover"); property("background-position", "center")
                property("border-bottom", "3px solid rgb(${tone.r}, ${tone.g}, ${tone.b})")
            }
            .then(modifier).toAttrs { attr("role", "img"); attr("aria-label", "${profile.displayName}'s background") }
    ) {
        Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.SpaceBetween).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).padding(topBottom = 1.2.cssRem, leftRight = 1.2.cssRem).toAttrs()) { content() }
    }
}

/** The owner's or an admin's preview of a background still waiting for approval (fetched with the bearer). */
@Composable
fun PendingBackgroundPreview(username: String, modifier: Modifier = Modifier) {
    var objectUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        try {
            val token = AuthClient.accessToken()
            val headers = js("{}"); headers["Authorization"] = "Bearer $token"
            val response = window.fetch(AmieConfig.api("background-preview?username=${encodeURIComponent(username)}"), org.w3c.fetch.RequestInit(method = "GET", headers = headers)).await()
            if (response.ok) objectUrl = org.w3c.dom.url.URL.createObjectURL(response.blob().await())
        } catch (e: Throwable) { objectUrl = null }
    }
    DisposableEffect(objectUrl) { val url = objectUrl; onDispose { url?.let { runCatching { org.w3c.dom.url.URL.revokeObjectURL(it) } } } }
    val url = objectUrl ?: return
    Img(src = url, alt = "Background awaiting approval", attrs = Modifier.fillMaxWidth().maxHeight(12.cssRem).styleModifier { property("object-fit", "cover") }.border(2.px, LineStyle.Dashed, Color.rgb(0xD64545)).then(modifier).toAttrs())
}
