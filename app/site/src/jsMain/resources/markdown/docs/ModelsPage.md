---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/models-page
title: Models page
---

# Models page

Route `models`, opened from the second icon in the sandbox sidebar. It is a full page (not a side panel) in
`components/terminal/window/controller/ModelsPage.kt`, and it re-reads its state while open. What it showed last
is kept for the life of the app (`ModelsCache`), so coming back to the page shows the models at once; the first
read after start-up is local only (files, sessions, listeners) and the one slow read, the server's jar listing,
is fetched right after and cached for a minute by `JarPackages`. Nothing on the page waits for the network.

The page keeps to one quiet scheme, the same grays as the sandbox. One dark blue is reserved for the actions that
create, build, validate, open or add (**+ New model**, **Build**, **Sample data**, **Validate…**, **Open
session**, **Open in viewer**, **+ New collection**, **+ Add**, **+ Add jar**, **Fetch**) and for the search
boxes, so they are found at a glance; the charts use a lighter step of the same blue. A running model or an open
package is underlined in green in the list and under its panel header; a stopped one is not marked. Lists are
flat rows; only the selected item has a panel.

## Layout

Under the window header sits a tab strip like the sandbox's panel tabs, with three tabs and their counts:
**Models**, **Packages** and **Sessions**, and **Open website** (the site's dashboard) at its right end when
signed in. Signing in happens in the window header (**Log in**) and is only needed for **Publish**; everything
else works without an account. Each tab is a list on the left and one detail panel on the right for the selected
item, both with a filter box (the sessions list once it is long).

At the bottom of the page sits **OUTPUT**: the lines the models, packages, sessions, builds and validations log
(the same lines the sandbox console shows, filtered to those), newest last, with the build or validation in
progress named on its header with its step. Nothing about progress sits at the top of the page.

## Models

The list has one line per module in `AMP/modules`: the name, its last accuracy (or `not validated`), the port it
listens on and its open sessions, and at the right `running`, `inferring` or `stopped`; a running one is
underlined in green. Models are grouped by the data they were trained on (the csv name the builder records in
`module.json` since this version; the dummy sits under *written, not trained*, older models under *unknown
data*), so the same data trained twice sits together. Groups with a running model come first; inside a group the
running models come first, then the most accurate. The filter box above matches the name, a label, the status,
the data and the file a model was validated on. Clicking a line selects it; the panel on the right shows that
model only, with the status line (listening address, the data it was trained on) under its name and the actions
at the right of the header:

| Action | What it does |
|---|---|
| **Configure** | Shows the serving settings: **Host / Port / Path / Methods** and **Save** write `AMP/modules/<name>/serving.json`; the POST and GET URLs are listed under them |
| **Listen / Stop** | Starts or stops the HTTP listener (`modules/ModelServer.kt`) with the settings as saved |
| **Open session** | Loads the model (encoder and head) as a session, the same as `initMod("name")` in the sandbox |
| **Publish** | Uploads `model.onnx` and `module.json` to the Amie backend (`POST /upload`, `progLanguage=onnx`) with the Keycloak token; a guest is sent to the sign-in page |

Under the header: the labels, the encoder, when it was built and, while listening, the serving line with its
request count, errors, average latency and uptime. Then **METRICS** from `AMP/modules/<name>/metrics.json`: the run
number and what it was validated on, three tiles (accuracy, macro F1, rows validated), and the plots. The plots
are images: `modules/MetricsImages.kt` draws them as PNG files into `AMP/modules/<name>/plots/` whenever
`metrics.json` is newer than they are, and the page shows the files, so they open at once and can be used
elsewhere as they are. `labels.png` is the **F1 of every label** as bars with precision, recall and row count
beside each; `confusion.png` the **confusion matrix** as a one-hue heatmap with truth in rows, prediction in
columns and the count in every cell; `history.png`, once there is more than one run, **accuracy and macro F1
over the runs** as two lines with markers and the last values labelled, over a folded **RUNS** list. Then
warnings. A model with no run yet
(the dummy on a fresh checkout) says `no validation yet`. The two buttons beside the title make a run without
training: **Validate on sample data** runs the model on `AMP/sample-intents.csv`, **Validate on file** on a csv
of your choice (`modules/ModelValidation.kt`). Every row goes through a session of the model (it appears under
SESSIONS and logs each row while it runs; the tab strip counts the rows), the top label is compared with the
row's, and the result is appended to `metrics.json` in the same shape the builder writes, with the file name as
the validation set. Rows with labels the model doesn't know are skipped and reported as a warning. **Validate
all** in the section header does the same for every model with one file. Then **SESSIONS**: every session this
model owns as a row (`#id`, idle or inferring, runs, open for how long) with **Log** and **Terminate**; the
console still has the ASCII charts (`metricsChart`, `metricsHistory`, `confusionMatrix`, see
[Models and metrics](/docs/models)).

### New model

**+ New model** in the section header opens a form (it is open on its own when there are no models): **Name**,
**Data** (Choose a csv with a text column and a label column, or **Sample data** for `AMP/sample-intents.csv`,
45 rows over `greeting`, `lights` and `weather`; the name is filled from the file name) and an optional **Test**
file to validate on instead of a 20% split. As soon as the data file is chosen the form reads
it and shows how many samples and which labels it holds; **Build** then runs the same `buildClassifier` the
sandbox's `modelBuilder` runs, on a background thread. While it runs a card at the top of the tab shows the phase,
step and progress bar (and the tab strip names the model), from the same `ModelBuildProgress` state the sandbox
uses and the output strip at the bottom prints its phases; when it ends the new model is selected. Only one
build runs at a time.

A fresh checkout has one component already: the **dummy** model (`modules/AmpModules.kt`, `ensureDummyModule`),
a fixed one-layer head over the encoder with the labels `greeting`, `lights`, `weather`. It is written when
`AMP/modules` is empty, so the page always has something to configure and `initMod("dummy")` works before any
training. The sample csv (`ensureSampleData`) has the same labels, so the first thing to try is **Validate on
sample data** on the dummy (its weights are fixed, so expect poor numbers) and then **+ New model** with
**Sample data** to see what a trained model's metrics look like.

## Packages

The list has one line per package jar a script can open, first under the **collections** and then under three
folding groups by place: **In AMP/pluginDir**, **In the env** and **On the server** (the website's `/api/jars`,
else the S3 service's `/api/aws/jars`; folded by default). Each line shows the Java the jar needs and its version,
and at the right `idle`, `2 open`, `inferring` or `on server`; an open one is underlined in green. The filter
box above the groups matches the jar name, version, Java, a collection name, the class a session opens on, and
any class or resource name inside the jar, so a class can be found without knowing which jar holds it.
**+ Add jar** copies a file into `AMP/pluginDir` and imports it into the env.

### Collections

A collection is a folder of jars that belong together, kept in `AMP/pluginDir/collections.json`
(`modules/PackageCollections.kt`), typically the jars that run on one Java: "jvm21" with every plugin that fits.
**+ New collection** opens a form: a name, the jars to put in (toggles), and a **By Java** shortcut per Java
version found among the jars (`jvm17`, `jvm21`, …) that names the collection and picks every jar that runs on
that Java or older. Each collection is a fold at the top of the list with its jars as rows; moving the pointer
over a row shows **Detach**, **+ Add** lists the jars that are not in it yet, and **Delete folder** removes the
collection but keeps the jars. A jar can be in several collections, and its panel header says which.

The detail panel describes the selected jar without running anything (`pluginEngine/JarInspector.kt`, read once
per file version). Its header has the status line (status, `java 17+`, `kotlin` or `java`, version) and the
buttons **Open page** (the website page that lists and describes the jar), **Fetch** for a jar still on the
server and **Reveal file** for a local one. Under it the `initMod("name.jar")` call, then folding sections,
closed until opened:

| Section | What it shows |
|---|---|
| **SOURCE** | where it is, the website page, the exact download URL, when it was fetched and published (from `<jar>.source.json`), the file |
| **BUILD** | what built it (the manifest's `Created-By`, `Build-Jdk-Spec`), the class file version and the Java it needs, whether this app's Java can load it, language, size |
| **CONTENTS** | the class a session opens on (the one with `run(String)` and `close()`) and the `Main-Class`; a filter box; **CLASSES** grouped by package and **RESOURCES** grouped by folder, each group folding; the entry class and main class are marked; clicking a line copies its full name |

Then **SESSIONS**: the sessions open on it, each with **Log** and **Terminate**. There are no Run or Quit
buttons: a package is a bottle, not something the page controls. A script opens its own instance with
`initMod("name.jar")`, calls `inference(text)` and `close()`, and a jar that is only on the server is fetched into
`AMP/pluginDir` on first open (see [Packages](/docs/sandbox-scripting#packages)).

## Sessions

(What a session is and how to use one from a script is in [Running sessions](/docs/sessions); this is the tab.)

The tab lists everything that is open as flat rows named after the owner (`dummy  #3`, `echo-1.0.jar  #1`) with
what it is (intent model, package, ONNX session), idle or inferring, how many runs it has done and for how long
it has been open; with more than a few, a filter box matches owner, kind or `#id`. **Log** unfolds what the
session was asked and answered: one line per `inference` call (`"turn the lights on" -> lights:0.97,
weather:0.02 (12 ms)`), plus open, close and error lines, with the time of each. The same lines are appended to
the owner's file, `AMP/modules/<name>/sessions.log` for a model and `AMP/pluginDir/<jar>.sessions.log` for a
package, so the history of a terminated session is not lost (`modules/SessionLog.kt`). **Terminate** ends a
session: intent model sessions (from **Open session** or `initMod("name")`; `modules/ModelSessions.kt`), package
sessions (`pluginEngine/JarPackages.kt`; terminating calls the package's `close()` and drops its class loader)
and ONNX Runtime sessions opened on a bare file with `importModelOnnx` (`modelLoaders/OrtSessions.kt`;
terminating cancels in-flight runs; these keep no log). The ORT session inside a model session is not listed
twice; it goes with the model session. **Terminate all** ends all of them.
