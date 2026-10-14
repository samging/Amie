---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/sandbox
title: The sandbox
---

# The sandbox

The sandbox is the first screen after sign-in: a Kotlin script editor on the left, the output console on the right,
and the sidebar with the green **Run** button at the bottom.

## The editor

- The file is `AMP/pluginDir/main.kts`. It is saved as you type (a third of a second after the last keystroke).
- **Highlighting**: Kotlin keywords, strings, numbers and comments have their own colors, and the sandbox API
  functions (`source`, `modelBuilder`, `metricsChart`, …) are drawn in the accent color so you can see at a glance
  what talks to the app.
- **Completion dropdown**: start typing a name and a list of matching API functions opens under the caret with the
  signature and a one-line description. **Up / Down** move, **Enter** or **Tab** insert the call and put the caret
  between the parentheses, **Esc** closes it, **Ctrl+Space** opens it on demand.
- The API is available **without a prefix**. `log("hi")`, `source("x.csv")` and `modelBuilder("name", data)` are the
  same as `amie.log(...)`, `amie.source(...)` and `amie.modelBuilder(...)`; both spellings work in the same script.
  Only `amie.import` has distinct names without the prefix: `implementation("group:module:version")` (or
  `dependency(...)`) for a package and `environment(SettingsEnv("name", "profile"))` for an environment, because
  `import(...)` without `amie.` reads as a Kotlin import statement. **Tab** in the editor inserts four spaces.

## Running

1. Press **Run** (or press it again to see "Script is already running").
2. The button becomes a progress ring and the status line under the console header reads
   `Running main.kts · 12s`. While `modelBuilder` works, the ring fills with the build's progress and the status line
   shows the phase and step (`embedding 40/120`, `training 72/150 · loss 0.31`).
3. The console ends with `Finished in 41s` or `Failed after 3s` and the error.

What happens underneath: the script's dependency calls are read **before** it compiles and the packages are put on
the classpath (configuration phase); then the script runs with the Kotlin JSR-223 engine on a background thread
(execution phase). Details in [Scripting API](/docs/sandbox-scripting).

## The output console

Every line has a time, a **level badge**, the source (`main.kts`, `sandbox`, `internal`) and the message. Rows are
striped, long messages wrap inside their own column, and multi-line output such as metric tables and ASCII charts
keeps its alignment because the console is monospace.

| Level | Color | Used for |
|---|---|---|
| **Internal** | violet | Lines the app itself writes: build phases, metrics tables, charts, session messages |
| **Error** | red | Failures, Keycloak and backend errors, script exceptions |
| **Data** | green | Data your script logs with `data(...)`, file dumps |
| **Info** | blue | `log(...)` and `info(...)` from the script, run start and end |

The chips in the header (`● Internal 12`, `● Error 0`, …) filter the view and show how many lines each level has.
Click a chip to hide or show its level; **Show all** turns them all on again, **Clear** empties the view. Nothing is
lost: everything the console ever showed is appended to `AMP/logs.txt`.

## Keyboard summary

| Keys | Action |
|---|---|
| Typing a name | Opens the completion dropdown |
| Up / Down | Move in the dropdown |
| Enter / Tab | Insert the selected function |
| Esc | Close the dropdown |
| Ctrl+Space | Open the dropdown |
