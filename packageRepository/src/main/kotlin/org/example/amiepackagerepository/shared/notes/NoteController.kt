package org.example.amiepackagerepository.shared.notes

import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.packagestore.requireSubject
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** `POST /notes` body. */
data class NewNoteDto(val path: String = "", val text: String = "")

/**
 * Notes on packages: anyone may read them, a signed-in person may add one, the author or the package's
 * owner may remove one. Errors follow the problem document of `ApiExceptionHandler`: 400 for an empty or
 * invalid note, 401 without a token, 403 for someone else's note, 404 for a missing package or note,
 * 429 when posting too fast, 502 when the store cannot confirm the package.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class NoteController(private val notes: NoteService, private val userService: UserService) {

    @GetMapping("/notes")
    fun list(@RequestParam path: String): List<NoteDto> = notes.list(path)

    @PostMapping("/notes")
    fun add(@RequestBody body: NewNoteDto, @RequestHeader("Authorization") authHeader: String): ResponseEntity<NoteDto> {
        val subject = userService.requireSubject(authHeader)
        val note = try {
            notes.add(subject, body.path, body.text)
        } catch (e: TooManyNotesException) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.message)
        } catch (e: PackageStoreException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, e.message)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(note)
    }

    @DeleteMapping("/notes/{id}")
    fun delete(@PathVariable id: Long, @RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val subject = userService.requireSubject(authHeader)
        return if (notes.delete(subject, id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}
