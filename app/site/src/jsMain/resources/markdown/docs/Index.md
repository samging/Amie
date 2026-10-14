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
| Sign in once and stay signed in | [Signing in](/docs/sign-in) |
| Write and run a script, read the console | [The sandbox](/docs/sandbox) |
| Know every function a script can call | [Scripting API](/docs/sandbox-scripting) |
| Train a model and look at its metrics | [Models and metrics](/docs/models) |
| Put a model behind an HTTP endpoint | [Serving a model](/docs/serving) |
| Manage devices on the dashboard | [Devices](/docs/devices) |
| Know what the app writes to disk | [The AMP folder](/docs/amp-folder) |
| Fix something that does not work | [Troubleshooting](/docs/troubleshooting) |

## The app in one minute

1. Start Keycloak (optional, see [Signing in](/docs/sign-in)) and run `./gradlew :desktopApp:run`.
2. On the welcome screen press **Continue**. The first time you are asked to sign in, create an account, or continue
   as a guest. From then on the app remembers you.
3. In the sandbox, type a script and press the green **Run** button:

```kotlin
val train = source("intents.csv")          // columns: text,label
val model = modelBuilder("intents", train)
metricsChart("intents")                    // F1 per label as bars
metricsHistory("intents")                  // accuracy and macro F1 over every build
```

4. Open **Models** (second sidebar icon): every model is a component to configure, serve, open and publish, and every open session can be terminated there.
