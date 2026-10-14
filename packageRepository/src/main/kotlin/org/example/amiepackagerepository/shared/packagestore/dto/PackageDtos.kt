package org.example.amiepackagerepository.shared.packagestore.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

/**
 * One entry directly below `uploads/` in the package store: an author folder (`type = "dir"`) or a loose
 * file. The field names are what the desktop app's `FileItem` and the website's packages page read.
 */
data class PackageItemDto(
    override val name: String,
    override val downloadUrl: String? = null,
    /** The object's ETag; null for a folder. */
    override val id: String? = null,
    val type: String = "file",
    val path: String = "",
    val size: Long? = null
) : RepositoryInterface

/**
 * One file an author published (`uploads/<user>/<lang>/<file>`). The JSON names (`download_url`,
 * `html_url`, `sha`) are kept from the previous store so the desktop app and the website keep working.
 */
data class PackageFileDto(
    val name: String,
    val path: String,
    /** The object's ETag. */
    val sha: String? = null,
    val size: Long = 0,
    /** Same as [downloadUrl]. */
    val url: String? = null,
    @get:JsonProperty("html_url") val htmlUrl: String? = null,
    @get:JsonProperty("download_url") val downloadUrl: String? = null,
    val type: String = "file",
    /** Base64 content, only when a single file was asked for and it is small enough. */
    val content: String? = null,
    val encoding: String? = null
)

/** Legacy metadata shape of `/list-github-metadata`. */
data class PackageItemMetadataDto(
    override val name: String,
    override val downloadUrl: String? = null,
    override val id: String? = null,
    val type: String = "file",
    val endComp: String? = null,
) : RepositoryInterface

/** One line of a user's error log. */
data class ErrorEntryDto(val time: String = "", val error: String = "", val message: String = "")

/** An error reported by a signed-in client (`POST /errors`). */
data class ErrorReportDto(val error: String = "", val message: String = "")

/** What the store knows about an object without its bytes. */
data class StoredObjectMeta(
    val name: String,
    val path: String,
    val size: Long,
    @JsonProperty("eTag") val eTag: String?,
    val contentType: String?,
    val downloadUrl: String
)

/** An object read back from the store. */
class StoredObject(val key: String, val bytes: ByteArray, val contentType: String?, val eTag: String?)
