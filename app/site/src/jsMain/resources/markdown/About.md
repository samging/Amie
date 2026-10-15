---
root: .components.layouts.MarkdownLayout
routeOverride: /about
title: About
---

# About Amie

Amie simplifies Arduino board management and bridges the gap between microcontrollers and cloud computing.
Bare-metal boards are affordable "middlemen" for harvesting real-world data such as voice signals, button events
and general IoT telemetry; Amie lets you manage, track and toggle those boards, and share the packages that run
on them.

## What is here

- **Packages**: firmware and tooling packages published by Amie users, with a search by name or extension.
- **Downloads**: ready-made package jars, served from Amie's storage bucket and checked on upload to expose
  `run(text)` and `close()`.
- **Docs**: how to use the Amie desktop app: signing in, the sandbox, models, serving and devices.
- **Upload**: sign in to publish your own package jar.

## The pieces

| Part | Role |
|---|---|
| Website (this site) | Browse, download and upload packages; read the docs |
| Amie backend | Accounts, the package store, device statuses, upload validation |
| S3 service | Lists and serves the published jars |
| Keycloak | Single sign-on for the website, the desktop app and the Android app |
| Desktop app | Train intent models, serve them, manage devices |

Sign in with your Amie account, or scan the QR code on the sign-in page with your phone.

## Finding your way

- Press **⌘K** (or **Ctrl+K**) on any page, or use the magnifier in the header, to search packages, authors, the
  documentation and its sections at once. Type **/** to jump to the filter box of the page you are on.
- The packages page filters as you type; **Search the store** asks the server, which understands wildcards such as
  `*jar`.
- Every file has its own page with a copyable link; jars show the classes inside and a snippet for the app.
- Deleting or replacing a package always asks once before anything changes.
