package org.example.amiepackagerepository.shared.packagestore

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.example.amiepackagerepository.shared.packages.AmiePackage
import org.example.amiepackagerepository.shared.notes.NoteService
import org.example.amiepackagerepository.shared.stats.StatsService
import org.example.amiepackagerepository.shared.packages.JarInspector
import org.example.amiepackagerepository.shared.packagestore.dto.PackageFileDto
import org.example.amiepackagerepository.shared.packagestore.dto.PackageItemDto
import org.example.amiepackagerepository.shared.packagestore.dto.PackageItemMetadataDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.PostDeviceStatusDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.Base64

/**
 * The package store's routes: listing, publishing, replacing and removing files, and the device state
 * the desktop app keeps per user. Files live in the S3 bucket behind the Amie S3 service
 * ([PackageStoreClient]); `/list-github` is kept as an alias of `/packages` for older clients.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class PackageController(
    private val store: PackageStoreClient,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository,
    private val jarInspector: JarInspector,
    private val notes: NoteService,
    private val statsService: StatsService,
) {
    private val logger = LoggerFactory.getLogger(PackageController::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Files up to this size come back with their content in `GET /package`. */
        const val MAX_INLINE_BYTES = 1024L * 1024
    }

    /** Everything directly below `uploads/`: one `dir` per author, plus loose files. */
    @GetMapping("/packages", "/list-github")
    fun listPackages(): List<PackageItemDto> = store.listRoot()

    /** Legacy metadata listing; marks `.c` files. */
    @GetMapping("/list-github-metadata")
    fun listMetadata(): Map<String, PackageItemMetadataDto> =
        store.listRoot().mapIndexed { index, item ->
            index.toString() to PackageItemMetadataDto(
                name = item.name,
                downloadUrl = item.downloadUrl,
                id = item.id,
                type = item.type,
                endComp = item.name.endsWith(".c").toString()
            )
        }.toMap()

    /**
     * One stored file: its metadata and, for files up to [MAX_INLINE_BYTES], its content as base64.
     * 404 when there is no such file, 400 for a path outside `uploads/`.
     */
    @GetMapping("/package")
    fun getPackage(@RequestParam path: String): PackageFileDto {
        if (!PackageStoreClient.isAllowedKey(path)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path")
        val meta = try {
            store.head(path)
        } catch (e: PackageStoreException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, e.message)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such file")
        val content = if (meta.size in 0..MAX_INLINE_BYTES) {
            try { store.get(path)?.bytes?.let { Base64.getEncoder().encodeToString(it) } } catch (e: PackageStoreException) { null }
        } else null
        return PackageFileDto(
            name = meta.name,
            path = meta.path,
            sha = meta.eTag,
            size = meta.size,
            url = meta.downloadUrl,
            htmlUrl = store.viewUrl(path),
            downloadUrl = meta.downloadUrl,
            type = "file",
            content = content,
            encoding = content?.let { "base64" }
        )
    }

    /** Removes one of the caller's files by path: 204 when gone, 404 when it was not there, 403 for another author's file. */
    @DeleteMapping("/package")
    fun deletePackage(@RequestParam path: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val subject = userService.requireSubject(authHeader)
        if (!PackageStoreClient.isAllowedKey(path)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path")
        if (PackageStoreClient.ownerOf(path) != subject) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your file")
        val removed = try {
            store.delete(path)
        } catch (e: PackageStoreException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, e.message)
        }
        if (removed) {
            userService.logActivity(subject, "REMOVE_PACKAGE", "Removed package: ${path.substringAfterLast('/')}")
            notes.deleteForPackage(path)
            statsService.forgetPackage(path)
        }
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /**
     * Publishes a file as `uploads/<user>/<progLanguage>/<name>`. A `.jar` upload (or anything sent with
     * `progLanguage=jar`, which is what the website's upload page sends) must be a valid Amie package: a
     * jar with a class exposing `run(String): List` and `close()` (see [AmiePackage]). Other file types,
     * such as the desktop app's ONNX models, are stored as they are.
     *
     * @return 200 with a message, 400 with the reasons a jar was refused, 401, 403, or 502 when the store is down.
     */
    @PostMapping("/upload")
    fun uploadCodeSnippet(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<String> {
        logger.info("--- [POST /upload] START (user: {}, file: {}, lang: {}) ---", username, file.originalFilename, progLanguage)
        userService.requireUser(authHeader, username)

        val fileName = file.originalFilename?.trim()?.takeIf { it.isNotEmpty() } ?: "unnamed_file"
        if (!PackageStoreClient.isAllowedKey(PackageStoreClient.packageKey(username, progLanguage, fileName))) {
            return ResponseEntity.badRequest().body("Invalid file name")
        }
        val bytes = file.bytes
        val mustBeJar = progLanguage.equals("jar", ignoreCase = true) || JarInspector.isJarName(fileName)
        if (mustBeJar) {
            val inspection = jarInspector.inspect(fileName, bytes)
            if (!inspection.valid) {
                logger.info("CONTROLLER: Refused jar '{}' from '{}': {}", fileName, username, inspection.problems)
                return ResponseEntity.badRequest().body(inspection.problems.joinToString("; "))
            }
            logger.info("CONTROLLER: Jar '{}' exposes package classes {}", fileName, inspection.entryClasses)
        }

        return try {
            store.upload(username, progLanguage, fileName, bytes, file.contentType)
            userService.logActivity(username, "UPLOAD_PACKAGE", "Uploaded package: $fileName")
            logger.info("CONTROLLER: Upload successful for user '{}', file '{}'", username, fileName)
            ResponseEntity.ok("File uploaded successfully")
        } catch (e: PackageStoreException) {
            logger.error("CONTROLLER: the package store refused '{}': {}", fileName, e.message)
            if (e.status == 400) ResponseEntity.badRequest().body(e.message)
            else ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The package could not be stored")
        } catch (e: Exception) {
            logger.error("CONTROLLER: Unexpected error during upload: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The package could not be stored")
        } finally {
            logger.info("--- [POST /upload] END ---")
        }
    }

    /** Removes a file the user uploaded, the reverse of `/upload` (the desktop app's "remove publish"). */
    @DeleteMapping("/upload")
    fun removeCodeSnippet(
        @RequestParam("filename") fileName: String,
        @RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<String> {
        logger.info("--- [DELETE /upload] START (user: {}, file: {}, lang: {}) ---", username, fileName, progLanguage)
        userService.requireUser(authHeader, username)
        val key = PackageStoreClient.packageKey(username, progLanguage, fileName)
        if (!PackageStoreClient.isAllowedKey(key)) return ResponseEntity.badRequest().body("Invalid file name")
        return try {
            val removed = store.delete(key)
            if (removed) {
                userService.logActivity(username, "REMOVE_PACKAGE", "Removed package: $fileName")
                notes.deleteForPackage(key)
                statsService.forgetPackage(key)
            }
            ResponseEntity.ok(if (removed) "removed" else "not there")
        } catch (e: PackageStoreException) {
            logger.error("CONTROLLER: delete failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The package could not be removed")
        } finally {
            logger.info("--- [DELETE /upload] END ---")
        }
    }

    /** Replaces an existing file below `uploads/<user>/` (the website's Replace button). */
    @PostMapping("/edit")
    fun updateCodeSnippet(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("filename") fileName: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): String {
        logger.info("--- [POST /edit] START (user: {}, file: {}) ---", username, fileName)
        userService.requireUser(authHeader, username)
        // The website sends the full path of the file it lists; the store wants it relative to the user's folder
        val relative = fileName.removePrefix("${PackageStoreClient.UPLOADS}$username/")
        if (!PackageStoreClient.isAllowedKey("${PackageStoreClient.UPLOADS}$username/$relative")) return "Error updating file: invalid file name"
        return try {
            store.edit(username, relative, file.bytes, file.contentType)
            userService.logActivity(username, "EDIT_PACKAGE", "Edited package: $relative")
            "File updated successfully"
        } catch (e: Exception) {
            logger.error("Edit Error: {}", e.message)
            "Error updating file: ${e.message}"
        } finally {
            logger.info("--- [POST /edit] END ---")
        }
    }

    /** Saves the user's devices (Postgres and `state/devices.json`). */
    @PostMapping("/update-device-status")
    suspend fun postDeviceStatusDto(@RequestBody deviceUpdateDto: PostDeviceStatusDto) {
        logger.info("--- [POST /update-device-status] START (user: {}) ---", deviceUpdateDto.username)
        val response = deviceService.repositoryDeviceController(
            DeviceActions.SET, deviceUpdateDto.username, deviceUpdateDto.deviceMap
        ).await()
        if (response.statusCode == HttpStatus.OK) {
            userService.logActivity(deviceUpdateDto.username, "UPDATE_DEVICE_STATUS", "Updated device statuses")
            logger.info("Device update successful for user: {}", deviceUpdateDto.username)
        } else {
            logger.error("Device update failed for user: {} ({}): {}", deviceUpdateDto.username, response.statusCode, response.body)
        }
        logger.info("--- [POST /update-device-status] END ---")
    }

    /** The user's saved devices, from `state/devices.json`; an empty map when there is none. */
    @PostMapping("/get-device-status")
    suspend fun fetchDeviceStatuses(@RequestBody deviceUpdateDto: PostDeviceStatusDto): Map<String, DeviceDto> {
        logger.info("--- [POST /get-device-status] START (user: {}) ---", deviceUpdateDto.username)
        val response = deviceService.repositoryDeviceController(
            DeviceActions.GET, deviceUpdateDto.username, deviceUpdateDto.deviceMap
        ).await()
        return try {
            if (response.statusCode == HttpStatus.OK) {
                val body = response.body?.takeIf { it.isNotBlank() } ?: "{}"
                json.decodeFromString<Map<String, DeviceDto>>(body)
            } else {
                logger.error("Device fetch failed for user: {} ({}): {}", deviceUpdateDto.username, response.statusCode, response.body)
                emptyMap()
            }
        } catch (e: Exception) {
            logger.error("Saved devices for {} could not be read: {}", deviceUpdateDto.username, e.message)
            emptyMap()
        } finally {
            logger.info("--- [POST /get-device-status] END ---")
        }
    }
}
