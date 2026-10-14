package org.example.awss3.jar

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One downloadable file in the bucket: a package jar, or an application build under the releases prefix. */
data class JarDto(
    val name: String,
    val key: String,
    val size: Long,
    val lastModified: String?,
    val eTag: String?,
    val version: String?,
    /** `package` for uploaded package jars, `application` for Amie app builds (jar, dmg, msi, deb, zip). */
    val kind: String = KIND_PACKAGE
) {
    companion object {
        const val KIND_PACKAGE = "package"
        const val KIND_APPLICATION = "application"
    }
}

/** A jar's bytes together with the S3 metadata that drives HTTP caching. */
class CachedJar(
    val name: String,
    val key: String,
    val bytes: ByteArray,
    val eTag: String?,
    val lastModified: Instant?
)

data class JarCacheInfo(val listingCached: Boolean, val cachedJars: Long, val cachedBytes: Long)

/**
 * Lists and serves the jar files stored in the bucket, caching both the listing and the jar bytes so
 * S3 is only asked again when an entry expires, a jar is written, or someone asks for a refresh.
 */
@Service
class JarService(
    private val s3Client: S3Client,
    private val bucketName: String,
    private val prefix: String,
    private val releasesPrefix: String,
    listingTtlSeconds: Long,
    contentTtlSeconds: Long,
    maxCacheBytes: Long,
    ticker: Ticker
) {
    @Autowired
    constructor(
        s3Client: S3Client,
        @Value("\${aws.s3.bucket-name}") bucketName: String,
        @Value("\${amie.jars.prefix:}") prefix: String,
        @Value("\${amie.jars.releases-prefix:releases/}") releasesPrefix: String,
        @Value("\${amie.jars.listing-ttl-seconds:60}") listingTtlSeconds: Long,
        @Value("\${amie.jars.content-ttl-seconds:3600}") contentTtlSeconds: Long,
        @Value("\${amie.jars.max-cache-bytes:268435456}") maxCacheBytes: Long
    ) : this(s3Client, bucketName, prefix, releasesPrefix, listingTtlSeconds, contentTtlSeconds, maxCacheBytes, Ticker.systemTicker())

    private val logger = LoggerFactory.getLogger(JarService::class.java)

    /** Objects above this size are streamed straight from S3 instead of being cached in memory. */
    val maxCachedItemBytes: Long = maxCacheBytes / 4

    private val listing: Cache<String, List<JarDto>> = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(listingTtlSeconds, TimeUnit.SECONDS)
        .build()

    private val content: Cache<String, CachedJar> = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(contentTtlSeconds, TimeUnit.SECONDS)
        .maximumWeight(maxCacheBytes)
        .weigher<String, CachedJar> { _, jar -> jar.bytes.size }
        .build()

    /** Every package jar and application build in the bucket, newest first; names are unique (the newest wins). */
    fun listJars(): List<JarDto> = listing.get(LISTING_KEY) { fetchListing() } ?: emptyList()

    fun findJar(name: String): JarDto? = listJars().firstOrNull { it.name == name }

    /** The jar's bytes, from the cache when possible; null when no jar of that name exists. */
    fun loadJar(name: String): CachedJar? {
        val jar = findJar(name) ?: return null
        return content.get(jar.key) { key -> fetchContent(jar.name, key) }
    }

    /** Forgets everything cached so the next request reads the bucket again. */
    fun invalidate() {
        listing.invalidateAll()
        content.invalidateAll()
        logger.info("Jar caches cleared")
    }

    /** A stream of the object behind [name] for files too large to cache; the caller closes it. */
    fun openJar(name: String): ResponseInputStream<GetObjectResponse>? {
        val jar = findJar(name) ?: return null
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(jar.key).build())
    }

    /** Removes the object behind [name]; false when there is none. */
    fun deleteJar(name: String): Boolean {
        val jar = findJar(name) ?: return false
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(jar.key).build())
        logger.info("Deleted {} from bucket {}", jar.key, bucketName)
        invalidate()
        return true
    }

    /**
     * Stores an application build under the releases prefix. Any whitespace in the name becomes a dash
     * so the file can be addressed in a URL. Returns the stored name.
     */
    fun storeRelease(fileName: String, bytes: ByteArray): String {
        val name = fileName.trim().replace(Regex("""\s+"""), "-")
        require(RELEASE_NAME.matches(name)) { "Release names may only contain letters, digits, dots, dashes and underscores, and must end in .jar, .dmg, .msi, .deb, .zip or .exe" }
        require(bytes.isNotEmpty()) { "The release file is empty" }
        if (name.endsWith(".jar", true) || name.endsWith(".zip", true)) {
            require(bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) { "The file is not a valid archive" }
        }
        val key = releasesPrefix + name
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentTypeOf(name)).build(),
            RequestBody.fromBytes(bytes)
        )
        logger.info("Stored release {} ({} bytes)", key, bytes.size)
        invalidate()
        return name
    }

    fun cacheInfo(): JarCacheInfo {
        val jars = content.asMap().values
        return JarCacheInfo(
            listingCached = listing.getIfPresent(LISTING_KEY) != null,
            cachedJars = jars.size.toLong(),
            cachedBytes = jars.sumOf { it.bytes.size.toLong() }
        )
    }

    private fun fetchListing(): List<JarDto> {
        logger.info("Listing jars in bucket {} under prefix '{}'", bucketName, prefix)
        val request = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build()
        return s3Client.listObjectsV2Paginator(request).contents().asSequence()
            .filter { !it.key().endsWith("/") }
            .filter { it.key().startsWith(releasesPrefix) || it.key().endsWith(".jar", ignoreCase = true) }
            .map { s3Object ->
                val name = s3Object.key().substringAfterLast('/')
                JarDto(
                    name = name,
                    key = s3Object.key(),
                    size = s3Object.size() ?: 0L,
                    lastModified = s3Object.lastModified()?.toString(),
                    eTag = s3Object.eTag(),
                    version = versionOf(name),
                    kind = if (s3Object.key().startsWith(releasesPrefix)) JarDto.KIND_APPLICATION else JarDto.KIND_PACKAGE
                )
            }
            .sortedWith(compareByDescending<JarDto> { it.lastModified ?: "" }.thenBy { it.name })
            .distinctBy { it.name }
            .toList()
            .also { logger.info("Found {} jar(s)", it.size) }
    }

    private fun fetchContent(name: String, key: String): CachedJar {
        logger.info("Fetching jar {} from bucket {}", key, bucketName)
        val response = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build())
        return CachedJar(
            name = name,
            key = key,
            bytes = response.asByteArray(),
            eTag = response.response().eTag(),
            lastModified = response.response().lastModified()
        )
    }

    companion object {
        private const val LISTING_KEY = "all"
        private val VERSION = Regex("""(\d+(?:\.\d+)+(?:[-+][\w.]+)?)""")
        val RELEASE_NAME = Regex("""[A-Za-z0-9._-]+\.(?i:jar|dmg|msi|deb|zip|exe)""")

        fun contentTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jar" -> "application/java-archive"
            "dmg" -> "application/x-apple-diskimage"
            "msi" -> "application/x-msi"
            "deb" -> "application/vnd.debian.binary-package"
            "zip" -> "application/zip"
            "exe" -> "application/vnd.microsoft.portable-executable"
            else -> "application/octet-stream"
        }

        /** `amie-1.2.3.jar` → `1.2.3`; null when the name carries no version. */
        fun versionOf(name: String): String? = VERSION.find(name.substringBeforeLast('.'))?.value
    }
}
