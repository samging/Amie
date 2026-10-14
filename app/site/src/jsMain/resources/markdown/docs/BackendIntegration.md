---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/backend-integration
title: Amie backend and website
---

# Amie backend and website

The desktop app, the Amie website (Kobweb site on port 8081) and the Amie backend (Spring Boot
`packageRepository`) share one identity: the Keycloak realm `master`, client `amie`. Since 2026-10-14 the
backend accepts Keycloak access tokens directly, so the account you sign in with in the app is the same account
the website shows, and what you publish from the app appears on the website's dashboard.

Since the same day the backend keeps its files in the Amie S3 bucket (reached through the awsS3 service on port
8088) instead of a GitHub repository: your packages under `uploads/<user>/<lang>/<file>`, your saved states under
`uploads/<user>/state/<name>.json` and the error log of your app under `uploads/<user>/state/errors.jsonl`. The
endpoint names the app calls did not change (`/list-github` is kept as an alias of the new `/packages`), and no
`GITHUB_TOKEN` is needed anywhere anymore.

## Configuration

`AMP/backend.json` (written with defaults on first start; no secrets, so it can be committed):

```json
{
  "baseUrl": "http://192.168.1.114:8080",
  "siteUrl": "http://192.168.1.114:8081",
  "s3ServiceUrl": "http://192.168.1.114:8088"
}
```

`AMIE_BACKEND_URL`, `AMIE_SITE_URL` and `AMIE_S3_URL` override the file. The S3 service is only asked for a
package jar the website cannot serve. `BackendConfig.load()` (`backend/BackendConfig.kt`) is
the single place the addresses come from; nothing else in the app hardcodes a host.

On a machine where Keycloak already listens on 8080, start the backend on another port (it reads
`AMIE_BACKEND_PORT`) and point `baseUrl` there, for example `http://localhost:8090`.

## What talks to the backend

| Code | Endpoint | Token | Used by |
|---|---|---|---|
| `render/templates/Client.kt` `rest()` | `/get-device-status`, `/update-device-status`, `/fetch-endpoints`, `/list-github`, `/list-github-metadata`, `/handle-login-error` | bearer when signed in | device dashboard, endpoints, plugins, device terminal |
| `backend/AmieBackend.kt` | `/me`, `/user-packages`, `/upload` | bearer, required | Models page: **Publish** on a model component |
| `backend/AmieBackend.kt` | `/errors` (`POST`, `GET`) | bearer; a guest's report goes to `/handle-login-error` | `ErrorReporter`: every ERROR console line |
| `backend/AmieBackend.kt` | `/state`, `/state/{name}` (`GET`, `PUT`, `DELETE`) | bearer, required | the sandbox's `saveState` / `loadState` / `listStates` |
| `util/DownloadService.kt` | `/list-github` | none | package list text |

`Client.rest()` attaches `Authorization: Bearer <Keycloak access token>` through `AuthState.accessToken()`, which
refreshes the token when it is about to expire. Guests send no token. An absolute URL passed as the slug is used
as is; a path is joined to `baseUrl`. Failures land in `lastErrorMessage` and the dialog flags; only failures the
server answered are reported to `/handle-login-error`.

**Devices** are stored on the backend per username. `AppBaseTemplate` calls `DeviceManager.setSession` with the
Keycloak username whenever the session changes, so the backend sees the real user instead of a guest id. When the
backend is unreachable the local `AMP/componentSettings.json` is kept (it used to be overwritten with `{}`).

## Models page and package jars

The Models page shows no backend status; it uses the Keycloak session for one thing: **Publish** on a model
component calls `AmieBackend.publishModule`, which uploads `model.onnx` and `module.json` through `POST /upload`
with `progLanguage=onnx` (stored as `uploads/<user>/onnx/<file>` in the S3 bucket). The backend answers 200
"File uploaded successfully", 400 with the reasons it refused a jar (a package jar must have `run(String): List`
and `close()`) or 502 when the store is unreachable; the page shows the reason. A guest pressing Publish is sent to
the sign-in page; **Open website** in the header opens `siteUrl/dashboard`.

Package jars flow the other way and need no token. `initMod("name.jar")` in the sandbox fetches a jar the server
lists (the website's `/api/jars` and `/api/jar-download?name=`, else the S3 service's `/api/aws/jars` and
`/api/aws/jars/<name>`) into `AMP/pluginDir` and opens a new instance of it; `packages.available()` lists what the
server offers. The S3 service address is `s3ServiceUrl` in `AMP/backend.json` (`AMIE_S3_URL` overrides it; default
`http://192.168.1.114:8088`). See [Packages](/docs/sandbox-scripting#packages).

## Error reports

Every ERROR-level console entry (`logError`, `logInternal(..., LogLevel.ERROR)`, a script's `error("...")`) is also
sent to the backend when a user is signed in: `logToConsole` hands it to `backend/ErrorReporter.kt`, which queues
it and posts `{"error": <source>, "message": <text>}` to `POST /errors` from a background coroutine, so the console
never waits. The same source and message are sent at most once a minute, messages are cut to 2000 characters, and
guests are not reported (a guest's failed backend call still goes to `/handle-login-error` as before). A report
that fails is written to the debug log only; it never becomes another console error. The backend appends each
report to `uploads/<user>/state/errors.jsonl` (the last 200 are kept), the website's dashboard shows them to the
owner under **Errors from your app** with a Clear button, and `AmieBackend.errors()` reads the same list
(`GET /errors?username=`, newest first, `{time, error, message}`).

## Saved states

The backend keeps small JSON documents per user under `uploads/<user>/state/<name>.json`; your devices
(`AMP/componentSettings.json`, synced through `/update-device-status`) are stored there as `devices`. The app reaches
them through `AmieBackend`: `listStates()` (`GET /state`, the names), `loadState(name)` (`GET /state/{name}`, the
JSON text or null), `saveState(name, json)` (`PUT /state/{name}`, any JSON up to 1 MiB) and `deleteState(name)`
(`DELETE /state/{name}`, false when there was none). A name matches `[A-Za-z0-9._-]{1,64}`. Scripts get the first
three as `saveState`, `loadState` and `listStates`, see [Saved states](/docs/sandbox-scripting#saved-states).

## Backend side (Amie repository)

`packageRepository` validates bearer tokens in `UserService.validateToken`: first as a Keycloak token
(`KeycloakTokenVerifier`: RS256 signature against the realm's JWKS, issuer check, expiry), then as one of its own
HS512 login tokens. A Keycloak user gets a `users` row on first contact. Properties:

```properties
server.port=${AMIE_BACKEND_PORT:8080}
amie.keycloak.issuer=${KEYCLOAK_ISSUER:http://192.168.1.114:8080/realms/master}
amie.keycloak.jwks-uri=${KEYCLOAK_JWKS_URI:http://localhost:8080/realms/master/protocol/openid-connect/certs}
```

The issuer must equal the `iss` claim Keycloak writes, which is the realm's **frontend URL** plus `/realms/master`.
The JWKS URI is where the backend process can reach Keycloak, which may be a different host. If the realm's
frontend URL changes, change `KEYCLOAK_ISSUER` with it.

New endpoint `GET /me` (bearer) returns `{id, username, fullName, email, bio}` for the token's user; `GET /profile`
returns the same shape and no longer includes the password hash.

## Website side

The site reads its addresses from `config/AmieConfig.kt`: backend and Keycloak default to the host the page was
loaded from on port 8080, overridable per browser with `localStorage.setItem("amie.backendUrl", ...)` and
`localStorage.setItem("amie.keycloakUrl", ...)`. The Keycloak token exchange moved to the site's server
(`/api/keycloak/token`), which adds the client secret from `KEYCLOAK_CLIENT_SECRET`, so the secret is no longer in
browser JavaScript and the password login on the site works again. The server also reads `KEYCLOAK_URL` and
`AMIE_SITE_URL` for the redirect URI.
