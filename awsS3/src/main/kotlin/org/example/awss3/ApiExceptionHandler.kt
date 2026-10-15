package org.example.awss3

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponseException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.server.ResponseStatusException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * One JSON shape for everything a route of this service throws, an RFC 9457 problem document
 * (`application/problem+json`) with a stable `code` the backend and the scripts can switch on:
 *
 * | thrown | answer |
 * |---|---|
 * | [NoSuchKeyException], or an [S3Exception] with status 404 | 404 `not_found` |
 * | [NoSuchBucketException] | 502 `bucket_missing` (`aws.s3.bucket-name` names a bucket that does not exist) |
 * | an [S3Exception] with status 403 (AccessDenied, InvalidAccessKeyId, SignatureDoesNotMatch) | 502 `store_access_denied` |
 * | any other [S3Exception] (S3 answered with an error) | 502 `store_error`, `awsCode` carries S3's error code |
 * | [SdkClientException] (no credentials, no network, DNS) | 503 `store_unreachable` |
 * | [IllegalArgumentException] (a `require(...)` in [S3Service]) | 400 `bad_request` |
 * | [MaxUploadSizeExceededException] (`spring.servlet.multipart.max-file-size`) | 413 `payload_too_large` |
 * | missing parameter or part, wrong type, wrong method, unsupported `Content-Type` | 400, 400, 400, 405, 415 |
 * | `ResponseStatusException` | its status and reason |
 * | anything else | 500 `internal_error`, with an `errorId` to find in the log |
 *
 * Routes that already answer an error themselves (the upload routes catch and return 400/500 with a
 * plain-text message) are not touched; this covers what escapes them.
 */
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    companion object {
        const val CODE_NOT_FOUND = "not_found"
        const val CODE_BUCKET_MISSING = "bucket_missing"
        const val CODE_ACCESS_DENIED = "store_access_denied"
        const val CODE_STORE_ERROR = "store_error"
        const val CODE_STORE_UNREACHABLE = "store_unreachable"
        const val CODE_INTERNAL = "internal_error"

        fun codeFor(status: HttpStatusCode): String = when (status.value()) {
            400 -> "bad_request"
            404 -> CODE_NOT_FOUND
            405 -> "method_not_allowed"
            413 -> "payload_too_large"
            415 -> "unsupported_media_type"
            500 -> CODE_INTERNAL
            502 -> "bad_gateway"
            503 -> "service_unavailable"
            else -> HttpStatus.resolve(status.value())?.name?.lowercase() ?: "error"
        }

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

    // ---- S3 ----------------------------------------------------------------------------------------

    @ExceptionHandler(NoSuchKeyException::class)
    fun noSuchKey(e: NoSuchKeyException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.NOT_FOUND, "No such object in the bucket", request, CODE_NOT_FOUND)

    @ExceptionHandler(NoSuchBucketException::class)
    fun noSuchBucket(e: NoSuchBucketException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("{} {}: the configured bucket does not exist: {}", request.method, request.requestURI, e.message)
        return answer(HttpStatus.BAD_GATEWAY, "The configured S3 bucket does not exist", request, CODE_BUCKET_MISSING)
    }

    /** S3 answered with an error: 404 is a missing object, 403 a credentials or policy problem, the rest an outage. */
    @ExceptionHandler(S3Exception::class)
    fun s3(e: S3Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val awsCode = e.awsErrorDetails()?.errorCode()
        val awsMessage = e.awsErrorDetails()?.errorMessage() ?: e.message
        return when (e.statusCode()) {
            404 -> answer(HttpStatus.NOT_FOUND, "No such object in the bucket", request, CODE_NOT_FOUND)
            403 -> {
                log.error("{} {}: S3 refused the call ({}): {}", request.method, request.requestURI, awsCode, awsMessage)
                val problem = problem(HttpStatus.BAD_GATEWAY, "S3 refused the call: check the AWS credentials and the bucket policy", request, CODE_ACCESS_DENIED)
                awsCode?.let { problem.setProperty("awsCode", it) }
                respond(problem)
            }
            else -> {
                log.error("{} {}: S3 failed ({} {}): {}", request.method, request.requestURI, e.statusCode(), awsCode, awsMessage)
                val problem = problem(HttpStatus.BAD_GATEWAY, "S3 failed: ${awsMessage ?: "unknown error"}", request, CODE_STORE_ERROR)
                awsCode?.let { problem.setProperty("awsCode", it) }
                problem.setProperty("awsStatus", e.statusCode())
                respond(problem)
            }
        }
    }

    /** The SDK never got an answer: no credentials, no network, or a DNS problem. */
    @ExceptionHandler(SdkClientException::class)
    fun sdkClient(e: SdkClientException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("{} {}: S3 is unreachable: {}", request.method, request.requestURI, e.message)
        return answer(HttpStatus.SERVICE_UNAVAILABLE, "S3 could not be reached: ${e.message?.lineSequence()?.firstOrNull() ?: "unknown reason"}", request, CODE_STORE_UNREACHABLE)
    }

    // ---- input ---------------------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, e.message, request)

    @ExceptionHandler(ErrorResponseException::class)
    fun errorResponse(e: ErrorResponseException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(e.statusCode, e.body.detail ?: (e as? ResponseStatusException)?.reason, request)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(e: MissingServletRequestParameterException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Missing parameter: ${e.parameterName}", request)

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun missingPart(e: MissingServletRequestPartException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Missing multipart field: ${e.requestPartName}", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        answer(HttpStatus.BAD_REQUEST, "Parameter '${e.name}' has the wrong value: ${e.value}", request)

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

    // ---- everything else ---------------------------------------------------------------------------

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val errorId = UUID.randomUUID().toString().substring(0, 8)
        log.error("[{}] {} {} failed: {}", errorId, request.method, request.requestURI, e.toString(), e)
        val problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server (error $errorId)", request, CODE_INTERNAL)
        problem.setProperty("errorId", errorId)
        return respond(problem)
    }
}
