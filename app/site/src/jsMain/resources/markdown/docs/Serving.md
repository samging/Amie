---
root: .components.layouts.MarkdownLayout
routeOverride: /docs/serving
title: Serving a model
---

# Serving a model

Every exported model can answer HTTP requests from inside the app.

1. Open **Models**, expand the model.
2. Set **Host** (default `127.0.0.1`), **Port**, **Path** (default `/models/<name>`) and the **Methods** (POST, GET),
   then **Save**. This writes `AMP/modules/<name>/serving.json`.
3. Switch **Listen for incoming data** on. The card turns green and shows `listening · up 3m`.

```bash
curl -X POST http://127.0.0.1:8765/models/intents -d '{"text":"turn the lights on"}'
curl 'http://127.0.0.1:8765/models/intents?text=turn+the+lights+on'
```

The POST and GET URLs are shown on the card and can be selected and copied.

- Every request is appended to `AMP/modules/<name>/incoming.jsonl` and counted as **incoming rows**.
- **Requests**, **errors**, **avg latency**, **max latency** and **last request** on the card are kept per
  listener from the moment it starts.
- Changes to host, port or path apply after switching the listener off and on.
- Models that were listening when the app closed start listening again on the next launch.
- **Cancel all** under ORT SESSIONS terminates in-flight inference runs and closes the sessions.
