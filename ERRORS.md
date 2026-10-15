# Errors in Amie: what is thrown, what a client sees, what to do

Three places answer errors: the **backend** (`packageRepository`, port 8090 on this Mac), the **S3 service**
(`awsS3`, port 8088) behind it, and the **website's own server** (`app/site`, port 8081), which the browser
talks to exclusively. Each has one error shape. `scripts/examples/provoke-errors.sh` prints every case below
against the running stack; `scripts/examples/amie_client.py` is a client that handles all of them.

## 1. The shapes

**Backend and S3 service, anything thrown** → an RFC 9457 problem document, `Content-Type: application/problem+json`
(`ApiExceptionHandler` in each service):

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "The token belongs to another user",
  "instance": "/upload",
  "code": "forbidden",
  "timestamp": "2026-10-15T16:02:11.482Z"
}
```

- `detail` is the sentence to show a person; `code` is what a program switches on (below).
- A 500 adds `errorId`; the same id is in `logs/backend.log` (or `logs/s3.log`) next to the stack trace.
- A 502 from the backend may add `storeStatus` (what the S3 service answered); a 502 from the S3 service
  adds `awsCode` (`AccessDenied`, `NoSuchBucket`, …) and `awsStatus`.

**Backend and S3 service, refusals a route answers itself** → plain text. These are the upload routes: a jar
that does not satisfy the package contract comes back as `400` with the reasons joined by `; `, for instance
`No class in the jar has both run(String): List and close() methods`. The website shows that text as it is.

**Website server** (`/api/...`) → `{"error": "<code>"}` with one of: `unauthorized` (401), `forbidden` (403),
`not_found` (404), `missing_username`, `missing_path`, `missing_file`, `invalid_name`, `invalid_path`,
`invalid_profile`, `invalid_request`, `missing_parameters`, `rejected` (400), `too_large` (413), `not_onnx`
(422, `/api/model-info`), `too_many_requests` (429, the SSO claim), `unavailable` (502: the backend or the
store is down). The proxy maps the backend's statuses onto these; a backend `400` on upload keeps its text.

**Keycloak** answers its own shape, `{"error":"invalid_grant","error_description":"Invalid user credentials"}`;
the website passes it through on `/api/keycloak/token`.

## 2. Codes

| `code` | status | who | meaning |
|---|---|---|---|
| `bad_request` | 400 | both | a parameter, header, body or name is missing or malformed; `detail` names it |
| `unauthorized` | 401 | backend | no `Authorization: Bearer` header, or a token that is expired or not from this backend / the Keycloak realm |
| `forbidden` | 403 | backend | the token is valid but belongs to another user than the one named in the request |
| `not_found` | 404 | both | no such file, state, profile or route |
| `method_not_allowed` | 405 | both | wrong HTTP method; `detail` says which ones work |
| `conflict` | 409 | backend | `/register` with a taken username |
| `payload_too_large` | 413 | both | over `spring.servlet.multipart.max-file-size` (100 MB backend) or the 1 MiB state limit |
| `unsupported_media_type` | 415 | both | the `Content-Type` is not one the route takes |
| `store_unavailable` | 502 | backend | the S3 service refused or could not be reached (`PackageStoreException`); retry later |
| `bad_gateway` | 502 | backend | same thing, thrown by a route as `ResponseStatusException(BAD_GATEWAY)` |
| `bucket_missing` | 502 | S3 service | `aws.s3.bucket-name` names a bucket that does not exist |
| `store_access_denied` | 502 | S3 service | S3 answered 403: wrong credentials or bucket policy (`awsCode`) |
| `store_error` | 502 | S3 service | S3 answered another error (`awsCode`, `awsStatus`) |
| `store_unreachable` | 503 | S3 service | the AWS SDK never got an answer: no credentials, no network |
| `internal_error` | 500 | both | a bug; quote `errorId` |

## 3. Backend routes

Every signed-in route can answer **401** (`Missing bearer token`, `Unauthorized or expired token`, `Token has
no subject`) and, when it names a user, **403** (`The token belongs to another user`). Every route that reads
the store can answer **502** with the `PackageStoreException` message: `The package store at
http://localhost:8088 is unreachable: …` or `The package store could not <what> (<status>): <body>`.

| Route | Refusals |
|---|---|
| `POST /register` | 400 `Username required` / `Password required`; 409 `Username is already assigned to different account` |
| `POST /login` | 401 `Invalid credentials`; `guest-*` with an empty password gets a guest token instead |
| `GET /dashboard`, `GET /me` | 401; `/me` 404 `Profile not found` when the row could not be created |
| `GET /profile?username=` | 404 `Profile not found` |
| `POST /profile?username=` | 401, 403; 400 `User not found` for an account without a backend row |
| `DELETE /user` | never fails: a wrong password simply deletes nothing (200) |
| `GET /packages`, `/list-github`, `/list-github-metadata` | 502 when the store is down |
| `GET /package?path=` | 400 `Invalid path` (not below `uploads/`, or `..`); 404 `No such file`; 502 |
| `DELETE /package?path=` | 401; 400 `Invalid path`; 403 `Not your file`; 404 (empty body) when it was not there; 204 when gone; 502 |
| `POST /upload` (multipart `file`, `username`, `progLanguage`) | 401, 403; 400 text `Invalid file name`; 400 text with the jar's problems (any `.jar`, or `progLanguage=jar`); 413 over 100 MB; 502 text `The package could not be stored`; 500 text |
| `DELETE /upload?filename=&progLanguage=&username=` | 401, 403; 400 text `Invalid file name`; 200 `removed` or `not there`; 502 text `The package could not be removed` |
| `POST /edit` (multipart `file`, `filename`, `username`) | 401, 403; otherwise **always 200**: `File updated successfully` or `Error updating file: <reason>` (the website turns an `Error…` body into its own 400 `rejected`; a `.jar` is inspected, a missing file is reported by the store) |
| `GET /user-packages?username=`, `GET /query?query=` | never fail: an empty list when the store is down |
| `GET /errors?username=`, `DELETE /errors?username=` | 401, 403, 502 |
| `POST /errors` `{"error","message"}` | 401; 400 `Nothing to report` when both are blank; a store failure is logged and still 204 |
| `GET /state` | 401, 502 |
| `GET /state/{name}` | 400 `State names may only contain letters, digits, dots, dashes and underscores`; 404 `No state named <name>`; 502 |
| `PUT /state/{name}` (JSON body) | 400 bad name; 400 `The state is not valid JSON`; 413 `A state may be at most 1 MiB`; 502 |
| `DELETE /state/{name}` | 400 bad name; 404 when there was none; 204 when gone; 502 |
| `POST /update-device-status`, `POST /get-device-status` | **always 200** for the desktop app: a failure is logged, GET answers `{}` |
| `POST /device-repository-controller?action=&username=` | 400 `Parameter 'action' has the wrong value` (not `GET`/`SET`); 400 `User not found or is Unidentifiable`; 502 `Local save OK, but the package store failed: …` (SET) or `Error: …` (GET); a failed action maps `IllegalArgumentException` → 400, `NoSuchElementException` → 404, `SecurityException` → 403, `IllegalStateException` → 409, `UnsupportedOperationException` → 501, a provider's `HttpClientErrorException` → its status, anything else → 500, body `Action Failed: <message>` |
| `POST /device-status?username=`, `GET /device-status?username=` | 400 `Missing parameter: username`; the POST saves nothing for an unknown user (200) |
| `GET /fetch-endpoints`, `POST /post-endpoints` | never fail: `{}` / 200 with the failure logged |
| `POST /api/sso-register` | 500 `Proxy Error: …` (legacy route with a hard-coded Keycloak address) |
| `GET /notes?path=` | 400 `Invalid path` |
| `POST /notes` `{"path","text"}` | 401; 400 `Invalid path` / `The note is empty`; 404 `No such user` / `No such package`; 429 `At most 10 notes a minute; try again shortly` / `This package already has 200 notes`; 502 when the store cannot confirm the package |
| `DELETE /notes/{id}` | 401; 403 `Only the author or the package's owner can remove this note`; 404 when there is no such note |
| `POST /profile?username=` (now with `nickname`) | 400 `A nickname has 2 to 32 characters` / `A nickname may only contain letters, digits, spaces, dots, dashes and underscores` / `That is not an email address`; 409 `That nickname is taken` / `That nickname is another account's name` |
| `GET /avatar?username=` | 404 `No picture` |
| `POST /avatar?username=` (multipart `file`) | 401, 403; 400 `Only PNG, JPEG, GIF and WebP pictures are accepted` / `A picture may be at most 512 KB` / `A picture may be at most 2048 × 2048 pixels` / `The picture could not be read` / `The picture is empty`; 413 over 512 KB |
| `DELETE /avatar?username=` | 401, 403; 404 when there was none |
| any route, too often | 429 `Too many requests; try again in N seconds` with `Retry-After` and `retryAfterSeconds` (`RateLimitFilter`: 10 sign-ins, 20 uploads, 60 writes, 300 reads a minute per address) |
| `GET /avatar?username=&preview=true` | 404 `No picture` unless the bearer is the owner's or an administrator's and a picture waits for approval |
| `GET /publications?username=` | never fails: an empty list for an unknown person |
| `POST /publications` | 401; 400 `A publication needs a title` / `The year must be between 1900 and 2100` / `A link must start with http:// or https://` / `That is not a valid link` / `That is not a DOI (10.xxxx/...)` / `At most 50 publications per person`; 404 `No such user` |
| `DELETE /publications/{id}` | 401; 403 `Only the owner can remove this publication`; 404 |
| `POST /profile?username=` (`github`, `huggingFace`) | 400 `That is not a GitHub username` / `That is not a Hugging Face username` |
| `GET /admin/me`, `/admin/users`, `/admin/notes`, `/admin/inbox`, `/admin/avatars/pending`, `/admin/badges` | 401; 403 `The admin role is required` (a token without the Keycloak admin role, including every token this backend issued itself) |
| `POST /admin/users/{u}/avatar/approve`, `POST`/`DELETE /admin/users/{u}/badges/{badge}`, `DELETE /admin/publications/{id}` | 401, 403; 404 when there is nothing to approve, revoke or remove, or no such user; 400 `No such badge: x` |
| `DELETE /admin/notes/{id}`, `/admin/users/{u}/nickname`, `/admin/users/{u}/avatar`, `/admin/users/{u}` | 401, 403; 404 when there is nothing to remove; 400 `You cannot remove your own account here` |

The site's proxies for these answer `invalid_path`, `invalid_request`, `too_large` (notes over 16 KB, pictures
over 512 KB, profiles and publications over 8 KB), `too_many_requests` (the site's own limits, with
`Retry-After`), `invalid_image`, `conflict`, `not_found`, `forbidden`, `unauthorized` and `unavailable`.

The S3 service answers `429 too_many_requests` the same way past 120 writes or 600 reads a minute per address.

### What is thrown inside the backend

| Exception | Thrown by | Becomes |
|---|---|---|
| `ResponseStatusException(status, reason)` | controllers, `requireSubject` / `requireUser` (`BearerAuth.kt`) | that status, `reason` as `detail` |
| `PackageStoreException(message, status?)` | `PackageStoreClient` for every call to the S3 service: `status` is what the service answered, `null` when it was unreachable (`ResourceAccessException`) | 502 `store_unavailable` when it escapes; controllers that catch it answer 502 themselves |
| `IllegalArgumentException` | `require(...)` in `PackageStoreClient` (`Invalid key`, `Invalid state name`, `The state is not valid JSON`), `UserService.updateProfile` (`User not found`), `ProfileRules` (nickname, email), `AvatarValidator`, `NoteService` (`Invalid path`, `The note is empty`) | 400 |
| `ConflictException` | `UserService.updateProfile` for a nickname someone else holds | 409 |
| `SecurityException` | `NoteService.delete` for someone else's note | 403 |
| `NoSuchElementException` | `NoteService.add` for an unknown author or a package that is not in the store | 404 |
| `TooManyNotesException` | `NoteService.add` past the per-minute or per-package limit | 429 (the controller maps it) |
| `MaxUploadSizeExceededException` | Spring's multipart parsing | 413 |
| `MissingRequestHeaderException`, `MissingServletRequestParameterException`, `MissingServletRequestPartException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException` | Spring, before the route runs | 401 for `Authorization`, otherwise 400 |
| `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `NoResourceFoundException` | Spring | 405, 415, 404 |
| anything else (`NullPointerException`, a JPA failure when Postgres is down, …) | | 500 with `errorId`; the stack trace is in the log under `[<errorId>]` |

`JarInspector` does not throw: `inspect()` returns `valid` and a list of `problems` (`The file must be a .jar
archive`, `The file is not a valid jar archive`, `The jar archive could not be read`, `The jar contains no
classes`, `No class in the jar has both run(String): List and close() methods`), which `/upload` joins into its
400 text. `KeycloakTokenVerifier.verify` returns `null` for a token it does not accept
(wrong issuer, expired, bad signature, JWKS unreachable), which `UserService.validateToken` turns into `null`
and the routes into 401.

## 4. S3 service routes (`/api/aws`)

| Route | Refusals |
|---|---|
| `POST /upload`, `POST /upload-data?username=&fileName=`, `POST /edit` | 400 `Invalid username or file name`; 400 the jar's problems; 500 text `S3 upload failed for <file>` / `S3 update failed for <key>` (an edit of a missing object) |
| `GET /files/content?key=`, `GET /files/meta?key=` | 400 `Invalid key`; 404 (empty) when there is no such object |
| `PUT /files/content?key=` | 400 `Invalid key`; 400 the jar's problems for a `.jar` key; 500 text when S3 refuses |
| `DELETE /files/content?key=` | 400 `Invalid key`; 404 when it was not there; 204 when gone |
| `POST /errors?username=&error=&message=` | 400 (empty) for a blank username |
| `GET /files?query=` | 404 (empty) when nothing matched |
| `GET /jars/<name>`, `DELETE /jars/<name>` | 400 (empty) for a name that is not `[A-Za-z0-9._-]+`; 404; `304 Not Modified` for a matching `If-None-Match` |
| `POST /jars/releases?fileName=` | 400 text for a name without one of the release extensions |
| `GET /downloadAmie?keyName=` | 404 `No such object in the bucket` |
| any route S3 fails on | 404 `not_found` (S3 said 404), 502 `store_access_denied` (403), 502 `bucket_missing`, 502 `store_error`, 503 `store_unreachable` |

At start-up the service prints `Spring Boot AWS Identity Check FAILED: …` (no credentials) or
`Bucket '<name>' check FAILED: …` and keeps running; every later call then answers 503 `store_unreachable` or
502 `store_access_denied`. `./run.sh` refuses to start the stack without credentials for that reason.

The exceptions behind these: `S3Exception` (S3 answered; `statusCode()` and `awsErrorDetails().errorCode()`),
its subclasses `NoSuchKeyException` and `NoSuchBucketException`, `SdkClientException` (no answer at all), the
`require(...)` `IllegalArgumentException`s in `S3Service` (`Key must be below uploads/`), and the
`RuntimeException("S3 upload failed …")` wrappers the upload routes catch and turn into their 500 text.

## 5. Where errors end up

- **The desktop app** sends every `ERROR` line of its console to `POST /errors` with its bearer token; the backend
  appends it to `uploads/<user>/state/errors.jsonl` and the website's dashboard lists them under "Errors from your
  app" (`GET /api/errors?username=`, cleared with `POST /api/errors-clear?username=`). Older clients use the
  unauthenticated `POST /handle-login-error`.
- **Backend log**: `logs/backend.log` (`./run.sh logs`). Thrown 5xx are logged at `WARN`/`ERROR` with the route;
  a 500 with `[<errorId>]` and the stack trace. Store failures are logged with the S3 service's status and body.
- **S3 service log**: `logs/s3.log`; S3 failures with the AWS error code.
- **Website server log**: `logs/site.log`; the proxy logs the backend's status and the first 200 characters of its
  body before answering `unavailable`.

## 6. Handling them in a client

1. Read the status first. `4xx` means the request must change (fix the jar, sign in again, use your own
   username); `502`/`503` means the store is down and the same request will work later; `500` means a bug, so
   quote `errorId`.
2. For a problem document, show `detail`; switch on `code`. For plain text (an upload), show the text.
3. Retry only `502`/`503` and connection errors, a few times with a short pause (the Python client does two
   retries, half a second apart and growing).
4. On `401` get a new token (backend tokens last an hour, Keycloak tokens as the realm says) and repeat once.
5. The website's `/api/...` answers never carry the backend's `detail` except for upload refusals; a page that
   needs more than the `error` code should ask the backend through a new proxy route rather than from the browser.
