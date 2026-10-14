---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/sign-in
title: Signing in
---

# Signing in

You sign in **once**. The app keeps the session in `AMP/session.json` and signs you back in on every start, for as
long as Keycloak's offline session allows (30 days without use by default, renewed each time the app uses it). You
only see the login page again when you pressed **Log out**, when Keycloak refused the saved session (for example
the account was disabled), or after a month without opening the app.

## The login page

| Button | What happens |
|---|---|
| **Continue** | Username and password are sent to Keycloak (`grant_type=password`). Use the **username**, not the email address: the realm does not log in by email. |
| **Sign in with browser (SSO)** | Keycloak's own login page opens in your browser; it handles identity providers, one-time codes and required actions, then returns to the app on `http://localhost:48080/callback`. |
| **Create account** | A registration form inside the app (first name, last name, email, username, password). With *Verify email* on in the realm you get a mail first; open its link, then sign in with the username. |
| **Register in browser** | Keycloak's hosted registration page. |
| **Forgot password?** | Keycloak's reset page in the browser. |
| **Continue as guest** | No server. The app uses the name `guest-desktop-user`. Guests can use the sandbox, the Models page and local metrics; they cannot publish to the backend or sync devices under their own name. |

## How the session is kept

- Every sign-in asks Keycloak for the scopes `openid profile email offline_access`. The last one makes Keycloak issue
  an **offline refresh token**, the same mechanism "remember me" uses in other apps: it does not die with the
  browser-style SSO session (30 minutes idle), only with the realm's offline session idle timeout.
- The access token itself is short-lived (60 seconds in this realm). On start the app refreshes it with the offline
  token. When Keycloak **cannot be reached** the session is kept and the refresh is retried on the next call that
  needs a token, so a stopped Keycloak container no longer logs you out.
- The session is dropped only when Keycloak answers and refuses the token, or when the realm in `AMP/keycloak.json`
  changed.
- **Log out** in the window header ends the Keycloak session and deletes `AMP/session.json`.

The realm and client settings this relies on (direct access grants, standard flow, the `offline_access` role the
realm gives new users, redirect URIs) are listed in [Keycloak setup](/docs/authentication).

## Where the session is used

- The **Models** page shows "signed in as …" and the **AMIE BACKEND** card; **Publish** uploads under your account.
- The **dashboard** stores devices on the backend per user.
- Every call to the Amie backend carries the Keycloak access token as a bearer token.
