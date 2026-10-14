package org.example.amiepackagerepository.shared.packagestore

import org.example.amiepackagerepository.shared.packagestore.dto.ErrorEntryDto
import org.example.amiepackagerepository.shared.packagestore.dto.ErrorReportDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * What a signed-in user keeps in the store besides packages: the error log their app reports to
 * (`uploads/<user>/state/errors.jsonl`, shown on the website's dashboard) and named saved states
 * (`uploads/<user>/state/<name>.json`). Every route needs the user's bearer token.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class StateController(
    private val store: PackageStoreClient,
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(StateController::class.java)

    companion object {
        const val MAX_STATE_BYTES = 1024 * 1024
    }

    private fun <T> store(what: String, block: () -> T): T = try {
        block()
    } catch (e: PackageStoreException) {
        logger.warn("Could not {}: {}", what, e.message)
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, e.message)
    }

    // ---- errors ------------------------------------------------------------------------------------

    /** The caller's error log, newest first. */
    @GetMapping("/errors")
    fun listErrors(@RequestParam username: String, @RequestHeader("Authorization") authHeader: String): List<ErrorEntryDto> {
        userService.requireUser(authHeader, username)
        return store("read the error log") { store.listErrors(username) }
    }

    /** Appends an error the caller's app ran into. */
    @PostMapping("/errors")
    fun reportError(@RequestBody report: ErrorReportDto, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val subject = userService.requireSubject(authHeader)
        if (report.message.isBlank() && report.error.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to report")
        store.writeError(subject, report.error.ifBlank { "error" }, report.message)
        return ResponseEntity.noContent().build()
    }

    /** Empties the caller's error log. */
    @DeleteMapping("/errors")
    fun clearErrors(@RequestParam username: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        userService.requireUser(authHeader, username)
        store("clear the error log") { store.clearErrors(username) }
        return ResponseEntity.noContent().build()
    }

    // ---- saved states ------------------------------------------------------------------------------

    /** The names of the caller's saved states. */
    @GetMapping("/state")
    fun listStates(@RequestHeader("Authorization") authHeader: String): List<String> {
        val subject = userService.requireSubject(authHeader)
        return store("list the states") { store.listStates(subject) }
    }

    /** One saved state as it was stored; 404 when there is none. */
    @GetMapping("/state/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loadState(@PathVariable name: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<String> {
        val subject = userService.requireSubject(authHeader)
        requireName(name)
        val json = store("read state $name") { store.loadState(subject, name) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No state named $name")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json)
    }

    /** Stores a state; the body must be JSON of at most [MAX_STATE_BYTES]. */
    @PutMapping("/state/{name}", consumes = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun saveState(@PathVariable name: String, @RequestBody body: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val subject = userService.requireSubject(authHeader)
        requireName(name)
        if (body.toByteArray().size > MAX_STATE_BYTES) throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A state may be at most 1 MiB")
        try {
            store("save state $name") { store.saveState(subject, name, body) }
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
        userService.logActivity(subject, "SAVE_STATE", "Saved state: $name")
        return ResponseEntity.noContent().build()
    }

    /** Removes a saved state; 404 when there was none. */
    @DeleteMapping("/state/{name}")
    fun deleteState(@PathVariable name: String, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val subject = userService.requireSubject(authHeader)
        requireName(name)
        val removed = store("delete state $name") { store.deleteState(subject, name) }
        return if (removed) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    private fun requireName(name: String) {
        if (!PackageStoreClient.isValidStateName(name)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "State names may only contain letters, digits, dots, dashes and underscores")
    }
}
