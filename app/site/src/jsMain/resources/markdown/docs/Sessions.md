---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/sessions
title: Running sessions
---

# Running sessions

A **session** is a model or a package that the app holds open in memory so it can answer questions right away:
the ONNX head of an intent model with its encoder, one instance of a package jar in its own class loader, or a
bare ONNX Runtime session. A session is opened once, asked as often as you like with `inference(text)`, and stays
open until it is terminated: it survives the end of the script that opened it, the next run of the script, and
switching pages. The **Sessions** tab of the Models page lists every open one, with its log and a Terminate button.

This page is the practical guide: how to open a session, talk to it, keep it warm between runs, read what it did,
and close it. The function reference is in [Scripting API](/docs/sandbox-scripting), the page itself in
[Models page](/docs/models-page#sessions).

## The three kinds

| Kind | Opened by | How many | What `inference(text)` returns |
|---|---|---|---|
| **Intent model** | `initMod("name")` in a script, **Open session** on the Models page | **One per model.** A second `initMod("name")` returns the session that is already open | `label:probability` lines, best first (`lights:0.91`, `weather:0.06`, …) |
| **Package** | `initMod("name.jar")` | **One per call.** Every `initMod` makes a fresh instance of the jar's class in its own class loader | What the package's `run(text)` returned, one line per element |
| **ONNX session** | `importModelOnnx("file.onnx")` | One per call | The raw model's output; no log is kept |

The bundled NER model, `initMod()` with no name, is not a listed session: it is an ordinary object your script
owns, so close it yourself when you are done.

## Open one and ask it something

Every fresh `AMP/` folder has the **dummy** model (labels `greeting`, `lights`, `weather`), so this script works
before anything was trained:

```kotlin
val m = initMod("dummy")                              // opens the session, or returns the open one
log(m.inference("turn the lights on").first())        // lights:0.61
m.inference("hello there").forEach { log(it) }        // every label with its probability
log(m.getStatus().toString())                         // the module manifest: encoder, dims, labels
```

Press **Run**. The console shows `initMod: 'dummy' session ready (…)`, then your lines, then `Finished in 2s`.
Open **Models** (second sidebar icon) and the **Sessions** tab: `dummy  #1`, *intent model*, *idle*, `2 runs`,
open for a few seconds. The session is still there: the script ended, the session did not.

Run the script again. This time the console says `initMod: 'dummy' is already open as session #1, that one is
used`, the run count goes up and no new session appears. A model has one session at most, on purpose: a script
run ten times does not pile up ten copies of the model in memory.

A package behaves differently, because packages are bottles with their own state:

```kotlin
val echo = initMod("amie-demo-1.0.0.jar")             // the demo package: a new instance every call
echo.inference("hello").forEach { log(it) }
log(echo.getStatus().toString())                      // id, jar, class, source, url, page, runs
echo.close()                                          // releases the instance and its class loader
```

Without the `close()`, every run of the script leaves one more `amie-demo-1.0.0.jar #n` open under Sessions.
That is fine while you experiment (terminate them on the page, or `packages.closeAll()` at the top of the
script), but a script you run often should close the packages it opens.

Where the jar comes from: the active environment, then `AMP/pluginDir`, then a path, then the Amie server, which
stores it in `AMP/pluginDir` so the next open is local. `packages.available()` lists what the server offers.

## Keep a session warm between runs

Loading a model takes a moment (the encoder is a BERT model); asking it takes milliseconds. Because an intent
model's session outlives the script, the pattern for iterating on a script is simply to open the model at the top
and **not** close it:

```kotlin
val m = initMod("intents")                            // first run: loads; every later run: instant
listOf("turn the lights on", "what's the weather", "hi").forEach { text ->
    log("$text -> ${m.inference(text).first()}")
}
```

Edit the texts, press Run, read the console, repeat. The session keeps counting runs across your edits. Close it
when you are done (`m.close()`, or Terminate on the page) and the next `initMod("intents")` loads it again.

For a package, keep the session across runs by not closing it and reusing the **same** session on the next run:

```kotlin
val echo = packages.open.firstOrNull { it.jarName == "amie-demo-1.0.0.jar" && !it.closed }
    ?: initMod("amie-demo-1.0.0.jar")
```

(`packages.open` is the list of open package sessions, oldest first.)

## Read what a session did

Every intent-model and package session keeps a log of what it was asked and answered:

| Line | Written when |
|---|---|
| `open` | the session was opened (or reused: *a model has one session*) |
| `run` | each `inference` call: the text, the first three answers, how long it took |
| `error` | an `inference` call threw: the text and the exception |
| `close` | the session was closed, with the number of runs |

Read it on the Models page, **Log** on the session's row, or in the file next to the owner:
`AMP/modules/<name>/sessions.log` for a model, `AMP/pluginDir/<jar>.sessions.log` for a package. The file keeps
the history of terminated sessions too; the page keeps the last 300 lines of an open one.

```
2026-10-14T17:40:02.118Z dummy #3 open  opened as session #3
2026-10-14T17:40:05.402Z dummy #3 run   "turn the lights on" -> lights:0.61, weather:0.22, greeting:0.17 (14 ms)
2026-10-14T17:41:10.009Z dummy #3 close closed after 1 runs
```

From a script, `packages.print()` logs one line per open package session (`#1 amie-demo-1.0.0.jar
(org.example.Echo) · pluginDir · runs 4`), and `sessions.print()` lists every ONNX Runtime session with its
inputs, outputs, runs and whether it is busy; an intent model's head shows there as `module:<name>`. The Sessions
tab shows the same at a glance: owner and `#id`, kind, *idle* or *inferring*, runs, open for how long.

## What runs where

- `inference` **blocks** the script until the answer is back. The model work itself runs on the app's ONNX
  threads, never on the UI thread, so the window stays responsive while a script waits.
- One text at a time goes through a model's encoder. Two scripts asking the same model take turns; the Sessions
  tab shows *inferring* while a call is in flight.
- Only one script runs at a time. Pressing **Run** during a run logs `Script is already running`; the sessions a
  running script opened stay open after it ends, whether it finished or failed.
- A script cannot be stopped from the window. Terminating the session it is using makes its next `inference`
  throw `model <name> (#id) is closed`, which ends the script with `Failed after …`.
- **Serving** (Listen on the Models page) and **Validate** do not use your session: Listen loads its own instance
  of the model and closes it on Stop; a validation opens a session for its run, logs every row to it, and closes
  it at the end. Terminating a session never stops a listener.

## Close sessions

| Call or button | What it ends |
|---|---|
| `m.close()` | this session; a second call does nothing |
| `packages.closeAll()` | every open package session (each package's `close()` is called, its class loader dropped) |
| `sessions.cancel("module:intents")`, `sessions.cancel("3")` | ONNX Runtime sessions by name or id; in-flight runs are terminated first |
| `sessions.cancelAll()` | every ONNX Runtime session, including the heads of open models |
| **Terminate** on the Sessions tab (also on a model's or package's own panel) | that session |
| **Terminate all** | everything open |

After a close, the session's row disappears, its log file keeps the history, and any further `inference` on the
closed object throws. Sessions do not survive a restart of the app: it starts with none open.

## When something is off

| Console says | What it means |
|---|---|
| `initMod: 'x' is already open as session #3, that one is used` | Normal: a model has one session; you got the open one |
| `Error: model x (#3) is closed` | The session was terminated (page, `close()`, `cancelAll()`); call `initMod("x")` again |
| `Module 'x' not found in …/AMP/modules` | No model of that name was built; see the Models tab or `AMP/modules/` |
| `package x.jar not found: not in the env, AMP/pluginDir, as a path, or on the server (…)` | The jar is nowhere local and the Amie server does not list it; check `packages.available()` and `AMP/backend.json` |
| `x.jar has no package class: a public class with run(String): List and close(), and a no-arg constructor` | The jar does not follow the package contract, see [Packages](/docs/sandbox-scripting#packages) |
| `Script is already running` | Wait for `Finished` or `Failed`; sessions are unaffected |
| Many `name.jar #n` rows under Sessions | Package sessions are one per `initMod` call; close them in the script or press Terminate all |
