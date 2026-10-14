---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/models-page
title: Models page
---

# Models page

Route `models`, opened from the second icon in the sandbox sidebar. It is a full page (not a side panel) in
`components/terminal/window/controller/ModelsPage.kt`, and it re-reads its state while open.

The page shows components and sessions, not metrics. Metrics stay in the console (`validateMetrics`,
`metricsChart`, `metricsHistory`, `confusionMatrix`, see [Models and metrics](/docs/models)) and in
`AMP/modules/<name>/metrics.json`. The page keeps to one quiet scheme: white marks what is active (a listening
model, a running session), gray marks everything else.

## Header

**Sign in** for a guest, or the Keycloak username and **Open website** (the site's dashboard) when signed in.
Signing in is only needed for **Publish**; everything else works without an account.

## Intent models

The section is a list and a detail panel, so it stays readable with many models. The list on the left has one
line per module in `AMP/modules`: a status dot, the name, what it is doing ("listening host:port", "2 sessions")
and a status badge: **RUNNING** when it serves or has a session open, **RUNNING · INFERRING** while one of its
sessions is answering, **STOPPED** otherwise. Clicking a line selects it; the panel on the right shows that model
only, in the style of the device cards (name, status, serving address, label count and format as badges) with an
action matrix:

| Action | What it does |
|---|---|
| **Configure** | Shows the serving settings: **Host / Port / Path / Methods** and **Save** write `AMP/modules/<name>/serving.json`; the POST and GET URLs are shown and selectable |
| **Listen / Stop** | Starts or stops the HTTP listener (`modules/ModelServer.kt`) with the settings as saved |
| **Open session** | Loads the model (encoder and head) as a session, the same as `initMod("name")` in the sandbox; it appears under SESSIONS |
| **Publish** | Uploads `model.onnx` and `module.json` to the Amie backend (`POST /upload`, `progLanguage=onnx`) with the Keycloak token; a guest is sent to the sign-in page |

Under the matrix the panel lists the labels, the encoder and, while listening, the serving URL, then
**SESSIONS OF \<name\>**: every session this model owns (`<name> · session #id`, idle or inferring, runs, open for
how long) with **Terminate** on each and **Terminate all** for the model.

A fresh checkout has one component already: the **dummy** model (`modules/AmpModules.kt`, `ensureDummyModule`),
a fixed one-layer head over the encoder with the labels `greeting`, `lights`, `weather`. It is written when
`AMP/modules` is empty, so the page always has something to configure and `initMod("dummy")` works before any
training.

## Building

While `modelBuilder` runs in the sandbox, a **BUILDING** card shows the model's phase, step and progress bar,
from the same `ModelBuildProgress` state the sandbox uses (see [sandbox-scripting.md](/docs/sandbox-scripting)).

## Packages

**PACKAGES** lists the package jars a script can open: those in `AMP/pluginDir` and the active env, and those the
Amie server offers (the website's `/api/jars`, else the S3 service's `/api/aws/jars`) that are not local yet. Each
row shows where the jar is (in the env, in `AMP/pluginDir` added locally or from the server, or still on the
server), its version and how many sessions are open on it, next to the call that opens one. The **i** toggle on a
row shows where the jar was downloaded from (the exact URL), the website page that lists and describes it, when it
was fetched and published, and the file names; **Open page** opens that website page. There are no
Run or Quit buttons: a package is a bottle, not something the page controls. A script opens its own instance with
`initMod("name.jar")`, calls `inference(text)` and `close()`, and a jar that is only on the server is fetched into
`AMP/pluginDir` on first open (see [Packages](/docs/sandbox-scripting#packages)). **+ Add jar** copies a file into
`AMP/pluginDir` and imports it into the env.

## Sessions

**SESSIONS** lists everything that is open, each named after its owner (`dummy · session #3`,
`echo-1.0.jar · session #1`) with a **Terminate** button: intent model sessions (from **Open session** or
`initMod("name")`; `modules/ModelSessions.kt`), package sessions (`pluginEngine/JarPackages.kt`; terminating calls
the package's `close()` and drops its class loader) and ONNX Runtime sessions opened on a bare file with
`importModelOnnx` (`modelLoaders/OrtSessions.kt`; terminating cancels in-flight runs). The ORT session inside a
model session is not listed twice; it goes with the model session. **Terminate all** ends all of them.
