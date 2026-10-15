package org.example.amiepackagerepository.shared.errors

import jakarta.servlet.http.HttpServletRequest
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponseException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * Turns everything a route throws into one JSON shape, an RFC 9457 problem document
 * (`Content-Type: application/problem+json`):
 *
 * ```json
 * {"type":"about:blank","title":"Forbidden","status":403,"detail":"The token belongs to another user",
 *  "instance":"/upload","code":"forbidden","timestamp":"2026-10-15T10:00:00Z"}
 * ```
 *
 * `detail` is the human-readable reason a controller gave (`ResponseStatusException(status, reason)`),
 * `code` a stable machine-readable name a client can switch on, and `errorId` (500 only) the id to look
 * for in the backend log. Without this advice Spring Boot answers thrown errors with its own JSON and
 * leaves the reason out, so clients never learn why they were refused.
 *
 * What maps to what:
 *
 * | thrown | answer |
 * |---|---|
 * | [ResponseStatusException] / any [ErrorResponseException] | its status and reason |
 * | [PackageStoreException] (the S3 service refused or is unreachable) | 502 `store_unavailable` |
 * | [IllegalArgumentException] (a `require(...)` in a service) | 400 `bad_request` |
 * | [ConflictException], [SecurityException], [NoSuchElementException] from a service | 409, 403, 404 |
 * | missing `Authorization` header | 401 `unauthorized` |
 * | missing parameter, part or body, wrong type, unreadable JSON | 400 `bad_request` |
 * | [MaxUploadSizeExceededException] (`spring.servlet.multipart.max-file-size`) | 413 `payload_too_large` |
 * | wrong method, unsupported `Content-Type`, no such route | 405, 415, 404 |
 * | anything else | 500 `internal_error`, with an `errorId` and the stack trace in the log |
 */
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    companion object {
        const val CODE_STORE_UNAVAILABLE = "store_unavailable"
        const val CODE_INTERNAL = "internal_error"

        /** The `code` a client sees for a status when nothing more specific applies. */
        fun codeFor(status: HttpStatusCode): String = when (status.value()) {
            400 -> "bad_request"
            401 -> "unauthorized"
            403 -> "forbidden"
            404 -> "not_found"
            405 -> "method_not_allowed"
            409 -> "conflict"
            413 -> "payload_too_large"
            415 -> "unsupported_media_type"
            422 -> "unprocessable"
            500 -> CODE_INTERNAL
            502 -> "bad_gateway"
            503 -> "service_unavailable"
            else -> HttpStatus.resolve(status.value())?.name?.lowercase() ?: "error"
        }

        /** Builds the problem document every handler answers with. */
        fun problem(status: HttpStatusCode, detail: String?, request: HttpServletRequest?, code: String = codeFor(status)): ProblemDetail {
            val problem = ProblemDetail.forStatus(status)
            problem.title = HttpStatus.resolve(status.value())?.reasonPhrase ?: "Error"
            problem.detail = detail?.takeIf { it.isNotBlank() } ?: problem.title
            request?.requestURI?.let { problem.instance = URI.create(it) }
            problem.setProperty("code", code)
            problem.setProperty("timestamp", Instant.now().toString())
            return problem
        }
    }

    private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(problem.status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)

    private fun answer(status: HttpStatusCode, detail: String?, request: HttpServletRequest, code: String = codeFor(status)) =
        respond(problem(status, detail, request, code))

    // ---- what controllers throw on purpose ---------------------------------------------------------

    /** `ResponseStatusException` and friends: the status and reason the route chose. */
    @ExceptionHandler(ErrorResponseException::class)
    fun errorResponse(e: ErrorResponseException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        // The reason a route gave; without one the status title stands in (never "401 UNAUTHORIZED")
        val detail = e.body.detail ?: (e as? ResponseStatusException)?.reason
        if (e.statusCode.is5xxServerError) log.warn("{} {} -> {}: {}", request.method, request.requestURI, e.statusCode.value(), detail)
        return answer(e.statusCode, detail, request)
    }

    /** The S3 service refused the call or could not be reached; the client can retry later. */
    @ExceptionHandler(PackageStoreException::class)
    fun packageStore(e: PackageStoreException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.warn("{} {}: the package store failed ({}): {}", request.method, request.requestURI, e.status ?: "unreachable", e.message)
        val problem = problem(HttpStatus.BAD_GATEWAY, e.message, request, CODE_STORE_UNAVAILABLE)
        e.status?.let { problem.setProperty("storeStatus", it) }
        return respond(problem)
    }

    /** Something already holds the name the caller wants. */
    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.CONFLICT, e.message, request)

    /** A service refused the caller: not the author, not the owner. */
    @ExceptionHandler(SecurityException::class)
    fun security(e: SecurityException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.FORBIDDEN, e.message, request)

    /** A service looked for something that is not there. */
    @ExceptionHandler(NoSuchElementException::class)
    fun missing(e: NoSuchElementException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.NOT_FOUND, e.message, request)

    /** A `require(...)` in a service: the caller sent something the store cannot take (bad key, name, JSON). */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, e.message, request)

    // ---- what Spring throws before a route runs ----------------------------------------------------

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(e: MissingRequestHeaderException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        if (e.headerName.equals("Authorization", ignoreCase = true)) answer(HttpStatus.UNAUTHORIZED, "Missing bearer token", request)
        else answer(HttpStatus.BAD_REQUEST, "Missing header: ${e.headerName}", request)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(e: MissingServletRequestParameterException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Missing parameter: ${e.parameterName}", request)

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun missingPart(e: MissingServletRequestPartException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Missing multipart field: ${e.requestPartName}", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Parameter '${e.name}' has the wrong value: ${e.value}", request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(e: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "The request body could not be read: ${e.mostSpecificCause.message?.lineSequence()?.firstOrNull() ?: "invalid JSON"}", request)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(e: MaxUploadSizeExceededException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val limit = e.maxUploadSize.takeIf { it > 0 }?.let { " (at most $it bytes)" }.orEmpty()
        return answer(HttpStatus.PAYLOAD_TOO_LARGE, "The upload is too large$limit", request)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun wrongMethod(e: HttpRequestMethodNotSupportedException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.METHOD_NOT_ALLOWED, "${e.method} is not allowed here" + (e.supportedMethods?.let { "; use ${it.joinToString(", ")}" } ?: ""), request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun wrongMediaType(e: HttpMediaTypeNotSupportedException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type ${e.contentType ?: "(none)"} is not accepted here", request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun noRoute(e: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.NOT_FOUND, "No route ${request.method} ${request.requestURI}", request)

    // ---- everything else ---------------------------------------------------------------------------

    /** A bug or an outage nobody planned for: the client gets an id, the log gets the stack trace. */
    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val errorId = UUID.randomUUID().toString().substring(0, 8)
        log.error("[{}] {} {} failed: {}", errorId, request.method, request.requestURI, e.toString(), e)
        val problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server (error $errorId)", request, CODE_INTERNAL)
        problem.setProperty("errorId", errorId)
        return respond(problem)
    }
}
