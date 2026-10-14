do api for the google disk

Runs on kobweb, before run you must export JAVA_HOME env in order to run locally.
# Start Backend
```./gradlew bootRun -Dorg.gradle.jvmargs="-Xmx2g" &```
# Start Frontend
```./gradlew :site:kobwebStart &```
# Start the S3 service (jar downloads)
```cd awsS3 && ./gradlew bootRun &```

## Environment

| Variable | Used by | Meaning |
|---|---|---|
| `AMIE_BACKEND_PORT` | backend | listen port (default 8080; use 8090 when Keycloak holds 8080 on the same machine) |
| `KEYCLOAK_ISSUER` | backend | the `iss` claim of the realm's tokens: realm frontend URL + `/realms/master` (default `http://192.168.1.114:8080/realms/master`) |
| `KEYCLOAK_JWKS_URI` | backend | where the backend reaches the realm's certs endpoint (default `http://localhost:8080/realms/master/protocol/openid-connect/certs`) |
| `KEYCLOAK_URL` | site server | where the Kobweb server reaches Keycloak (default `http://localhost:8080`) |
| `KEYCLOAK_CLIENT_SECRET` | site server | the confidential `amie` client's secret for `/api/keycloak/token`; never shipped to browsers |
| `AMIE_SITE_URL` | site server, backend | public origin of the site: the OAuth redirect URI, and the `html_url` links in the backend's package listings (default `http://localhost:8081`) |
| `AMIE_BACKEND_URL` | site server | where the Kobweb server reaches the backend for its `/api/...` proxies (default `http://localhost:8080`) |
| `AMIE_S3_SERVICE_URL` | site server, backend | the awsS3 service in front of the S3 bucket, the only package store (default `http://localhost:8088`) |
| `AMIE_S3_PUBLIC_URL` | awsS3 | the address clients download from; every `download_url` the service lists is built on it (default `http://localhost:8088`) |

## Website pages

| Page | What it does |
|---|---|
| `/` | The package store: search, authors, packages |
| `/downloads` | Jar downloads, listed from the S3 bucket through the site's `/api/jars` and `/api/jar-download` routes |
| `/docs` | The desktop app documentation (the MkDocs pages from `amieAppStable/docs`, kept in `app/site/src/jsMain/resources/markdown/docs`) |
| `/navigateto` | Upload a package jar (signed in) |
| `/dashboard?username=` | One author's packages and devices; owners can replace and delete, and see the errors their app reported (`/api/errors`, cleared with `/api/errors-clear`) |
| `/loginpage` | Sign in with username and password, with the browser (SSO), or by scanning a QR code on a phone |
| `/admin` | For Keycloak accounts with the `admin` role (`AMIE_ADMIN_ROLE`): remove any package or build, publish application builds |

### Application downloads

Builds of the desktop app (dmg, msi, deb, zip or jar) live under `releases/` in the bucket and show up first on
`/downloads`. Publish one from `/admin`, or straight to the S3 service:

```
curl -X POST 'http://localhost:8088/api/aws/jars/releases?fileName=amie-pilot-1.0.0.dmg' \
  -H 'Content-Type: application/octet-stream' --data-binary @'amie pilot-1.0.0.dmg'
```

Files over 64 MB stream from the S3 service directly (`amie.s3ServiceUrl` in the browser, default `http://<host>:8088`)
instead of going through the site's cache. The admin routes verify the Keycloak access token on the site's server
(`api/keycloak/KeycloakTokens.kt`) and require the admin role in `realm_access` or the client's `resource_access`.

### Package jars

An uploaded package must be a jar with at least one public class exposing `run(input: String): List<String>`
and `close()` (see `packageRepository/.../shared/packages/AmiePackage.kt`). The upload page checks the file is a
jar in the browser; `POST /upload` inspects the classes (`JarInspector`) and refuses anything else with a 400
and the reason. Accepted jars are stored in the S3 bucket at `uploads/<user>/jar/<file>`; the awsS3 service
(`/api/aws/jars`) serves them for the downloads page.

### The package store

The S3 bucket behind the awsS3 service is the only store. Package files live at `uploads/<user>/<lang>/<file>`,
a user's device state at `uploads/<user>/state/devices.json` and the errors the desktop app reports at
`uploads/<user>/state/errors.jsonl`. The site proxies the backend for all of it: `/api/packages` (the listing),
`/api/view?package=`, `/api/delete-package`, and `GET /api/errors?username=` with the owner's
`Authorization: Bearer` header, which the dashboard shows under "Errors from your app"
(`POST /api/errors-clear?username=` empties the log).

### Caching

Everything the site shows is cached at three levels: the awsS3 service keeps the bucket listing and jar bytes
in memory (Caffeine, `amie.jars.*` properties), the Kobweb server keeps every proxied answer with an ETag
(`ServerCache`, `?refresh=1` bypasses it), and the browser keeps listings in localStorage (`ClientCache`) and
honours the `Cache-Control` headers for jar downloads.

## Sign-in

Users sign in with Keycloak (realm `master`, client `amie`) on the website, in the Android app and in the
amieAppStable desktop app. The backend accepts the realm's access tokens as bearer tokens
(`UserService.validateToken` → `KeycloakTokenVerifier`), creates a `users` row on first contact, and exposes
`GET /me` so a client can learn how the backend names it. Its own `/login` tokens still work.

Keycloak tokens are verified in the browser too (`auth/Jwt.kt`: RS256 signature against the realm's JWKS,
issuer, expiry, client) before a session is kept, and refreshed with the refresh token when they run out. The
QR sign-in uses Keycloak's OAuth 2.0 Device Authorization Grant, which must be enabled on the `amie` client;
without it the QR code simply opens the hosted sign-in page.

In the browser, `config/AmieConfig.kt` derives the backend and Keycloak addresses from the page's host (port
8080). Override per browser with `localStorage.setItem("amie.backendUrl", "http://host:8090")` and
`localStorage.setItem("amie.keycloakUrl", "http://host:8080")`.


## About Project Amie

Project Amie was created to simplify Arduino board management and bridge the gap between microcontrollers and cloud computing platforms. 

> **Note on Project Status:** The project is currently in an unfinished, experimental state and lacks several core features. Because no formal tests have been written yet, expect unexpected runtime bugs.

### Architectural Vision
Bare-metal microcontrollers are incredibly affordable solutions that can act as the perfect "middlemen" for harvesting real-world data—such as voice signals, button events, and general IoT telemetry. Through Amie's Android UI, users can seamlessly manage, track, and toggle these connected Arduino boards.

### Planned Features
* **Multi-threaded Execution Engine:** A dedicated engine to continuously track, read from, and respond to multiple Arduino boards simultaneously.
* **Arduino Board Execution Platform:** Outsourcing harvested sensor and peripheral data to high-level cloud computation nodes.

---

## Features & UI Overview

### 1. Device Dashboard & Management
Provides a straightforward interface for creating devices and managing their operational states. It is fully compatible with any Android emulator or physical Android phone.

* **Configure (Beta):** An essential hub for viewing ongoing device processes and debugging (currently a work in progress).
* **Manage:** Dedicated space to configure device names, ports, and endpoints, as well as view live device logs.

<img src="Screenshot%202026-07-04%20at%2020.51.09.png" alt="Main Interface" max-width="500px" width="50%" />

### 2. Packages Overview
Allows you to download online firmware packages directly from a central repository. While custom package uploads are not yet supported, the download and deployment system is fully functional.

<img src="Screenshot%202026-07-04%20at%2020.52.32.png" alt="Task Flow" max-width="500px" width="50%"/>

### 3. Terminal Shell (Upcoming)
An integrated runtime terminal where you will be able to view live stream logs, issue interrupt commands, and interact directly with Arduino boards during execution.

<img src="Screenshot%202026-07-04%20at%2020.53.07.png" alt="Logs and Execution" max-width="500px" width="50%" />

---

## Tech Stack & Libraries
* **Backend:** Spring Boot
* **Mobile Frontend:** Android Material UI, Ktor Client
* **Integrations:** Google API, various serialization libraries

---
