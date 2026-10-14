---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/getting-started
title: Getting started
---

# Getting started

## What you need

| Requirement | Why | Notes |
|---|---|---|
| JDK 21 | The desktop module pins a Java 21 toolchain | Gradle's foojay resolver downloads one if it is missing |
| Gradle wrapper | `./gradlew` from the repository root | Gradle 9.3, configuration cache on |
| BERT models | `modelBuilder` and `initMod` embed text with the `bge` encoder; `initMod()` runs the NER model | The NER folder (`model.onnx`, `tokenizer.json`) with `bge/` inside it: `/Users/samuel/Documents/onnx/bert` for a Gradle run, or `AMIE_MODELS_DIR` (`BundledModels.kt`). The packaged app ships them in `app/resources/models/bert/` |
| Keycloak (optional) | Sign-in, the Models page's backend card, publishing | Docker on `http://localhost:8080`; without it choose **Continue as guest** |
| Amie backend (optional) | Devices, packages, **Publish** | Address in `AMP/backend.json`; see [Amie backend](/docs/backend-integration) |
| Android SDK (optional) | Only for the Android app | `sdk.dir=...` in `local.properties` |

## Build and run

All commands run from the repository root.

```bash
./gradlew :desktopApp:run                 # run the desktop app
./gradlew :desktopApp:hotRun --auto       # hot reload while editing
./gradlew :desktopApp:compileKotlin       # compile only (fast check)
./gradlew :desktopApp:packageDistributionForCurrentOS   # DMG / MSI / DEB under amieApplication/desktopApp/build/compose/binaries/main/, BERT models included
```

In IntelliJ or Android Studio open the repository root and use the run configurations in the toolbar.

## First start

1. The window stays blank for a moment while the app checks `AMP/session.json`. When a valid session comes back
   you land on the **dashboard** straight away; otherwise the **Welcome** screen opens with **Continue**.
2. With no session you land on the **login page**: sign in with a Keycloak username and password, use
   **Sign in with browser (SSO)**, **Create account**, or **Continue as guest**. See [Signing in](/docs/sign-in).
3. You arrive in the **sandbox** with `main.kts` open. Everything the app writes goes into `AMP/` next to its working
   directory (`amieApplication/desktopApp/AMP/` when started with Gradle); see [The AMP folder](/docs/amp-folder).

## The three screens

- **Sandbox** (console icon in the left sidebar): the editor, the output console and the green **Run** button.
  [The sandbox](/docs/sandbox).
- **Models** (second sidebar icon): the app's resource use and every model's uptime, traffic, latency, validation
  metrics and serving settings. Open to everyone; only the Amie backend card needs a sign-in.
  [Models and metrics](/docs/models).
- **Dashboard** (first sidebar icon): device cards with configure, manage, connect and disconnect screens.
  [Devices](/docs/devices).

The window header shows who is signed in with a **Log out** action; a guest sees **Log in** instead.
