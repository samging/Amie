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
  backend takes **8090** and the site is told (`AMIE_BACKEND_URL`). The website's pages only ever call the site's own
  server (`/api/...`), which forwards to the backend, so the browser needs no setting. The desktop app defaults to port
  8080 for the backend, so on such a machine give it `AMIE_BACKEND_URL=http://<host>:8090` (or `baseUrl` in
  `AMP/backend.json`).

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
scripts/docs-index.sh                    # (run by sync-docs.sh) rebuilds the headings index the site search uses
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
| `KEYCLOAK_ISSUER` | backend | the `iss` claims the backend accepts, comma-separated. The realm has no fixed frontend URL, so a token's issuer is the address its client used for Keycloak: localhost for the desktop app and the site server, the LAN address for a browser on the LAN. `run.sh` defaults to `http://localhost:8080/realms/master,http://127.0.0.1:8080/realms/master,http://<this machine's LAN address>:8080/realms/master` |
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
| `/` | The package store: every file of every author in one list (`/api/all-packages`), filtered as you type, with kind chips, sorting, author cards, recently viewed files, and a store-wide search for wildcards such as `*jar` |
| `/downloads` | The desktop app builds (the one for the visitor's own system first, with install hints and MD5 checksums) and the package jars, listed from the S3 bucket through the site's `/api/jars` and `/api/jar-download` routes |
| `/docs` | The desktop app documentation, generated from `amieAppStable/docs` by `scripts/sync-docs.sh` (see Documentation above); a page finder in the sidebar, "On this page" links, previous/next pages, a Copy button on every code block |
| `/view?package=` | One file: breadcrumbs, line numbers, wrapping, pretty-printed JSON, copy; jars show their classes and a snippet for the app; other files by the same author |
| `/navigateto` | Upload a package jar (signed in): three steps, the browser's own jar check, a warning when the name already exists, a progress bar |
| `/edit?package=` | Replace one of your files; shows a progress bar and lands on the dashboard with a confirmation |
| `/dashboard?username=` | One author's packages and devices with the numbers at a glance; owners can replace and delete (with a confirmation step), and see the errors their app reported (`/api/errors`, cleared with `/api/errors-clear`) |
| `/loginpage` | Sign in with username and password (show/hide, Caps Lock warning, remembered username), with the browser (SSO), or by scanning a QR code on a phone; says what you were about to do when sent here |
| `/profile` | Name, email and a few words, with the session and package count; changes can be discarded before saving |
| `/admin` | For Keycloak accounts with the `admin` role (`AMIE_ADMIN_ROLE`): remove any package or build (with a confirmation step), publish application builds |
| any other address | A 404 page with suggestions for the page that was probably meant |

Everywhere: `⌘K` / `Ctrl+K` (or the magnifier in the header) opens a search over pages, authors, packages, docs pages
and docs sections (`scripts/docs-index.sh` writes that index into `DocsIndex.kt`; `sync-docs.sh` runs it); `/` jumps to
the page's own filter box; copy actions confirm with a small toast; the header marks the current section.

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
`/api/all-packages` (every author's files merged into one list, which the store page and the search use),
`/api/upload` and `/api/edit` (multipart uploads passed on with the bearer), `/api/profile` (read and save; a
single sign-on account's backend row is created on first use through the backend's `/me`), `/api/session` (is
the token still good), `/api/view?package=`, `/api/delete-package`, and `GET /api/errors?username=` with the owner's
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

The desktop app signs the browser in with its own session: **Open website** in the app posts the app's tokens to
`POST /api/sso/handoff` (`api/sso/Handoff.kt`), which keeps them for two minutes behind a one-time code, and opens
`/sso?code=<code>&next=<path>` (`pages/Sso.kt`). The page claims the code with `POST /api/sso/claim`, verifies the
tokens like any other sign-in and continues to `next`; the tokens never appear in a URL.

In the browser, `config/AmieConfig.kt` derives the backend and Keycloak addresses from the page's host (port
8080). Override per browser with `localStorage.setItem("amie.backendUrl", "http://host:8090")` and
`localStorage.setItem("amie.keycloakUrl", "http://host:8080")`.


## About Project Amie

Project Amie was created to simplify Arduino board management and bridge the gap between microcontrollers and cloud computing platforms. 

> **Note on Project Status:** The project is experimental and several features are unfinished. The backend and the
> S3 service have unit tests (Mockito, see "Testing" below) and there is an end-to-end check of sign-in,
> registration, publishing and SSO against the running stack; expect rough edges elsewhere.

## Browsing files and models on the site

The packages page and every author's dashboard show the store as a **tree**: the author, the folders the store
files packages under (`jar`, `onnx`, `python`, …) and the files, each with View, Download and, for the owner,
Replace and Delete. **Table** next to the tree switches back to the flat list; the choice is kept in the browser.

Opening an `.onnx` file shows the model's structure: nodes, parameters, inputs → outputs, opset and producer as
tiles, a chip per operator type that filters the graph, and a tree of Inputs, the Graph (every node in order with
its inputs, outputs and attributes; weights named where a node reads them), Outputs, Weights and Metadata. The
structure is read on the site's server by `api/onnx/OnnxReader.kt`, a protobuf walker that needs no ONNX library
and never copies tensor contents, and served by `GET /api/model-info?package=` (cached five minutes; `422` for a
file that is not ONNX, `404` for a missing one, files up to 256 MB). Every branch of the tree starts closed.

The server also recognises the **blocks** a transformer is made of (`api/onnx/OnnxPatterns.kt`, the `patterns`
list of the answer): each scaled dot-product attention (the scores `MatMul` of two activations, its scale and
mask, the `Softmax`, the context `MatMul`, and the query, key, value and output projections around them),
onnxruntime's fused `Attention`/`MultiHeadAttention`, feed-forward blocks (projection, activation, projection,
gated ones too), and normalizations, fused or spelled out as `ReduceMean → Sub → Pow → … → Div`. They show as
a "Blocks" branch with every node's role, as a tile ("Attention 12 · 12 feed-forward · 25 norm"), as chips
that narrow the graph to one kind of block, and as a badge on each node that belongs to one. Operator families
(linear, activation, normalization, shape, arithmetic, convolution) and block kinds each have one muted colour,
the same in light and dark mode. `./gradlew :site:jvmTest` in `app/` runs the reader's and the detector's
tests. Other model formats (`.bin`, `.pt`, `.safetensors`) only get the download button.

A headless walkthrough of the site (sign-in through the app's SSO handoff, the trees, the model page, every file
kind) is what `scripts/seed-site-content.py` is for: it publishes a jar, the desktop app's dummy ONNX model and a
few text files under a throwaway user and prints the user's tokens; `--cleanup <seed.json>` removes it all again.

## Testing

Unit tests (JUnit 5 + Mockito, no services needed):

```bash
cd packageRepository && ./gradlew test     # the backend: controllers, user service, token verifier, package store client, the error handler
cd awsS3 && ./gradlew test                 # the S3 service: controllers, S3Service, jar inspection, the identity check, the error handler
```

Both need `JAVA_HOME` at the JDK 21 from "Running"; `--offline` works, nothing is downloaded. The error answers
are covered by `ApiExceptionHandlerTest` in each service (every thrown exception and the problem document it
becomes, also through a real route) and `DeviceControllerTest` (how a failed repository action maps to a status).

The backend's Spring context test (`AmiePackageRepositoryApplicationTests`) is left out by default because it needs
Postgres and the Google Drive credentials; `-PexcludeTests=''` includes it.

End to end, against the running stack (`./run.sh` first; Keycloak in docker, Postgres, AWS credentials):

```bash
python3 scripts/e2e-auth-publish.py
```

It registers and signs in a backend account (also through the site's `/api/login` and `/api/register`), creates a
throwaway Keycloak user through the admin API, signs it in the way the desktop app does (password grant, refresh,
logout, and the browser flow with PKCE on the loopback redirect), checks that the backend accepts the Keycloak
token (`/me`), publishes a package jar (`/upload` with the token, the site's `/api/upload` proxy, the listings,
`DELETE /upload`), refuses a bad jar and another user's name, keeps and reads a saved state and an error entry,
and runs the site's SSO handoff (`/api/sso/handoff` → `/api/sso/claim`, once only) and `/api/keycloak/token`.
Every check prints PASS or FAIL; everything it created is removed at the end (the Keycloak user, the backend
accounts, the rows the backend made for them, the jar in the bucket).

## Notes, nicknames and profile pictures

Signed-in people can leave **notes** on any package (the "Notes" section of `/view?package=`), choose a
**nickname** that is shown instead of their username, and upload a **profile picture** (both on `/profile`).
Notes and profiles live in the backend's Postgres (`package_notes` table; `nickname`, `avatar`, `avatar_type`
columns of `users`); the S3 bucket keeps holding only files.

| Backend route | Who | What |
|---|---|---|
| `GET /notes?path=` | anyone | the notes on a package, newest first |
| `POST /notes` `{"path","text"}` | signed in | adds a note (201 with the note); 404 for a package that is not in the store, 429 after ten notes in a minute |
| `DELETE /notes/{id}` | the author or the package's owner | removes a note; 403 for anyone else |
| `GET /profile?username=` | anyone | now also `nickname`, `hasAvatar`, `avatarVersion` |
| `POST /profile?username=` `{"fullName","email","bio","nickname"}` | the person | 409 when the nickname is another account's username or nickname |
| `GET /avatar?username=` | anyone | the picture, served with its detected type, `nosniff` and a sandboxing CSP; 404 when none |
| `POST /avatar?username=` (multipart `file`), `DELETE /avatar?username=` | the person | sets or removes the picture |

The site proxies them as `/api/notes?package=` (GET, POST `{"text"}`), `/api/notes-delete?id=`,
`/api/avatar?username=` (GET, POST multipart) and `/api/avatar-delete?username=`; the browser never calls the
backend. The Python example client has `notes`, `nick` and `avatar` commands for all of it.

### Security measures

Everything a person types or uploads is treated as hostile, on the server, whatever the page checked:

- **No HTML from users, ever.** Notes, nicknames, names and bios are stored as plain text and rendered by the
  site as DOM text nodes (`Text(...)` in Compose HTML), never through `innerHTML`; a note of
  `<img src=x onerror=alert(1)>` is shown as those characters. There is no Markdown and links are not made
  clickable. `ProfileRules` (`shared/profile/`) additionally strips control characters, zero-width characters
  and bidi overrides (so `evil.jar` cannot be made to read as `raj.live`), collapses whitespace and cuts every
  field to its limit (nickname 32, name 80, email 120, bio 280, note 2000 characters).
- **Nicknames cannot impersonate.** 2 to 32 letters, digits, spaces, dots, dashes or underscores, and never
  another account's username or nickname (case-insensitive): 409 `conflict`.
- **Pictures are what their bytes say.** `AvatarValidator` accepts only PNG, JPEG, GIF and WebP by signature
  (SVG and HTML are refused however they are named), at most 512 KB and 2048 × 2048 pixels (the header is
  measured without decoding, so a decompression bomb is refused before it is decoded), and the detected type
  is the one served. The picture comes back with `X-Content-Type-Options: nosniff`,
  `Content-Disposition: inline` and `Content-Security-Policy: default-src 'none'; sandbox`, on the backend
  and again on the site's proxy, which also refuses to serve anything that is not one of the four types.
- **Authorization on the server.** A note needs a valid bearer token; only its author or the package's
  owner can remove it; nicknames and pictures can only be changed by their owner (`requireUser`); the site
  passes the person's token through and never decides on its own. Tokens travel in the `Authorization` header,
  not in cookies, so there is no CSRF surface for these routes.
- **Rate and size limits.** Ten notes a minute per person, two hundred per package, 16 KB request bodies on
  the notes proxy, 512 KB pictures (checked three times: the page, the proxy, the backend).
- **Keys stay below `uploads/`.** A note's package path is validated like every other key (`isAllowedKey`),
  and the note is only stored when the store confirms the package exists.
- **Nothing leaks.** Problem documents name what was wrong, never a stack trace; the proxies translate backend
  answers into their short codes; 500s carry an id for the log.
- **Pictures need an administrator's approval.** A new picture is stored as *waiting*: `GET /avatar` answers
  404 for it, the profile says `avatarPending`, and only its owner (with their bearer, through
  `/api/avatar-preview`) and administrators (`GET /admin/avatars/pending`, the "Pictures waiting for approval"
  panel) can see it. `POST /admin/users/{u}/avatar/approve` makes it public; Reject removes it. Nothing a
  stranger uploaded is ever shown to other visitors before a person looked at it.
- **Timeouts.** Tomcat waits at most 10 s for a request line and headers (`server.tomcat.connection-timeout`),
  keeps an idle connection 15 s, takes 500 connections and 64 threads (backend; 300 and 48 for the S3 service),
  caps headers at 16 KB and multipart parts at 20, and ends async requests after 60 s (10 min for the S3
  service's big uploads). The site's own clients time out after 10 s to connect and 30 to 60 s to answer (10 min
  for a package upload), and the linked-account lookups after 5 and 10 s with redirects disabled.
- **Rate limits.** Every service keeps token buckets per client address (`RateLimitFilter`, `RateLimit.kt`):
  on the backend 10 sign-in attempts a minute (`/login`, `/register`), 20 uploads, 60 other writes, 300 reads;
  on the S3 service 120 writes and 600 reads; on the site 10 sign-ins, 20 uploads, 60 writes and 30 heavy reads
  (model structure, linked accounts) a minute. Over a limit the answer is `429` with `Retry-After` and the
  problem document (`too_many_requests`). The site forwards the browser's address as `X-Forwarded-For`, and
  the backend counts it only when the caller is on this machine or a private network, so an outside caller
  cannot pretend to be someone else. The numbers are properties (`amie.ratelimit.*`) and environment
  variables (`AMIE_LIMIT_SIGNIN`, `AMIE_LIMIT_UPLOAD`, `AMIE_LIMIT_WRITE`, `AMIE_LIMIT_HEAVY`;
  `AMIE_TRUST_FORWARDED=1` when the site itself sits behind a reverse proxy).

## Linked accounts, publications and badges

On `/profile` a person can enter their **GitHub** and **Hugging Face** usernames. The dashboard then shows an
"Elsewhere" section with their twelve most recently pushed public repositories and most downloaded public
models, fetched by the site's server (`GET /api/linked?username=`, cached ten minutes) from the two public
APIs, never by the browser. The handles are validated against the services' username rules on the backend
(`ProfileRules.githubLogin`, `huggingFaceLogin`; a pasted profile address is reduced to the handle), so the
server only ever calls `api.github.com/users/<login>/repos` and `huggingface.co/api/models?author=<login>`,
and only `https://github.com/…` and `https://huggingface.co/…` links are rendered. Nothing is verified or
signed in: the section says "as stated by the author".

**Publications** (papers, theses, articles) are added on `/profile` and listed on the dashboard:
`GET /publications?username=`, `POST /publications` `{title, venue, year, url, doi}`, `DELETE
/publications/{id}` (the owner; an administrator through `DELETE /admin/publications/{id}`). A title is
required, the year must be 1900 to 2100, a link must be `http(s)` (so a `javascript:` address can never be
stored) and a DOI must look like `10.xxxx/…`; at most 50 per person. The site proxies them as
`/api/publications` and `/api/publications-delete?id=`.

**Badges** are granted and revoked by administrators only, from a fixed catalogue (`BadgeCatalog`: verified,
maintainer, contributor, researcher, helper, early adopter): `GET /admin/badges`, `POST` / `DELETE
/admin/users/{u}/badges/{badge}` (site: `/api/admin-badge?username=&badge=&action=grant|revoke`). A profile's
`badges` list shows on the dashboard, the profile page and in the admin's People table, where each badge is
a chip that toggles.

## Administration

`/admin` is for Keycloak accounts whose access token carries the **admin** role (a realm role or a role of the
`amie` client; `AMIE_ADMIN_ROLE` changes the name). The page shows app builds and the bucket, the package store
with a Remove on every file, **People** (every account with picture, nickname, how they sign in and how many
notes they wrote; an admin can clear a nickname, remove a picture or delete an account with its devices,
notes and activity) and the **latest notes** across all packages, each with a Remove.

An administrator also has an **Inbox**: a red counter next to "Inbox" in the header (`/api/admin-inbox`,
refreshed every half minute) and an Inbox card on their own `/profile`, listing the pictures waiting for
approval and the notes of the last day. Every waiting picture can be viewed at full size (the thumbnail or
"View" opens it over the page with its pixel size, Approve and Reject); the bytes are fetched with the
admin's bearer through `/api/avatar-preview`, since the picture is not public until approved.

The role is verified twice: the site's server checks the token's `realm_access` / `resource_access` before it
forwards anything (`api/keycloak/KeycloakTokens.kt`), and the backend checks the same claims again on every
`/admin/...` route (`shared/admin/AdminAccess.kt`), so a call that reaches the backend directly is held to the
same rule. Tokens the backend issues itself carry no roles and can never be admin. Every admin action is
logged with the admin's username; an admin cannot delete their own account from the panel.

| Backend route (bearer with the admin role) | What |
|---|---|
| `GET /admin/me` | who the admin is and their roles |
| `GET /admin/users` | every account: profile (never the password hash), note count, local password or SSO |
| `GET /admin/notes?limit=` | the latest notes, newest first |
| `DELETE /admin/notes/{id}` | removes any note |
| `DELETE /admin/users/{u}/nickname`, `DELETE /admin/users/{u}/avatar` | takes a nickname or picture away |
| `DELETE /admin/users/{u}` | removes the account and what points at it (not its files in the store) |
| `GET /admin/inbox` | what waits for an administrator: the pictures to approve and the notes of the last day, with counts |
| `GET /admin/avatars/pending`, `POST /admin/users/{u}/avatar/approve` | the pictures waiting for approval, and approving one (reject = `DELETE …/avatar`) |
| `GET /admin/badges`, `POST` / `DELETE /admin/users/{u}/badges/{badge}` | the badge catalogue, granting and revoking |
| `DELETE /admin/publications/{id}` | removes anyone's publication |

The site's `/api/admin-users`, `/api/admin-notes`, `/api/admin-note-delete?id=`, `/api/admin-avatars`,
`/api/admin-badges`, `/api/admin-badge?username=&badge=&action=`, `/api/admin-publication-delete?id=` and
`/api/admin-user-action?username=&action=clear-nickname|clear-avatar|approve-avatar|delete` forward these
with the admin's own bearer. The Python client's `admin` commands call the backend directly (sign in with `keycloak`, not `login`).

## Example scripts

`scripts/examples/` holds small clients to copy from, each handling every answer the backend can give
(see [`scripts/examples/README.md`](scripts/examples/README.md)):

```bash
scripts/examples/make-package-jar.sh echo-package.jar                         # the smallest valid package jar (--broken: one the store refuses)
AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/publish-package.sh echo-package.jar   # sign in, upload, verify; one sentence per status
python3 scripts/examples/amie_client.py login sam secret                        # a Python client/CLI with AmieError subclasses and retries
python3 scripts/examples/amie_client.py state put devices '{"uno":{"name":"Uno","port":"/dev/tty.usb1"}}'
AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/provoke-errors.sh          # every refusal, one line each: status, code, detail
```

## Error handling

Anything a backend or S3 service route throws becomes one JSON shape, an RFC 9457 problem document
(`application/problem+json`) with a sentence for people and a code for programs:

```json
{"type":"about:blank","title":"Forbidden","status":403,"detail":"The token belongs to another user",
 "instance":"/upload","code":"forbidden","timestamp":"2026-10-15T16:02:11Z"}
```

| status | `code` | when |
|---|---|---|
| 400 | `bad_request` | a missing or malformed parameter, header, body, path or state name; `detail` names it |
| 401 | `unauthorized` | no bearer token, or one that is expired or not from this backend / the Keycloak realm |
| 403 | `forbidden` | the token is valid but the request names another user |
| 404 | `not_found` | no such file, state, profile or route |
| 409 | `conflict` | `/register` with a taken username |
| 413 | `payload_too_large` | over the 100 MB upload limit or the 1 MiB state limit |
| 502 | `store_unavailable`, `bad_gateway` | the S3 service refused or is unreachable; retry later |
| 502 / 503 | `store_access_denied`, `bucket_missing`, `store_error`, `store_unreachable` | the S3 service itself: credentials, bucket, an S3 outage, no network |
| 500 | `internal_error` | a bug; the body's `errorId` is next to the stack trace in `logs/backend.log` or `logs/s3.log` |

Two kinds of answer stay plain text on purpose: a refused jar (`POST /upload`, 400 with the inspector's reasons,
which the website shows as they are) and `/edit`, which the desktop app expects to answer 200 with
`File updated successfully` or `Error updating file: …`. The website's own `/api/...` routes answer
`{"error":"<code>"}` (`unauthorized`, `forbidden`, `not_found`, `too_large`, `unavailable`, …) and never pass
the backend's text through except for that upload refusal. The desktop app reports its own errors to
`POST /errors`; they show on the dashboard. Every route's refusals, the exceptions behind them
(`ResponseStatusException`, `PackageStoreException`, `S3Exception`, …) and what a client should do are in
[`ERRORS.md`](ERRORS.md); `scripts/examples/provoke-errors.sh` prints them live.

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
