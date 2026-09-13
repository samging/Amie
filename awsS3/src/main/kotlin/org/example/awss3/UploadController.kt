package org.example.awss3

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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
}
