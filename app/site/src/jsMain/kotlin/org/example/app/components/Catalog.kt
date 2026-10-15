package org.example.app.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.jwtJson
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent

/** One file in the package store, with the author it belongs to. */
@Serializable
data class CatalogEntry(
    val name: String,
    val path: String = "",
    val author: String = "",
    val size: Double = 0.0,
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val sha: String? = null,
) {
    val kind: PackageKind get() = PackageKind.of(name)

    /** This site's page for the file */
    val viewUrl: String
        get() = "/view?package=${encodeURIComponent(path)}&username=${encodeURIComponent(author)}&url=${encodeURIComponent(downloadUrl)}"
}

/** The sort of file a package is, from its extension; decides the icon and the filter chips. */
enum class PackageKind(val label: String, val plural: String, val icon: String, val iconFile: String?, val extensions: Set<String>) {
    JAR("Jar", "Jars", "☕", null, setOf("jar")),
    MODEL("Model", "Models", "◉", null, setOf("onnx", "bin", "pt", "pth", "tflite", "safetensors")),
    JSON("JSON", "JSON files", "{}", "/json.svg", setOf("json")),
    PYTHON("Python", "Python files", "py", "/python.svg", setOf("py")),
    TEXT("Text", "Text files", "¶", null, setOf("md", "txt", "csv", "yaml", "yml", "kt", "kts", "ino", "c", "h", "cpp", "js", "ts", "sh", "xml", "html", "css")),
    OTHER("File", "Other files", "▣", null, emptySet());

    companion object {
        fun of(name: String): PackageKind {
            val extension = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { extension in it.extensions } ?: OTHER
        }
    }
}

/**
 * Every package in the store as one flat list, from this site's `/api/all-packages` route (which merges the
 * authors' folders on the server and caches the answer). Cached in the browser for a minute; the last good
 * answer is kept for showing something while a fresh one loads or when the store is unreachable.
 */
object Catalog {
    const val KEY = "packages:flat"

    fun parse(text: String): List<CatalogEntry> =
        runCatching { jwtJson.decodeFromString(ListSerializer(CatalogEntry.serializer()), text) }.getOrDefault(emptyList())

    suspend fun load(refresh: Boolean = false): List<CatalogEntry> {
        val text = ClientCache.cached(KEY, ClientCache.MINUTE, refresh) {
            val result = Http.get(AmieConfig.api("all-packages" + if (refresh) "?refresh=1" else ""))
            if (!result.ok) throw IllegalStateException("catalog unavailable")
            result.text
        }
        return parse(text)
    }

    /** What was last loaded, fresh or not; empty when nothing was ever loaded. */
    fun stale(): List<CatalogEntry> = ClientCache.getStale(KEY)?.let { parse(it) }.orEmpty()

    fun forget() {
        ClientCache.removePrefix("packages:")
    }
}
