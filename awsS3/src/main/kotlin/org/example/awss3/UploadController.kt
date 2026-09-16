package org.example.awss3

import org.apache.coyote.Response
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse

@RestController
@RequestMapping("/api/aws")
class UploadController(private val s3Service: S3Service) {

    @PostMapping("/upload")
    suspend fun uploadFile(@RequestParam("file") file: MultipartFile): ResponseEntity<String> {
        return try {
            val result = s3Service.uploadFile(file)
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
        return try {
            ResponseEntity.ok(s3Service.uploadFileData(username, fileName, data))
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
        return try {
            ResponseEntity.ok(s3Service.sendEdit(username, fileName, file))
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
    suspend fun downloadAmie(): ResponseEntity<InputStreamResource> {
        val keyName = "test.txt"
        val bucketName = "setup-demo-sams"
        val s3Stream = s3Service.downloadAmie(keyName, bucketName)
        val contentLength = s3Stream.response().contentLength()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$keyName\"")
            .contentLength(contentLength)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(s3Stream))
    }
}
