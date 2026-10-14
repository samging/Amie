---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/models
title: Models and metrics
---

# Models and metrics

A model is an intent classifier: a small dense network trained on BERT embeddings of your texts. Everything about
a model lives in `AMP/modules/<name>/`, so building, inspecting and serving one needs no account; only publishing
does. A fresh checkout starts with a fixed **dummy** model (labels `greeting`, `lights`, `weather`) that is written
when `AMP/modules` is empty, so `initMod("dummy")` and the Models page work before any training.

## Building one

```kotlin
val train = source("intents.csv")        // columns: text,label (header names like text/label/intent are detected)
relate(train, "intents-test.csv")        // optional: validate on this file instead of a 20% split
val model = modelBuilder("intents", train)
model.modelStatus()                      // the validation table
model.modelChart()                       // bars, history and confusion matrix
```

`modelBuilder` checks the data (at least 2 labels and 5 samples per label; imbalance, duplicates and conflicts
become warnings), embeds every text, trains for 150 epochs, validates, and exports:

| File | Content |
|---|---|
| `model.onnx` | The trained layers, written as ONNX (opset 13) |
| `module.json` | Encoder path, input and output sizes, pooling, the label list |
| `metrics.json` | **One entry per build**: accuracy, macro F1, per-label precision / recall / F1 / support, the confusion matrix, warnings, what it was validated on |
| `serving.json` | Host, port, path, methods and whether it listens |
| `incoming.jsonl` | Every request the served model received |

Accepted data shapes: a `DataSource` from `source(...)`, `List<Pair<text, label>>`, `Map<label, List<text>>`, or
rows of `[text, …, label]`.

## Reading the metrics

Metrics are plain local files, so anyone using the app can read them, from the console or from the Models page.

### In the console, as tables

```kotlin
validateMetrics("intents")                          // all labels
validateMetrics("intents", listOf("lights", "music")) // some labels
```

```
metrics 'intents' @ 2026-10-14T14:02:11Z: 240 samples (192 train / 48 validation on split 20%)
accuracy 0.917  macro F1 0.905
label   precision  recall  f1     support
lights  0.933      0.933   0.933  15
music   0.875      0.933   0.903  15
```

### In the console, as ASCII charts

```kotlin
metricsChart("intents")             // accuracy, macro F1 and the F1 of every label as bars
metricsChart("intents", listOf("lights"))
metricsHistory("intents")           // accuracy and macro F1 across every build, as a line chart
confusionMatrix("intents")          // the last validation's confusion matrix as a shaded grid
```

`metricsChart` draws one bar per row with precision, recall and support beside it:

```
metrics 'intents' · build 3 of 3 · 48 validation samples on split 20%
F1 per label (bar), precision p, recall r, support n
accuracy ███████████████████████████░░░ 0.917
macro F1 ███████████████████████████░░░ 0.905
lights   ████████████████████████████░░ 0.933  p 0.933  r 0.933  n 15
music    ███████████████████████████░░░ 0.903  p 0.875  r 0.933  n 15
weather  ██████████████████████████░░░░ 0.880  p 0.917  r 0.846  n 18
```

`metricsHistory` plots every build on one canvas (● accuracy, ○ macro F1, ◆ where they meet) with the build
numbers under the columns and a dated line per build, so you can see whether a new data file helped.

```
history 'intents' · 3 builds
1.00 ┤            ◆
     ┤      ●
     ┤      ○
0.50 ┤●
     ┤○
0.00 ┤
     └─────────────
      1     2     3
      ● accuracy   ○ macro F1   ◆ both
```

`confusionMatrix` prints truth in rows and prediction in columns; the shade of each cell is its share of the row
and ✓ marks the diagonal.

Each of these returns the report, so they chain: `metricsChart("intents").history().confusion()`. A model that
was never built logs an error telling you to run `modelBuilder` first.

### On the Models page

The **Models** page (second sidebar icon) does not show figures; use the console calls above. Expand a card for labels,
encoder and serving settings. The page has no metrics: every model is a component with Configure, Listen / Stop,
Open session and Publish (Keycloak sign-in) actions, and every open session can be terminated there. Details in
[Models page](/docs/models-page).

## Using a model from a script

```kotlin
val m = initMod("intents")
log(m.inference("turn the lights on").first())   // "lights:0.97"
m.close()
```

`initMod()` with no name opens the bundled NER model; `importModelOnnx("path.onnx")` opens any ONNX model behind
the same `inference / getStatus / close` interface. Every `initMod("name")` is a session the Models page lists
and can terminate; ORT sessions are also printed with `sessions.print()`.

## Publishing

Signed in with Keycloak, expand a model on the Models page and press **Publish**: `model.onnx` and `module.json`
are uploaded through the Amie backend (`POST /upload`, `progLanguage=onnx`) into the S3 bucket at
`uploads/<user>/onnx/<file>`, and the website's dashboard lists them. A guest pressing it is sent to the sign-in
page. Requires the backend and the awsS3 service from [Amie backend](/docs/backend-integration). Package jars go the other way: a
script opens one the server lists with `initMod("name.jar")` (see [Packages](/docs/sandbox-scripting#packages)).
