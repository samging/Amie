package org.example.amiepackagerepository.shared.packagestore

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.json.Json
import org.example.amiepackagerepository.shared.packagestore.dto.ErrorEntryDto
import org.example.amiepackagerepository.shared.packagestore.dto.PackageFileDto
import org.example.amiepackagerepository.shared.packagestore.dto.PackageItemDto
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObject
import org.example.amiepackagerepository.shared.packagestore.dto.StoredObjectMeta
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture

/** The store answered with an error, or could not be reached ([status] is null then). */
class PackageStoreException(message: String, val status: Int? = null, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The package store: the S3 bucket behind the Amie S3 service (`awsS3`, `amie.s3.service-url`). This
 * backend holds no AWS credentials; everything goes through that service's `/api/aws` routes.
 *
 * Layout in the bucket, all below `uploads/`: `<user>/README.md` (dashboard marker),
 * `<user>/<lang>/<file>` (published package files), `<user>/state/<name>.json` (a user's saved states,
 * the devices among them) and `<user>/state/errors.jsonl` (the user's error log).
 */
@Service
class PackageStoreClient(
    @Value("\${amie.s3.service-url:http://localhost:8088}") serviceUrl: String,
    @Value("\${amie.site-url:http://localhost:8081}") siteUrl: String,
    restClientBuilder: RestClient.Builder
) {
    private val log = LoggerFactory.getLogger(PackageStoreClient::class.java)
    private val baseUrl = serviceUrl.trimEnd('/')
    private val siteUrl = siteUrl.trimEnd('/')
    // Timeouts come from the auto-configured builder (spring.http.client.* in application.properties)
    private val restClient: RestClient = restClientBuilder.baseUrl(baseUrl).build()

    companion object {
        const val UPLOADS = "uploads/"
        const val STATE_FOLDER = "state/"
        private val STATE_NAME = Regex("""[A-Za-z0-9._-]{1,64}""")

        /** A key below `uploads/` that cannot escape it. */
        fun isAllowedKey(key: String): Boolean =
            key.startsWith(UPLOADS) && key.length > UPLOADS.length && !key.endsWith("/") &&
                key.split('/').none { it.isEmpty() || it == "." || it == ".." }

        /** The author of a key: `uploads/<user>/...` -> `<user>`. */
        fun ownerOf(key: String): String? = key.removePrefix(UPLOADS).substringBefore('/', "").takeIf { it.isNotEmpty() && isAllowedKey(key) }

        fun isValidStateName(name: String): Boolean = STATE_NAME.matches(name)

        fun stateKey(username: String, name: String) = "$UPLOADS$username/$STATE_FOLDER$name.json"

        fun packageKey(username: String, language: String, fileName: String): String =
            listOf(UPLOADS.trimEnd('/'), username.trim(), language.trim(), fileName.trim()).filter { it.isNotEmpty() }.joinToString("/")

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
    }

    /** The website page that shows one stored file. */
    fun viewUrl(key: String): String = "$siteUrl/view?package=${encode(key)}"

    // ---- listings ----------------------------------------------------------------------------------

    /** Everything directly below `uploads/`: one `dir` per author, plus loose files. */
    fun listRoot(): List<PackageItemDto> = call("list the store") {
        val items = restClient.get().uri("/api/aws/files/root").retrieve()
            .body(object : ParameterizedTypeReference<List<S3Item>>() {}) ?: emptyList()
        items.map { PackageItemDto(it.name, it.downloadUrl.takeIf { url -> url.isNotBlank() }, it.eTag, it.type, it.path, it.size) }
    }

    /** The files an author published, with the names the website and the desktop app read. */
    fun listUserPackages(username: String): List<PackageFileDto> {
        if (username.isBlank()) return emptyList()
        return call("list $username's packages") {
            val items = restClient.get().uri("/api/aws/users/{u}/packages", username).retrieve()
                .body(object : ParameterizedTypeReference<List<S3Item>>() {}) ?: emptyList()
            items.filter { it.type == "file" }.map { it.toPackageFile() }
        }
    }

    /** The store's search: `*ext` by extension, anything else an exact `uploads/<name>` lookup; null when nothing matched. */
    fun query(query: String): Any? = try {
        restClient.get().uri("/api/aws/files?query={q}", query).retrieve().body(Any::class.java)
    } catch (e: HttpClientErrorException.NotFound) {
        null
    } catch (e: Exception) {
        log.warn("Package search '{}' failed: {}", query, e.message)
        null
    }

    // ---- package files -----------------------------------------------------------------------------

    /** Stores a published file at `uploads/<user>/<lang>/<file>`. Throws [PackageStoreException] when the store refuses. */
    fun upload(username: String, language: String, fileName: String, bytes: ByteArray, contentType: String? = null) {
        require(username.isNotBlank()) { "Username must not be blank" }
        val relative = listOf(language.trim(), fileName.trim()).filter { it.isNotEmpty() }.joinToString("/")
        require(isAllowedKey("$UPLOADS$username/$relative")) { "Invalid file name" }
        call("store $fileName") {
            restClient.post()
                .uri { b -> b.path("/api/aws/upload-data").queryParam("username", username).queryParam("fileName", relative).apply { if (!contentType.isNullOrBlank()) queryParam("contentType", contentType) }.build() }
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes)
                .retrieve()
                .toBodilessEntity()
        }
        log.info("Stored {} for {} in the package store", relative, username)
    }

    /** Replaces an existing file below `uploads/<user>/`; the store answers 500 when it is not there. */
    fun edit(username: String, fileName: String, bytes: ByteArray, contentType: String?) {
        val form = LinkedMultiValueMap<String, Any>()
        form.add("username", username)
        form.add("fileName", fileName)
        form.add("file", object : ByteArrayResource(bytes) {
            override fun getFilename(): String = fileName.substringAfterLast('/')
        })
        call("replace $fileName") {
            restClient.post().uri("/api/aws/edit")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toBodilessEntity()
        }
    }

    /** Metadata of one object, or null when there is none. */
    fun head(key: String): StoredObjectMeta? {
        require(isAllowedKey(key)) { "Invalid key" }
        return try {
            restClient.get().uri("/api/aws/files/meta?key={k}", key).retrieve().body(StoredObjectMeta::class.java)
        } catch (e: HttpClientErrorException.NotFound) {
            null
        } catch (e: Exception) {
            throw storeFailure("read $key", e)
        }
    }

    /** One object with its bytes, or null when there is none. */
    fun get(key: String): StoredObject? {
        require(isAllowedKey(key)) { "Invalid key" }
        return try {
            val entity = restClient.get().uri("/api/aws/files/content?key={k}", key).retrieve().toEntity(ByteArray::class.java)
            StoredObject(key, entity.body ?: ByteArray(0), entity.headers.contentType?.toString(), entity.headers.eTag)
        } catch (e: HttpClientErrorException.NotFound) {
            null
        } catch (e: Exception) {
            throw storeFailure("read $key", e)
        }
    }

    /** Writes [bytes] at [key]. */
    fun put(key: String, bytes: ByteArray, contentType: String? = null) {
        require(isAllowedKey(key)) { "Invalid key" }
        call("write $key") {
            restClient.put()
                .uri { b -> b.path("/api/aws/files/content").queryParam("key", key).apply { if (!contentType.isNullOrBlank()) queryParam("contentType", contentType) }.build() }
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes)
                .retrieve()
                .toBodilessEntity()
        }
    }

    /** Removes [key]; false when it was not there. */
    fun delete(key: String): Boolean {
        require(isAllowedKey(key)) { "Invalid key" }
        return try {
            restClient.delete().uri("/api/aws/files/content?key={k}", key).retrieve().toBodilessEntity()
            true
        } catch (e: HttpClientErrorException.NotFound) {
            false
        } catch (e: Exception) {
            throw storeFailure("delete $key", e)
        }
    }

    // ---- dashboards, endpoints, errors, states -----------------------------------------------------

    /** Creates `uploads/<user>/README.md` when it is missing; best effort, in the background. */
    fun createUserDashboard(username: String): CompletableFuture<Void> {
        if (username.isBlank()) return CompletableFuture.completedFuture(null)
        return CompletableFuture.runAsync {
            try {
                restClient.post().uri("/api/aws/dashboard?username={u}", username).retrieve().toBodilessEntity()
                log.info("Dashboard ready for {}", username)
            } catch (e: Exception) {
                log.warn("Dashboard for {} could not be created: {}", username, e.message)
            }
        }
    }

    fun fetchEndpoints(): Map<String, String> = try {
        restClient.get().uri("/api/aws/endpoints").retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {}) ?: emptyMap()
    } catch (e: Exception) {
        log.warn("Endpoints unavailable: {}", e.message)
        emptyMap()
    }

    fun postEndpoints() = call("publish the endpoints") {
        restClient.post().uri("/api/aws/endpoints").retrieve().toBodilessEntity()
    }

    /** Appends one line to the user's error log; best effort. */
    fun writeError(username: String, error: String, message: String) {
        if (username.isBlank()) return
        try {
            restClient.post()
                .uri { b -> b.path("/api/aws/errors").queryParam("username", username).queryParam("error", error.take(200)).queryParam("message", message.take(2000)).build() }
                .retrieve().toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Error for {} could not be logged to the store: {}", username, e.message)
        }
    }

    /** The user's error log, newest first. */
    fun listErrors(username: String): List<ErrorEntryDto> = call("read $username's errors") {
        restClient.get().uri("/api/aws/errors?username={u}", username).retrieve()
            .body(object : ParameterizedTypeReference<List<ErrorEntryDto>>() {}) ?: emptyList()
    }

    fun clearErrors(username: String) = call("clear $username's errors") {
        restClient.delete().uri("/api/aws/errors?username={u}", username).retrieve().toBodilessEntity()
    }

    /** The names of the user's saved states. */
    fun listStates(username: String): List<String> = call("list $username's states") {
        restClient.get().uri("/api/aws/users/{u}/states", username).retrieve()
            .body(object : ParameterizedTypeReference<List<String>>() {}) ?: emptyList()
    }

    /** A saved state as JSON text, or null when there is none. */
    fun loadState(username: String, name: String): String? {
        require(isValidStateName(name)) { "Invalid state name" }
        return get(stateKey(username, name))?.bytes?.toString(Charsets.UTF_8)
    }

    /** Stores a state; [json] must be valid JSON. */
    fun saveState(username: String, name: String, json: String) {
        require(isValidStateName(name)) { "Invalid state name" }
        val element = try { Json.parseToJsonElement(json) } catch (e: Exception) { throw IllegalArgumentException("The state is not valid JSON") }
        put(stateKey(username, name), element.toString().toByteArray(), MediaType.APPLICATION_JSON_VALUE)
    }

    fun deleteState(username: String, name: String): Boolean {
        require(isValidStateName(name)) { "Invalid state name" }
        return delete(stateKey(username, name))
    }

    // ---- plumbing ----------------------------------------------------------------------------------

    private fun <T> call(what: String, block: () -> T): T = try {
        block()
    } catch (e: PackageStoreException) {
        throw e
    } catch (e: Exception) {
        throw storeFailure(what, e)
    }

    private fun storeFailure(what: String, e: Exception): PackageStoreException = when (e) {
        is RestClientResponseException -> PackageStoreException(
            "The package store could not $what (${e.statusCode.value()}): ${e.responseBodyAsString.take(300)}", e.statusCode.value(), e
        )
        is ResourceAccessException -> PackageStoreException("The package store at $baseUrl is unreachable: ${e.message}", null, e)
        else -> PackageStoreException("The package store could not $what: ${e.message}", null, e)
    }

    /** The S3 service's listing item. */
    data class S3Item(
        val name: String = "",
        val path: String = "",
        val downloadUrl: String = "",
        @JsonProperty("eTag") val eTag: String? = null,
        val type: String = "file",
        val size: Long? = null
    )

    private fun S3Item.toPackageFile() = PackageFileDto(
        name = name,
        path = path,
        sha = eTag,
        size = size ?: 0,
        url = downloadUrl,
        htmlUrl = viewUrl(path),
        downloadUrl = downloadUrl,
        type = type
    )
}
