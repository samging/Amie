package org.example.app.components

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.example.app.config.encodeURIComponent
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

/** One HTTP answer: status and body text. */
class HttpResult(val status: Int, val text: String) {
    val ok: Boolean get() = status in 200..299
}

/** Small wrappers around `fetch` used by every page. */
object Http {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), noStore: Boolean = false): HttpResult {
        val init = RequestInit(method = "GET", headers = jsHeaders(headers))
        if (noStore) init.asDynamic().cache = "no-store"
        return window.fetch(url, init).await().toResult()
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResult {
        val init = RequestInit(method = "POST", body = json, headers = jsHeaders(headers + ("Content-Type" to "application/json")))
        return window.fetch(url, init).await().toResult()
    }

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResult {
        val body = form.entries.joinToString("&") { (k, v) -> encodeURIComponent(k) + "=" + encodeURIComponent(v) }
        val init = RequestInit(
            method = "POST",
            body = body,
            headers = jsHeaders(headers + ("Content-Type" to "application/x-www-form-urlencoded"))
        )
        return window.fetch(url, init).await().toResult()
    }

    suspend fun postBody(url: String, body: dynamic, headers: Map<String, String> = emptyMap()): HttpResult {
        val init = RequestInit(method = "POST", body = body, headers = jsHeaders(headers))
        return window.fetch(url, init).await().toResult()
    }

    fun bearer(token: String?): Map<String, String> =
        if (token.isNullOrEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $token")

    private fun jsHeaders(headers: Map<String, String>): dynamic {
        val obj = js("{}")
        headers.forEach { (k, v) -> obj[k] = v }
        return obj
    }

    private suspend fun Response.toResult(): HttpResult = HttpResult(status.toInt(), text().await())
}
