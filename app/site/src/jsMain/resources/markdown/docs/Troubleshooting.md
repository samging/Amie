---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/troubleshooting
title: Troubleshooting
---

# Troubleshooting

## Sign-in

**The app asks me to log in every time.** Before 2026-10-14 the app only had Keycloak's normal refresh token, which
the realm expires after 30 minutes without use, and a Keycloak that was not reachable at start also deleted the
session. Now every sign-in asks for an offline token and an unreachable Keycloak keeps the session. If you still see
the login page: check `AMP/logs.txt` for lines starting with `auth:`. "Keycloak refused the saved session" means
the realm revoked it (sign in again); "couldn't refresh … kept, will retry" means Keycloak is down, see below.

**"Keycloak is not reachable" on the login page.** Run `docker ps`. The Keycloak container restarts in a loop while
its Postgres container is down:

```bash
docker start my-ubuntu-image-postgresql-1
docker start my-ubuntu-image-keycloak-1
curl -s http://localhost:8080/realms/master/.well-known/openid-configuration | head -c 100
```

**"Wrong username or password" with the right password.** Use the username; the realm does not log in by email.

**"Client 'amie' rejected".** The client is confidential: put its secret in `AMP/keycloak.json` or
`KEYCLOAK_CLIENT_SECRET`.

**The browser sign-in hangs.** The realm's frontend URL points at an old LAN address. Fix it under Realm settings →
General in the admin console (see [Keycloak setup](/docs/authentication#stale-frontend-url)).

**Port in use when signing in with the browser.** Change `redirectPort` in `AMP/keycloak.json` and add
`http://localhost:<port>/*` to the client's redirect URIs.

## Sandbox

**`import("…")` without the prefix does not compile.** Kotlin reads it as an import statement. Use
`implementation("group:module:version")`, `dependency("…")`, `environment(SettingsEnv(…))` or `amie.import("…")`.

**"env must be selected with a literal environment(SettingsEnv(...))".** The environment call must be written
literally in the script (`environment(SettingsEnv("name", "profile"))` or `amie.import(SettingsEnv(...))`); it is
read from the text before the script compiles.

**modelBuilder fails to embed.** The encoder folder is missing; see [Getting started](/docs/getting-started#what-you-need).

**metricsChart says there are no metrics.** The model was never built in this `AMP/`, or its name differs from the
folder under `AMP/modules/`.

## Models page and backend

**"The backend rejected the Keycloak token (401)".** The backend was started without the Keycloak verifier
settings, or its `KEYCLOAK_ISSUER` does not match the realm's frontend URL. See [Amie backend](/docs/backend-integration).

**"Connection refused: ensure the Amie backend is running".** Nothing listens at the address in `AMP/backend.json`.
With Keycloak on 8080, run the backend with `AMIE_BACKEND_PORT=8090` and set `baseUrl` accordingly.

**No Publish button.** You are a guest. Publishing uploads under your account, so it needs a Keycloak sign-in;
the metrics and statistics on the page do not.
