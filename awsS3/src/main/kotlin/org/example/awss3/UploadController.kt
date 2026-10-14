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

    @PostMapping("/upload-data", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    suspend fun uploadFileData(
        @RequestParam username: String,
        @RequestParam fileName: String,
        @RequestBody data: ByteArray
    ): ResponseEntity<String> {
        rejectInvalidJar(fileName) { data }?.let { return it }
        return try {
            val result = s3Service.uploadFileData(username, fileName, data)
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

    @PostMapping("/errors")
    suspend fun writeError(
        @RequestParam username: String,
        @RequestParam error: String,
        @RequestParam message: String
    ): ResponseEntity<Void> {
        s3Service.writeError(username, error, message)
        return ResponseEntity.noContent().build()
    }

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
}
