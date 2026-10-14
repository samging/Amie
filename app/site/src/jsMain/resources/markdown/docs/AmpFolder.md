---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/amp-folder
title: The AMP folder
---

# The AMP folder

Everything the app writes lives in `AMP/` inside its working directory (`amieApplication/desktopApp/AMP/` when
started with `./gradlew :desktopApp:run`). Nothing is kept anywhere else on the machine, so copying this folder
moves your scripts, models, metrics, plugins and sign-in to another checkout.

| Path | What it holds | Tracked in git |
|---|---|---|
| `pluginDir/main.kts` | The sandbox script, saved as you type | yes |
| `pluginDir/*.jar` | Package jars, opened by scripts with `initMod("x.jar")`; jars fetched from the Amie server land here | yes |
| `pluginDir/*.jar.source.json` | Where a fetched jar came from: download URL, website page, version, fetch time | yes |
| `modules/<name>/model.onnx` | An exported model | yes |
| `modules/<name>/module.json` | Its manifest: encoder, dimensions, labels | yes |
| `modules/<name>/metrics.json` | One validation entry per build (tables and charts read this) | yes |
| `modules/<name>/serving.json` | Host, port, path, methods, listening | yes |
| `modules/<name>/incoming.jsonl` | Requests the served model received | yes |
| `env/<name>/env.lock.json` | What a script environment has imported | yes |
| `env/caches/` | Maven downloads | yes |
| `relations.json` | Which test file belongs to which training file (`relate`) | yes |
| `componentSettings.json` | Configured devices | yes |
| `backend.json` | Amie backend, website and S3 service addresses | yes |
| `keycloak.json` | Keycloak server, realm, client id and **secret** | **no** |
| `session.json` | The signed-in session (access, refresh and id tokens) | **no** |
| `logs.txt` | Everything the console showed, every level | yes |

## Configuration files

`keycloak.json` is written with defaults on first start:

```json
{
  "baseUrl": "http://localhost:8080",
  "realm": "master",
  "clientId": "amie",
  "clientSecret": "<the amie client's secret>",
  "redirectPort": 48080
}
```

Environment variables `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` override it.

`backend.json` names the Amie backend, the website and the S3 service that keeps package jars; `AMIE_BACKEND_URL`,
`AMIE_SITE_URL` and `AMIE_S3_URL` override it. With
Keycloak on port 8080, run the backend on another port (`AMIE_BACKEND_PORT=8090`) and set `baseUrl` here.

## Metrics format

`metrics.json` is a list of runs; the last one is the current model:

```json
{
  "runs": [
    {
      "createdAt": "2026-10-14T14:02:11Z",
      "samples": 240, "trainSize": 192, "validationSize": 48,
      "accuracy": 0.917, "macroF1": 0.905,
      "labels": { "lights": { "precision": 0.933, "recall": 0.933, "f1": 0.933, "support": 15 } },
      "confusion": [[14, 1], [1, 14]],
      "warnings": [],
      "validation": "split 20%"
    }
  ]
}
```

`confusion[truth][prediction]` is indexed like the label list in `module.json`.
