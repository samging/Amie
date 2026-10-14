---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/sandbox-scripting
title: Scripting API
---

# Scripting API

The sandbox is a Kotlin script editor (`main.kts`) with a console (how to use the editor, the completion dropdown and
the console filters is in [The sandbox](/docs/sandbox)). The script runs inside the app with the sandbox API bound to
it; everything it logs shows in the console on the right and is appended to `AMP/logs.txt`.

Every function below can be called **directly** (`source("x.csv")`) or through the `amie` object
(`amie.source("x.csv")`). The only difference is `amie.import`: without the prefix write
`implementation("group:module:version")` or `dependency(...)` for a package and `environment(SettingsEnv(...))` for
an environment, since a bare `import(...)` reads as a Kotlin import.

## Running a script

1. Type in the editor. The script is saved to `AMP/pluginDir/main.kts` as you type.
2. Press the green **Run** button at the bottom of the left sidebar.
3. While the script runs the button becomes a spinning ring (it fills with the model build's progress when
   `modelBuilder` is working), the status line under the console header reads `Running main.kts · 12s`,
   and pressing Run again only logs "Script is already running".
4. When it ends the status line shows `Finished in 41s` or `Failed after 3s`, and the console gets the same line.

The console chips (Internal, Error, Data, Info) filter what is shown; **Clear** empties the view, the log file keeps it all.

### Run phases

- **Configuration.** `implementation("group:module:version")` / `amie.import("...")` and
  `environment(SettingsEnv("env", "profile"))` / `amie.import(SettingsEnv(...))` calls are read from the script text *before* it compiles, the packages are resolved from Maven Central (cached under
  `AMP/env/caches`) and put on the script's classpath. Each env (`AMP/env/<name>/env.lock.json`) keeps what was ever
  imported into it, like a virtualenv.
- **Execution.** The script's own `import` lines are moved to the top, then `val amie = bindings["logger"] as ScriptLogger`
  and a prelude of top-level functions delegating to it (`fun source(path: String) = amie.source(path)`, …) are
  inserted before the rest of the code, and the whole is evaluated with the Kotlin JSR-223 engine on a background
  thread. The prelude is generated from one list (`SandboxApi.kt`), which also feeds the editor's completion dropdown.

## The API

Defined in `ScriptLogger.kt`. The most used calls (prefix `amie.` optional):

| Call | What it does |
|---|---|
| `log("...")`, `info`, `error`, `data` | Console lines at the given level |
| `source("train.csv")` | Reads a labeled CSV (text and label columns are inferred from headers like `text`, `label`, `intent`). Looked up as given, then in `AMP/`, then `AMP/pluginDir/` |
| `relate(train, "test.csv")` | Attaches a test file to a training source; the model validates on it instead of a split. Remembered in `AMP/relations.json` |
| `sourceFile("file.csv", column = "")` | Raw rows keyed by index, optionally one column |
| `modelBuilder("name", data)` | Trains and exports an intent classifier (below) |
| `modelBuilder("name") { ... }` | Same, the lambda returns the data |
| `initMod("name")` | Opens an exported model as a session (listed on the Models page, terminable there); `.inference("text")` returns `label:probability` sorted best first; `.close()`. `initMod("dummy")` works on a fresh checkout |
| `initMod()` | The bundled BERT NER model |
| `initMod("name.jar")` | A new instance of a package jar behind the same interface: `.inference("text")` calls its `run(text)`, `.close()` its `close()`. Looked up in the env, `AMP/pluginDir`, as a path, then fetched from the Amie server (below) |
| `validateMetrics("name", labels = listOf(...))` | Prints the stored validation table of a model |
| `metricsChart("name", labels = listOf(...))` | ASCII bars: accuracy, macro F1 and the F1 of each label with precision, recall and support |
| `metricsHistory("name")` | ASCII line chart of accuracy and macro F1 across every build |
| `confusionMatrix("name")` | The last validation's confusion matrix as a shaded ASCII grid |
| `forwardContext("text") { ids -> }` | Runs the encoder and returns the hidden state `[batch][token][hidden]` |
| `importModelOnnx("path.onnx")` | Any ONNX model behind the `inference / getStatus / close` interface |
| `sessions.print()`, `sessions.cancel("name")`, `sessions.cancelAll()` | The open ORT sessions |
| `packages.available()`, `packages.fetch("x.jar")`, `packages.print()`, `packages.closeAll()` | The package jars the server offers, a download into `AMP/pluginDir`, the open package sessions |
| `jars.run("plugin.jar")`, `jars.quit("plugin.jar")` | Runs a static `run()` or `main` of a plugin jar once; not a package session |
| `implementation("group:module:version")`, `amie.import("...")` | Add a package to the script's environment |
| `implementation("my.jar")`, `amie.import("my.jar")` | Add a jar file to the environment |
| `environment(SettingsEnv("name", "profile"))`, `amie.import(SettingsEnv(...))` | Select the script environment (must be written literally) |
| `internalEnv()` | Print what the environment contains |

## Building a model

```kotlin
val train = source("intents.csv")          // columns: text,label
relate(train, "intents-test.csv")          // optional test file
val model = modelBuilder("intents", train)
model.modelStatus()                        // the stored validation table
model.modelChart()                         // bars, build history and confusion matrix

val m = initMod("intents")
log(m.inference("turn the lights on").first())
m.close()
```

The charts are described with examples in [Models and metrics](models.md#reading-the-metrics).

## Packages

A package is a jar with one public class that has `run(text: String): List<String>` and `close()`, the contract the
website checks on upload (implementing the backend's `AmiePackage` interface is the easiest way to get there).
Packages are bottles: every `initMod("name.jar")` makes a fresh instance in its own class loader, so two sessions
on the same jar never share state, and `close()` releases that instance and its loader. The jar is looked up in the
active env, then `AMP/pluginDir`, then as a path; when it is nowhere local it is fetched from the Amie server (the
website's `/api/jar-download`, else the S3 service's `/api/aws/jars/<name>`; addresses in `AMP/backend.json`) and
stored in `AMP/pluginDir`, so the next open is local. Next to it, `AMP/pluginDir/<name>.source.json` records where
it came from: the exact download URL, the website page that lists and describes it (`<site>/downloads`), version,
size, publish date and fetch time. `getStatus()` of a package session carries the `url` and `page`, and the
Models page shows them behind the **i** toggle of the package.

```kotlin
val echo = initMod("echo-1.0.jar")        // fetched from the server when it is not local
echo.inference("hello there").forEach { log(it) }
log(echo.getStatus().toString())          // jar, class, source, runs
echo.close()

packages.available().forEach { log("${it.name} ${it.size}") }   // what the server lists
packages.print()                                                   // open package sessions
```

The package class is the manifest's `Main-Class` when it satisfies the contract, else a class implementing an
interface named `AmiePackage`, else the first class in the jar that does. `run` executes on the calling thread with
the jar's class loader as context loader, and what it returns is shown one line per element. Open package sessions
also appear under **SESSIONS** on the [Models page](/docs/models-page).

Accepted data shapes: a `DataSource` from `source`, `List<Pair<text, label>>`, `Map<label, List<text>>`, or
rows of `[text, ..., label]`.

### What modelBuilder does

`buildClassifier` in `jetbrainDLBuilder/JetBuilder.kt`:

1. **checking data**: at least 2 labels, 5 samples per label; imbalance, duplicate and conflicting texts become
   warnings stored with the metrics.
2. **embedding**: every text goes through the BERT encoder; the hidden states are mean-pooled into one vector each.
3. **training**: a two-layer network (`dense[16 relu] -> dense[labels] softmax`) trained with Adam for 150 epochs on
   plain Kotlin arrays (no native TensorFlow needed).
4. **validating**: on the related test file, or on a stratified 20% split; precision, recall, F1 per label.
5. **exporting**: the layers are written by hand as ONNX (opset 13) to `AMP/modules/<name>/model.onnx` with
   `module.json` (encoder, dims, labels) and `metrics.json` (one entry per build).

### Build progress

`ModelBuildProgress` (`jetbrainDLBuilder/BuildProgress.kt`) is a `StateFlow` the builder updates at every phase and
step: text *n* of *N* while embedding, epoch *n* of 150 while training, each validation sample, then export. Phases are
weighted (embedding 60%, training 30%, ...) into one fraction that drives:

- the **Run** button ring in the sandbox sidebar,
- the **status line** under the console header (`modelBuilder 'intents' · training 72/150 · epoch 72 loss 0.3102 accuracy 0.912` with a bar),
- the **BUILDING** card on the Models page.

Every 25 epochs the loss and accuracy are also logged to the console. Finished builds (with their duration and
outcome) stay in `ModelBuildProgress.history`; the status line shows the last one while idle.

## Serving a model

Open the Models page, expand a model, set host, port, path and methods, save, and switch **Listen for incoming data**
on. Then:

```bash
curl -X POST http://127.0.0.1:8765/models/intents/predict -d '{"text":"turn the lights on"}'
curl 'http://127.0.0.1:8765/models/intents/predict?text=turn+the+lights+on'
```

Every request is appended to `AMP/modules/<name>/incoming.jsonl` and counted on the Models page. Models that were
listening when the app closed start again on the next launch.

Accepted data shapes: a `DataSource` from `source`, `List<Pair<text, label>>`, `Map<label, List<text>>`, or
rows of `[text, ..., label]`.

### What modelBuilder does

`buildClassifier` in `jetbrainDLBuilder/JetBuilder.kt`:

1. **checking data**: at least 2 labels, 5 samples per label; imbalance, duplicate and conflicting texts become
   warnings stored with the metrics.
2. **embedding**: every text goes through the BERT encoder; the hidden states are mean-pooled into one vector each.
3. **training**: a two-layer network (`dense[16 relu] -> dense[labels] softmax`) trained with Adam for 150 epochs on
   plain Kotlin arrays (no native TensorFlow needed).
4. **validating**: on the related test file, or on a stratified 20% split; precision, recall, F1 per label.
5. **exporting**: the layers are written by hand as ONNX (opset 13) to `AMP/modules/<name>/model.onnx` with
   `module.json` (encoder, dims, labels) and `metrics.json` (one entry per build).

### Build progress

`ModelBuildProgress` (`jetbrainDLBuilder/BuildProgress.kt`) is a `StateFlow` the builder updates at every phase and
step: text *n* of *N* while embedding, epoch *n* of 150 while training, each validation sample, then export. Phases are
weighted (embedding 60%, training 30%, ...) into one fraction that drives:

- the **Run** button ring in the sandbox sidebar,
- the **status line** under the console header (`modelBuilder 'intents' · training 72/150 · epoch 72 loss 0.3102 accuracy 0.912` with a bar),
- the **BUILDING** card on the Models page.

Every 25 epochs the loss and accuracy are also logged to the console. Finished builds (with their duration and
outcome) stay in `ModelBuildProgress.history`; the status line shows the last one while idle.

## Serving a model

Open the Models page, expand a model, set host, port, path and methods, save, and switch **Listen for incoming data**
on. Then:

```bash
curl -X POST http://127.0.0.1:8765/models/intents/predict -d '{"text":"turn the lights on"}'
curl 'http://127.0.0.1:8765/models/intents/predict?text=turn+the+lights+on'
```

Every request is appended to `AMP/modules/<name>/incoming.jsonl` and counted on the Models page. Models that were
listening when the app closed start again on the next launch.
