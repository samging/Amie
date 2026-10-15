package org.example.app.components.widgets

import androidx.compose.runtime.*
import kotlinx.coroutines.await
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

/** The public part of a profile (`/api/profile?username=`): what to call the person and whether they have a picture. */
@Serializable
data class PublicProfile(
    val username: String = "",
    val fullName: String = "",
    val nickname: String = "",
    val hasAvatar: Boolean = false,
    val avatarVersion: String = "",
    val avatarPending: Boolean = false,
    val github: String = "",
    val huggingFace: String = "",
    val badges: List<String> = emptyList(),
) {
    val displayName: String get() = nickname.ifBlank { username }
}

private val profileJson = Json { ignoreUnknownKeys = true }

/** Loads a public profile, cached for five minutes; a profile the backend does not have is an empty one. */
suspend fun loadPublicProfile(username: String, refresh: Boolean = false): PublicProfile {
    if (username.isEmpty()) return PublicProfile()
    return try {
        if (refresh) ClientCache.remove("profile:$username")
        val text = ClientCache.cached("profile:$username", 5 * ClientCache.MINUTE) {
            val result = Http.get(AmieConfig.api("profile?username=${encodeURIComponent(username)}"), noStore = true)
            if (!result.ok) throw IllegalStateException()
            result.text
        }
        profileJson.decodeFromString(PublicProfile.serializer(), text).let { if (it.username.isEmpty()) it.copy(username = username) else it }
    } catch (e: Throwable) {
        PublicProfile(username = username)
    }
}

/** The address of a person's picture; the version keeps browsers from showing an old one after a change. */
fun avatarUrl(username: String, version: String): String =
    AmieConfig.api("avatar?username=${encodeURIComponent(username)}" + (if (version.isNotEmpty()) "&v=$version" else ""))

/**
 * The signed-in person's own picture while it waits for approval: fetched with the bearer from
 * `/api/avatar-preview` as a blob, so no token ever sits in an image URL.
 */
@Composable
fun PendingAvatarPreview(username: String, size: CSSLengthValue = 2.5.cssRem) {
    var objectUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        try {
            val token = org.example.app.auth.AuthClient.accessToken()
            val init = org.w3c.fetch.RequestInit(method = "GET", headers = js("{}").also { h -> h["Authorization"] = "Bearer $token" })
            val response = kotlinx.browser.window.fetch(AmieConfig.api("avatar-preview?username=${encodeURIComponent(username)}"), init).await()
            if (response.ok) objectUrl = org.w3c.dom.url.URL.createObjectURL(response.blob().await())
        } catch (e: Throwable) { objectUrl = null }
    }
    DisposableEffect(objectUrl) { onDispose { objectUrl?.let { runCatching { org.w3c.dom.url.URL.revokeObjectURL(it) } } } }
    val url = objectUrl
    if (url == null) Avatar(PublicProfile(username = username), size)
    else Img(src = url, alt = "Your picture, awaiting approval", attrs = Modifier.size(size).flexShrink(0).border(2.px, LineStyle.Dashed, Colors.White).objectFit(ObjectFit.Cover).toAttrs { attr("title", "Awaiting approval") })
}

fun initialsOf(name: String, username: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> username.take(2).uppercase()
    }
}

/**
 * A person's picture, or their initials when they have none. The picture is an `<img>` served by this
 * site with `nosniff`; the alt text is the display name. Never renders anything else from the profile.
 */
@Composable
fun Avatar(profile: PublicProfile, size: CSSLengthValue = 2.5.cssRem, modifier: Modifier = Modifier) {
    val base = Modifier.size(size).flexShrink(0).border(2.px, LineStyle.Solid, Colors.White).then(modifier)
    if (profile.hasAvatar) {
        Img(
            src = avatarUrl(profile.username, profile.avatarVersion),
            alt = profile.displayName,
            attrs = base.objectFit(ObjectFit.Cover).backgroundColor(Colors.Black).toAttrs { attr("loading", "lazy"); attr("referrerpolicy", "no-referrer") }
        )
    } else {
        Div(
            base.display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center)
                .backgroundColor(Colors.White).color(Colors.Black).fontSize(11.px).toAttrs { attr("aria-hidden", "true") }
        ) { Text(initialsOf(profile.fullName.ifBlank { profile.nickname }, profile.username)) }
    }
}
