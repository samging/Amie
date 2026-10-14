# Amie

The package store for Amie packages and the home of the desktop app's documentation. Three services make the
site work, plus Keycloak for sign-in:

| Service | Folder | Port | What it is |
|---|---|---|---|
| S3 service | `awsS3/` | 8088 | Spring Boot 3, in front of the S3 bucket `setup-demo-sams` (eu-north-1): the only package store |
| Backend | `packageRepository/` | 8080, or 8090 when Keycloak holds 8080 | Spring Boot 4: users (Postgres), packages, device states, error logs; talks to the S3 service over HTTP |
| Website | `app/site/` | 8081 | Kobweb: the store, downloads, dashboard, admin and `/docs`; proxies the backend as `/api/...` |
| Keycloak | Docker | 8080 | realm `master`, client `amie` |

## Running

```bash
./run.sh                    # start the S3 service, then the backend, then the site (skips what already runs)
./run.sh status             # which ports answer, with the addresses to open
./run.sh logs               # follow logs/s3.log, logs/backend.log, logs/site.log
./run.sh --restart          # stop and start again
./run.sh --only backend,s3  # a subset: s3, backend, site
./run.sh stop               # stop all three (the site through kobwebStop)
```

The script needs, and checks for, before it starts anything:

- **JDK 21** at `/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home` (another path: `JAVA_HOME_21`).
  Every module has its own Gradle wrapper; running `./gradlew` by hand needs the same `JAVA_HOME` exported.
- **AWS credentials** for the S3 service: profile `[default]` in `~/.aws/credentials` (another one: `AWS_PROFILE`), or
  `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in the environment. Without them the S3 service starts but every
  listing fails, so the script refuses to start.
- **Postgres** on `localhost:5432` with the database `AmiesUsersDB` (`packageRepository/src/main/resources/application.properties`),
  for the backend's `users` table.
- **Keycloak** on `http://localhost:8080` (a warning only; without it nobody can sign in). When Keycloak holds 8080 the
  backend takes **8090** and the site is told (`AMIE_BACKEND_URL`). The browser-side config and the desktop app
  default to port 8080 for the backend, so on such a machine set `localStorage.setItem("amie.backendUrl",
  "http://<host>:8090")` in the browser and `AMIE_BACKEND_URL=http://<host>:8090` (or `baseUrl` in `AMP/backend.json`)
  for the desktop app.

When the machine's LAN address changes (DHCP), two things go stale: the Keycloak `amie` client only redirects back
to the origins it knows, so the browser sign-in from the new address fails with "Invalid parameter: redirect_uri",
and the backend accepts tokens from one issuer. Fix both with:

```bash
scripts/keycloak-allow-origin.sh          # adds http://<LAN address>:8081/* to the client (or pass an origin)
./run.sh --restart --only backend         # picks up the new address as KEYCLOAK_ISSUER
```

`./run.sh status` prints the current LAN address and the issuer in use.

Logs land in `logs/`, pids in `.run/`; both are ignored by git. The services can still be run one at a time by hand:

```bash
cd awsS3 && ./gradlew bootRun                                              # S3 service, port 8088
cd packageRepository && AMIE_BACKEND_PORT=8090 ./gradlew bootRun           # backend
cd app && ./gradlew :site:kobwebStart                                      # site, port 8081 (kobwebStop to stop)
```

`bootRun.sh` is the old backend-only starter and still works.

## Documentation

`/docs` on the site is the desktop app's documentation: the MkDocs pages of the `amieAppStable` repository
(`docs/*.md`) turned into Kobweb markdown pages under `app/site/src/jsMain/resources/markdown/docs/`. The pages are
generated, not edited here. After the app's docs change, regenerate them and rebuild the site:

```bash
scripts/sync-docs.sh                     # reads ../amieAppStable/docs, or the folder in $AMIE_APP_DOCS / the first argument
cd app && ./gradlew :site:kspKotlinJs    # compiles the pages; a bare <placeholder> outside backticks breaks this
```

The script adds the front matter each page needs (`root`, `routeOverride: /docs/<name>`, the title from the first
heading), rewrites `other.md` and `other.md#anchor` links to `/docs/other`, escapes bare `<placeholders>` outside
code, drops pages that no longer exist in the app docs, and swaps the "built with MkDocs" paragraph of the overview
page for one that fits the site.

## Environment

| Variable | Used by | Meaning |
|---|---|---|
| `AMIE_BACKEND_PORT` | backend | listen port (default 8080; `run.sh` picks 8090 by itself when Keycloak holds 8080 on the same machine) |
| `AWS_PROFILE`, `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | awsS3 | how the S3 service signs its bucket calls (default: profile `default` from `~/.aws/credentials`, region eu-north-1) |
| `JAVA_HOME_21` | run.sh | the JDK 21 to run every Gradle build with |
| `KEYCLOAK_ISSUER` | backend, site server | the `iss` claim of the realm's tokens. The realm has no fixed frontend URL, so it is the address the browser used for Keycloak; `run.sh` defaults to `http://<this machine's LAN address>:8080/realms/master` |
| `KEYCLOAK_JWKS_URI` | backend | where the backend reaches the realm's certs endpoint (default `http://localhost:8080/realms/master/protocol/openid-connect/certs`) |
| `KEYCLOAK_URL` | site server | where the Kobweb server reaches Keycloak (default `http://localhost:8080`) |
| `KEYCLOAK_CLIENT_SECRET` | site server | the confidential `amie` client's secret for `/api/keycloak/token`; never shipped to browsers. Without it every sign-in on the site fails (Keycloak logs `invalid_client_credentials`, the page says sign-in is not available). `run.sh` reads it from the desktop app's `AMP/keycloak.json` when unset |
| `AMIE_SITE_URL` | site server, backend | public origin of the site: the OAuth redirect URI, and the `html_url` links in the backend's package listings (default `http://localhost:8081`) |
| `AMIE_BACKEND_URL` | site server | where the Kobweb server reaches the backend for its `/api/...` proxies (default `http://localhost:8080`) |
| `AMIE_S3_SERVICE_URL` | site server, backend | the awsS3 service in front of the S3 bucket, the only package store (default `http://localhost:8088`) |
| `AMIE_S3_PUBLIC_URL` | awsS3 | the address clients download from; every `download_url` the service lists is built on it (default `http://localhost:8088`) |

## Website pages

| Page | What it does |
|---|---|
| `/` | The package store: search, authors, packages |
| `/downloads` | Jar downloads, listed from the S3 bucket through the site's `/api/jars` and `/api/jar-download` routes |
| `/docs` | The desktop app documentation, generated from `amieAppStable/docs` by `scripts/sync-docs.sh` (see Documentation above) |
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
