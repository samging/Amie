---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/
title: Amie desktop app
---

# Amie desktop app

Amie is a desktop workbench for small intent models. You write a Kotlin script in the **sandbox**, it trains a
classifier on top of a BERT encoder, the result lands in the `AMP/` folder next to the app, and from there you can
read its metrics, draw them as charts in the console, serve the model over HTTP, and open package jars from the Amie server.
Devices connected to the Amie backend get their own dashboard.

These pages describe the Amie desktop app (`amieAppStable`). The same text lives as Markdown in that repository's `docs/` folder.

## Where to go

| I want to… | Read |
|---|---|
| Build and run the app for the first time | [Getting started](/docs/getting-started) |
| Write and run a script, read the console | [The sandbox](/docs/sandbox) |
| Open a model or a package, ask it things, keep it running, close it | [Running sessions](/docs/sessions) |
| Know every function a script can call | [Scripting API](/docs/sandbox-scripting) |
| Train a model and look at its metrics | [Models and metrics](/docs/models) |
| Put a model behind an HTTP endpoint | [Serving a model](/docs/serving) |
| Manage devices on the dashboard | [Devices](/docs/devices) |
| Know what the app writes to disk | [The AMP folder](/docs/amp-folder) |
| Fix something that does not work | [Troubleshooting](/docs/troubleshooting) |
| Sign in, or set up Keycloak | [Signing in](/docs/sign-in), [Keycloak setup](/docs/authentication) |

## The app in one minute

1. Run `./gradlew :desktopApp:run`. No account is needed: press **Continue** on the welcome screen and then
   **Continue as guest** (signing in only matters for publishing, see [Signing in](/docs/sign-in)).
2. In the sandbox, type a script and press the green **Run** button. The **dummy** model is there from the start:

```kotlin
val m = initMod("dummy")                              // opens a session, or returns the open one
log(m.inference("turn the lights on").first())        // lights:0.61
```

3. Open **Models** (second sidebar icon), **Sessions** tab: the session is still open, with its log and a
   Terminate button. How sessions behave is in [Running sessions](/docs/sessions). To train your own model:

```kotlin
val train = source("intents.csv")          // columns: text,label
val model = modelBuilder("intents", train)
metricsChart("intents")                    // F1 per label as bars
metricsHistory("intents")                  // accuracy and macro F1 over every build
```

4. On the **Models** page every model is a component to configure, serve, open and publish, and every open session can be terminated there.
