package org.example.awss3.jar

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.core.io.InputStreamResource
import org.springframework.util.DigestUtils
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/**
 * Jar downloads for the website: a cached listing and the jar bytes themselves. Every answer carries an
 * `ETag` and a `Cache-Control` so browsers and the website's own server can revalidate instead of
 * re-downloading; a matching `If-None-Match` gets `304 Not Modified`.
 */
@RestController
@RequestMapping("/api/aws/jars")
class JarController(
    private val jarService: JarService,
    @Value("\${amie.jars.listing-ttl-seconds:60}") private val listingTtlSeconds: Long,
    @Value("\${amie.jars.content-ttl-seconds:3600}") private val contentTtlSeconds: Long
) {
    @GetMapping
    fun list(@RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?): ResponseEntity<List<JarDto>> {
        val jars = jarService.listJars()
        val eTag = listingETag(jars)
        val cacheControl = CacheControl.maxAge(listingTtlSeconds, TimeUnit.SECONDS).cachePublic()
        if (matches(ifNoneMatch, eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).cacheControl(cacheControl).build()
        }
        return ResponseEntity.ok().eTag(eTag).cacheControl(cacheControl).body(jars)
    }

    @GetMapping("/{name:.+}")
    fun download(
        @PathVariable name: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<Any> {
        if (!JarService.RELEASE_NAME.matches(name)) return ResponseEntity.badRequest().build()
        val info = jarService.findJar(name) ?: return ResponseEntity.notFound().build()
        val cacheControl = CacheControl.maxAge(contentTtlSeconds, TimeUnit.SECONDS).cachePublic()
        val contentType = MediaType.parseMediaType(JarService.contentTypeOf(info.name))

        if (info.size > jarService.maxCachedItemBytes) {
            // Large application builds are streamed from S3 rather than held in memory
            val eTag = info.eTag?.takeIf { it.startsWith("\"") } ?: "\"${info.key.hashCode()}\""
            if (matches(ifNoneMatch, eTag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).cacheControl(cacheControl).build()
            }
            val stream = jarService.openJar(name) ?: return ResponseEntity.notFound().build()
            return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(cacheControl)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${info.name}\"")
                .contentType(contentType)
                .contentLength(stream.response().contentLength() ?: info.size)
                .body(InputStreamResource(stream))
        }

        val jar = jarService.loadJar(name) ?: return ResponseEntity.notFound().build()
        val eTag = jar.eTag?.takeIf { it.startsWith("\"") } ?: "\"${DigestUtils.md5DigestAsHex(jar.bytes)}\""
        if (matches(ifNoneMatch, eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).cacheControl(cacheControl).build()
        }
        val builder = ResponseEntity.ok()
            .eTag(eTag)
            .cacheControl(cacheControl)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${jar.name}\"")
            .contentType(contentType)
            .contentLength(jar.bytes.size.toLong())
        jar.lastModified?.let { builder.lastModified(it) }
        return builder.body(jar.bytes)
    }

    /** Removes a package jar or an application build. */
    @DeleteMapping("/{name:.+}")
    fun delete(@PathVariable name: String): ResponseEntity<Void> {
        if (!JarService.RELEASE_NAME.matches(name)) return ResponseEntity.badRequest().build()
        return if (jarService.deleteJar(name)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** Stores an application build (jar, dmg, msi, deb, zip, exe) for the downloads page. */
    @PostMapping("/releases", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun storeRelease(@RequestParam fileName: String, @RequestBody bytes: ByteArray): ResponseEntity<Any> {
        return try {
            val stored = jarService.storeRelease(fileName, bytes)
            ResponseEntity.ok(mapOf("name" to stored, "kind" to JarDto.KIND_APPLICATION))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(e.message ?: "Invalid release")
        }
    }

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<Void> {
        jarService.invalidate()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/cache")
    fun cache(): JarCacheInfo = jarService.cacheInfo()

    companion object {
        val JAR_TYPE: MediaType = MediaType("application", "java-archive")

        fun listingETag(jars: List<JarDto>): String {
            val fingerprint = jars.joinToString("\n") { "${it.key}|${it.eTag}|${it.size}" }
            return "\"${DigestUtils.md5DigestAsHex(fingerprint.toByteArray())}\""
        }

        fun matches(ifNoneMatch: String?, eTag: String): Boolean {
            if (ifNoneMatch.isNullOrBlank()) return false
            val wanted = eTag.removePrefix("W/")
            return ifNoneMatch.split(',').map { it.trim() }.any { it == "*" || it.removePrefix("W/") == wanted }
        }
    }
}
