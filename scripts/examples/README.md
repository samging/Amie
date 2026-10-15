# Example scripts

Small, copy-able clients of the Amie backend that show the calls a package author or an app makes, and how to
handle every answer. They need the stack from `./run.sh` (the backend on `AMIE_BACKEND_URL`, default
`http://localhost:8090`, and the S3 service on `:8088`). The full list of errors they handle is in
[`../../ERRORS.md`](../../ERRORS.md).

| Script | What it shows |
|---|---|
| `make-package-jar.sh [out.jar] [ClassName]` | Compiles the smallest valid Amie package (`run(String): List` + `close()`) into a jar; `--broken` makes one without `close()` so you can watch the store refuse it with a 400 and the reason |
| `publish-package.sh <jar>` | Bash + curl: sign in (`/login`, registering first when the account is new), `POST /upload`, check `/user-packages` and `/package`; one `explain` function turns each status (400, 401, 403, 413, 502, connection refused) into a sentence and an exit code |
| `amie_client.py <command>` | A Python client and CLI: backend or Keycloak sign-in, packages, upload, delete, saved states, the error log. `AmieError` subclasses (`InvalidPackage`, `NotSignedIn`, `Forbidden`, `NotFound`, `TooLarge`, `StoreUnavailable`, `BackendError`, `Unreachable`) are raised from one place, with the backend's `code` and `detail`; 502/503 and connection errors are retried |
| `provoke-errors.sh` | Calls every route the wrong way on purpose and prints status, `code` and `detail` for each: a live version of ERRORS.md. With a token (or `AMIE_USER`/`AMIE_PASSWORD`) it also shows the refusals only a signed-in user gets, and cleans up after itself |

A typical session:

```bash
scripts/examples/make-package-jar.sh echo-package.jar                      # a jar the store accepts
AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/publish-package.sh echo-package.jar
python3 scripts/examples/amie_client.py login sam secret                     # keeps the token in ~/.amie-token.json
python3 scripts/examples/amie_client.py state put devices '{"uno":{"name":"Uno","port":"/dev/tty.usb1"}}'
python3 scripts/examples/amie_client.py errors report SerialPortException "port busy"   # what the desktop app does
python3 scripts/examples/amie_client.py errors list
python3 scripts/examples/amie_client.py delete jar/echo-package.jar
python3 scripts/examples/amie_client.py nick "Sam the Maker"                 # a display name; "" clears it (409 when taken)
python3 scripts/examples/amie_client.py avatar set me.png                    # PNG/JPEG/GIF/WebP ≤ 512 KB; the bytes decide the type
python3 scripts/examples/amie_client.py notes add uploads/sam/jar/echo-package.jar "Works on my Uno"
python3 scripts/examples/amie_client.py notes list uploads/sam/jar/echo-package.jar
python3 scripts/examples/amie_client.py keycloak admin secret                # a Keycloak account with the admin role …
python3 scripts/examples/amie_client.py admin users                         # … can moderate people and notes
AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/provoke-errors.sh       # every refusal, one per line
```

Exit codes are the same everywhere: `0` done, `1` the backend refused (a 4xx with the reason printed), `2` wrong
usage, `3` the backend or the package store is down (connection refused, 5xx).

Using the Python client from your own code:

```python
import sys; sys.path.insert(0, "scripts/examples")
from amie_client import AmieClient, InvalidPackage, NotSignedIn, StoreUnavailable

client = AmieClient()
client.login("sam", "secret")
try:
    client.upload("echo-package.jar", open("echo-package.jar", "rb").read())
except InvalidPackage as e:
    print("fix the jar:", e.detail)          # e.g. "No class in the jar has both run(String): List and close() methods"
except NotSignedIn:
    client.login("sam", "secret")            # the token expired (an hour for backend tokens)
except StoreUnavailable as e:
    print("try later:", e.code, e.detail)    # store_unavailable: the S3 service is down
```
