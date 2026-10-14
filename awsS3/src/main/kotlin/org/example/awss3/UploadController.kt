package org.example.awss3

import org.example.awss3.jar.JarInspector
import org.example.awss3.jar.JarService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * The package store's HTTP face, used by the Amie backend (which holds no AWS credentials) and the
 * website's admin routes. Objects are addressed by their key below `uploads/`; see [S3Service].
 */
@RestController
@RequestMapping("/api/aws")
class UploadController(
    private val s3Service: S3Service,
    private val jarInspector: JarInspector,
    private val jarService: JarService
) {

    /**
     * Rejects a `.jar` upload that is not a valid Amie package; other file types pass through untouched.
     * Returns the 400 answer to send, or null when the upload may proceed.
     */
    private fun rejectInvalidJar(fileName: String?, bytes: () -> ByteArray): ResponseEntity<String>? {
        if (!JarInspector.isJarName(fileName)) return null
        val inspection = jarInspector.inspect(fileName, bytes())
        if (inspection.valid) return null
        return ResponseEntity.badRequest().body(inspection.problems.joinToString("; "))
    }

    @PostMapping("/upload")
    suspend fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<String> {
        rejectInvalidJar(file.originalFilename) { file.bytes }?.let { return it }
        return try {
            val result = s3Service.uploadFile(file)
            jarService.invalidate()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            val message = e.message ?: "Unknown error occurred during upload"
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message)
        }
    }

    /** Stores raw bytes at `uploads/<username>/<fileName>`; `fileName` may carry one folder (`jar/echo.jar`). */
    @PostMapping("/upload-data", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    suspend fun uploadFileData(
        @RequestParam username: String,
        @RequestParam fileName: String,
        @RequestParam(required = false) contentType: String?,
        @RequestBody data: ByteArray
    ): ResponseEntity<String> {
        if (username.isBlank() || !S3Service.isAllowedFileName(fileName)) {
            return ResponseEntity.badRequest().body("Invalid username or file name")
        }
        rejectInvalidJar(fileName) { data }?.let { return it }
        return try {
            val result = s3Service.uploadFileData(username, fileName, data, contentType)
            jarService.invalidate()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            val message = e.message ?: "Unable to upload raw file data"
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message)
        }
    }

    @PostMapping("/edit")
    suspend fun editFile(
        @RequestParam(required = false, defaultValue = "") username: String,
        @RequestParam fileName: String,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<String> {
        rejectInvalidJar(fileName) { file.bytes }?.let { return it }
        return try {
            val result = s3Service.sendEdit(username, fileName, file)
            jarService.invalidate()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            val message = e.message ?: "Unable to update the S3 object"
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message)
        }
    }

    // ---- objects by key ----------------------------------------------------------------------------

    /** `GET /files/content?key=`: the object's bytes with its content type; 404 when missing. */
    @GetMapping("/files/content")
    suspend fun getObject(@RequestParam key: String): ResponseEntity<Any> {
        if (!S3Service.isAllowedKey(key)) return ResponseEntity.badRequest().body("Invalid key")
        val stored = s3Service.getObject(key) ?: return ResponseEntity.notFound().build()
        val builder = ResponseEntity.ok()
            .contentType(contentTypeOf(stored))
            .contentLength(stored.bytes.size.toLong())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${stored.name}\"")
        stored.eTag?.let { builder.eTag(it) }
        stored.lastModified?.let { builder.lastModified(it) }
        return builder.body(stored.bytes)
    }

    /** `GET /files/meta?key=`: name, size, ETag, content type and download URL of an object; 404 when missing. */
    @GetMapping("/files/meta")
    suspend fun headObject(@RequestParam key: String): ResponseEntity<Any> {
        if (!S3Service.isAllowedKey(key)) return ResponseEntity.badRequest().body("Invalid key")
        val meta = s3Service.headObject(key) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(meta)
    }

    /** `PUT /files/content?key=` with the bytes as the body; a `.jar` key is inspected like an upload. */
    @PutMapping("/files/content", consumes = [MediaType.ALL_VALUE])
    suspend fun putObject(
        @RequestParam key: String,
        @RequestParam(required = false) contentType: String?,
        @RequestBody bytes: ByteArray
    ): ResponseEntity<Any> {
        if (!S3Service.isAllowedKey(key)) return ResponseEntity.badRequest().body("Invalid key")
        rejectInvalidJar(key.substringAfterLast('/')) { bytes }?.let { return ResponseEntity.status(it.statusCode).body(it.body) }
        return try {
            val eTag = s3Service.putObject(key, bytes, contentType)
            if (JarInspector.isJarName(key)) jarService.invalidate()
            ResponseEntity.ok(mapOf("key" to key, "eTag" to eTag))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.message ?: "Unable to store the object")
        }
    }

    /** `DELETE /files/content?key=`: 204 when gone, 404 when it was not there. */
    @DeleteMapping("/files/content")
    suspend fun deleteObject(@RequestParam key: String): ResponseEntity<Any> {
        if (!S3Service.isAllowedKey(key)) return ResponseEntity.badRequest().body("Invalid key")
        val removed = s3Service.deleteObject(key)
        if (removed && JarInspector.isJarName(key)) jarService.invalidate()
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    // ---- error log ---------------------------------------------------------------------------------

    /** Appends one entry to the user's error log. */
    @PostMapping("/errors")
    suspend fun writeError(
        @RequestParam username: String,
        @RequestParam error: String,
        @RequestParam message: String
    ): ResponseEntity<Void> {
        if (username.isBlank()) return ResponseEntity.badRequest().build()
        s3Service.writeError(username, error, message)
        return ResponseEntity.noContent().build()
    }

    /** The user's error log, newest first. */
    @GetMapping("/errors")
    suspend fun listErrors(@RequestParam username: String): ResponseEntity<List<ErrorEntry>> =
        ResponseEntity.ok(s3Service.listErrors(username))

    /** Removes the user's error log. */
    @DeleteMapping("/errors")
    suspend fun clearErrors(@RequestParam username: String): ResponseEntity<Void> {
        s3Service.clearErrors(username)
        return ResponseEntity.noContent().build()
    }

    // ---- the rest ----------------------------------------------------------------------------------

    @GetMapping("/endpoints")
    suspend fun fetchEndpoints(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(s3Service.fetchEndpoints())

    @PostMapping("/endpoints")
    suspend fun postEndpoints(): ResponseEntity<Void> {
        s3Service.postEndpoints()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/files")
    suspend fun queryFiles(@RequestParam(defaultValue = "") query: String): ResponseEntity<Any> {
        val result = s3Service.queryFiles(query) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/dashboard")
    suspend fun createUserDashboard(@RequestParam username: String): ResponseEntity<Void> {
        s3Service.createUserDashboard(username)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/files/root")
    suspend fun listFiles(): ResponseEntity<List<S3ItemDto>> =
        ResponseEntity.ok(s3Service.listFiles())

    @GetMapping("/users/{username}/packages")
    suspend fun listUserPackages(@PathVariable username: String): ResponseEntity<List<S3ItemDto>> =
        ResponseEntity.ok(s3Service.listUserPackages(username))

    /** The names of a user's saved states. */
    @GetMapping("/users/{username}/states")
    suspend fun listStates(@PathVariable username: String): ResponseEntity<List<String>> =
        ResponseEntity.ok(s3Service.listStates(username))

    @GetMapping("/objects")
    suspend fun listObjects(): ResponseEntity<*> {
        return try {
            ResponseEntity.ok(s3Service.listBucketObjects())
        } catch (e: Exception) {
            val message = e.message ?: "Unable to retrieve objects from S3"
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message)
        }
    }

    @GetMapping("/downloadAmie")
    suspend fun downloadAmie(
        @RequestParam(defaultValue = "test.txt") keyName: String,
        @RequestParam(defaultValue = "setup-demo-sams") bucketName: String
    ): ResponseEntity<InputStreamResource> {
        val s3Stream = s3Service.downloadAmie(keyName, bucketName)
        val contentLength = s3Stream.response().contentLength()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$keyName\"")
            .contentLength(contentLength)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(s3Stream))
    }

    private fun contentTypeOf(stored: StoredObject): MediaType {
        val declared = stored.contentType?.takeIf { it.isNotBlank() && !it.startsWith("binary/octet-stream") }
        val byName = when (stored.name.substringAfterLast('.', "").lowercase()) {
            "json" -> "application/json"
            "jsonl" -> "application/x-ndjson"
            "md" -> "text/markdown; charset=utf-8"
            "txt", "kts", "kt", "py", "c", "cpp", "h", "ino", "csv" -> "text/plain; charset=utf-8"
            "jar" -> "application/java-archive"
            "onnx" -> "application/octet-stream"
            else -> null
        }
        return try {
            MediaType.parseMediaType(declared ?: byName ?: MediaType.APPLICATION_OCTET_STREAM_VALUE)
        } catch (e: Exception) {
            MediaType.APPLICATION_OCTET_STREAM
        }
    }
}
