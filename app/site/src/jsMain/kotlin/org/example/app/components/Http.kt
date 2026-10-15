package org.example.app.components

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.example.app.config.encodeURIComponent
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

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

    /**
     * POSTs a form with the upload progress reported as a fraction (0..1), which `fetch` cannot do. A network
     * failure answers status 0.
     */
    suspend fun upload(url: String, form: FormData, headers: Map<String, String> = emptyMap(), onProgress: (Double) -> Unit): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val xhr = XMLHttpRequest()
            xhr.open("POST", url)
            headers.forEach { (name, value) -> xhr.setRequestHeader(name, value) }
            xhr.upload.onprogress = { event ->
                if (event.lengthComputable && event.total.toDouble() > 0) onProgress(event.loaded.toDouble() / event.total.toDouble())
            }
            xhr.onload = { if (continuation.isActive) continuation.resume(HttpResult(xhr.status.toInt(), xhr.responseText)) }
            xhr.onerror = { if (continuation.isActive) continuation.resume(HttpResult(0, "")) }
            xhr.onabort = { if (continuation.isActive) continuation.resume(HttpResult(0, "")) }
            continuation.invokeOnCancellation { runCatching { xhr.abort() } }
            xhr.send(form)
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
