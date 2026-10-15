#!/usr/bin/env python3
"""A small Python client for the Amie backend, with the errors it can raise, usable as a library or a CLI.

    python3 scripts/examples/amie_client.py login sam secret            # a backend account (registers it when new)
    python3 scripts/examples/amie_client.py keycloak sam secret         # the desktop app's way: Keycloak password grant
    python3 scripts/examples/amie_client.py me
    python3 scripts/examples/amie_client.py packages [user]            # the store, or one author's files
    python3 scripts/examples/amie_client.py upload echo-package.jar    # publish (jar is inspected by the backend)
    python3 scripts/examples/amie_client.py delete jar/echo-package.jar
    python3 scripts/examples/amie_client.py state put devices '{"uno":{"name":"Uno","port":"/dev/tty1"}}'
    python3 scripts/examples/amie_client.py state get devices | state list | state delete devices
    python3 scripts/examples/amie_client.py errors list | errors report "SerialPortException" "port busy" | errors clear
    python3 scripts/examples/amie_client.py notes list uploads/sam/jar/echo.jar     # anyone
    python3 scripts/examples/amie_client.py notes add uploads/sam/jar/echo.jar "Works on my Uno"
    python3 scripts/examples/amie_client.py notes delete 7                       # the author or the package owner
    python3 scripts/examples/amie_client.py nick "Sam the Maker"                 # a custom display name ("" clears it)
    python3 scripts/examples/amie_client.py avatar set me.png | avatar get out.png | avatar clear
    python3 scripts/examples/amie_client.py admin users | admin notes | admin note-delete 7 | admin user-delete eve   # Keycloak admin role
    python3 scripts/examples/amie_client.py admin avatars | admin approve eve | admin badge eve verified | admin unbadge eve verified
    python3 scripts/examples/amie_client.py pubs list [user] | pubs add "Title" "Venue" 2026 https://… 10.1234/x | pubs delete 3
    python3 scripts/examples/amie_client.py link octocat hf-handle                # GitHub and Hugging Face usernames ("" unlinks)
    python3 scripts/examples/amie_client.py background set banner.png | background clear   # waits for an admin like the picture
    python3 scripts/examples/amie_client.py like uploads/sam/jar/echo.jar | likes | stats sam

The token from `login`/`keycloak` is kept in ~/.amie-token.json and used by every other command, or pass
AMIE_TOKEN. Addresses: AMIE_BACKEND_URL (default http://localhost:8090), KEYCLOAK_URL (http://localhost:8080),
KEYCLOAK_CLIENT_SECRET (the confidential `amie` client; read from the desktop app's AMP/keycloak.json when unset).

Error handling, in one place (`request`): every non-2xx answer becomes an AmieError subclass carrying the
status, the backend's `code` and `detail` (problem+json) or its plain-text reason, so callers can

    try:
        client.upload("echo.jar", open("echo.jar", "rb").read())
    except InvalidPackage as e:      # 400: the jar does not satisfy the package contract
        print("fix the jar:", e.detail)
    except NotSignedIn:              # 401: sign in again (the token expired)
        ...
    except Forbidden:                # 403: the token is someone else's
        ...
    except StoreUnavailable as e:    # 502/503: the S3 service is down; `request` already retried
        ...

Exit codes of the CLI: 0 ok, 1 the backend refused (4xx), 3 the backend or the store is down (connection error,
5xx), 2 wrong usage.
"""
import json, os, sys, time, urllib.error, urllib.parse, urllib.request, uuid

BACKEND = os.environ.get("AMIE_BACKEND_URL", "http://localhost:8090").rstrip("/")
KEYCLOAK = os.environ.get("KEYCLOAK_URL", "http://localhost:8080").rstrip("/")
TOKEN_FILE = os.path.expanduser("~/.amie-token.json")
APP_KEYCLOAK_JSON = os.environ.get("AMIE_APP_KEYCLOAK_JSON", os.path.expanduser("~/Documents/GitHub/amieAppStable/amieApplication/desktopApp/AMP/keycloak.json"))


# ---- errors ---------------------------------------------------------------------------------------------

class AmieError(Exception):
    """Any answer that is not a success. `status` is the HTTP status, `code` the backend's machine-readable
    name (problem+json `code`, e.g. `forbidden`, `store_unavailable`), `detail` the human-readable reason."""
    def __init__(self, status, detail, code=None, body=None, error_id=None):
        super().__init__(f"{status} {code or ''}: {detail}".replace("  ", " "))
        self.status, self.detail, self.code, self.body, self.error_id = status, detail, code, body, error_id

class BadRequest(AmieError): "400: the request was wrong (invalid path, state name, JSON, missing field)"
class InvalidPackage(BadRequest): "400 from /upload: the jar does not satisfy the package contract"
class NotSignedIn(AmieError): "401: no token, or it expired"
class Forbidden(AmieError): "403: the token belongs to another user"
class NotFound(AmieError): "404: no such file, state or profile"
class Conflict(AmieError): "409: the username is taken"
class TooLarge(AmieError): "413: over the upload or state limit"
class TooMany(AmieError): "429: too many notes in a minute"
class StoreUnavailable(AmieError): "502/503: the S3 service is down or refused; try later"
class BackendError(AmieError): "500: a bug on the server; `error_id` is in its log"
class Unreachable(AmieError): "the backend itself is not answering"

def error_for(status, body, path):
    """Builds the exception for a failed call from whatever the backend sent."""
    code, detail, error_id = None, body.strip()[:300] or f"HTTP {status}", None
    try:
        doc = json.loads(body)
        if isinstance(doc, dict):
            code = doc.get("code") or doc.get("error")
            detail = doc.get("detail") or doc.get("error_description") or doc.get("message") or detail
            error_id = doc.get("errorId")
    except ValueError:
        pass  # plain text, as the upload routes answer
    cls = {400: BadRequest, 401: NotSignedIn, 403: Forbidden, 404: NotFound, 409: Conflict, 413: TooLarge, 429: TooMany,
           500: BackendError, 502: StoreUnavailable, 503: StoreUnavailable}.get(status, AmieError)
    if status == 400 and path.startswith("/upload"):
        cls = InvalidPackage
    return cls(status, detail, code, body, error_id)


# ---- the client -----------------------------------------------------------------------------------------

class AmieClient:
    def __init__(self, backend=BACKEND, token=None, retries=2):
        self.backend, self.token, self.retries = backend, token, retries

    def request(self, method, path, body=None, raw=None, content_type=None, params=None, auth=True, timeout=120):
        """One call. Raises an AmieError subclass on any failure; retries 502/503 and connection errors."""
        url = self.backend + path + ("?" + urllib.parse.urlencode(params) if params else "")
        headers = {}
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        data = None
        if body is not None:
            data, headers["Content-Type"] = json.dumps(body).encode(), "application/json"
        elif raw is not None:
            data, headers["Content-Type"] = raw, content_type or "application/octet-stream"
        last = None
        for attempt in range(self.retries + 1):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, data=data, method=method, headers=headers), timeout=timeout) as resp:
                    return resp.status, resp.read().decode(errors="replace"), dict(resp.headers)
            except urllib.error.HTTPError as e:
                err = error_for(e.code, e.read().decode(errors="replace"), path)
                if not isinstance(err, StoreUnavailable):
                    raise err
                last = err
            except urllib.error.URLError as e:
                last = Unreachable(0, f"{self.backend} is not answering: {e.reason}")
            if attempt < self.retries:
                time.sleep(0.5 * (attempt + 1))  # the store is usually back within a second
        raise last

    def _json(self, *a, **kw):
        _, text, _ = self.request(*a, **kw)
        return json.loads(text) if text else None

    # -- sign-in
    def login(self, username, password, register=True):
        """A backend account's token; registers the account first when it does not exist yet."""
        try:
            self.token = self._json("POST", "/login", {"username": username, "password": password}, auth=False)["token"]
        except NotSignedIn:
            if not register:
                raise
            try:
                self.request("POST", "/register", {"username": username, "password": password}, auth=False)
            except Conflict:  # exists, so the password was simply wrong
                raise NotSignedIn(401, f"wrong password for {username}", "unauthorized")
            self.token = self._json("POST", "/login", {"username": username, "password": password}, auth=False)["token"]
        return self.token

    def keycloak_login(self, username, password, client_secret=None, keycloak=KEYCLOAK, realm="master"):
        """The desktop app's sign-in: the password grant on the confidential `amie` client."""
        secret = client_secret or os.environ.get("KEYCLOAK_CLIENT_SECRET")
        if not secret and os.path.exists(APP_KEYCLOAK_JSON):
            secret = json.load(open(APP_KEYCLOAK_JSON)).get("clientSecret")
        if not secret:
            raise BadRequest(0, "no client secret: set KEYCLOAK_CLIENT_SECRET", "missing_secret")
        form = urllib.parse.urlencode({"grant_type": "password", "client_id": "amie", "client_secret": secret,
                                       "username": username, "password": password, "scope": "openid profile email"}).encode()
        req = urllib.request.Request(f"{keycloak}/realms/{realm}/protocol/openid-connect/token", data=form, method="POST",
                                     headers={"Content-Type": "application/x-www-form-urlencoded"})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                self.token = json.load(resp)["access_token"]
        except urllib.error.HTTPError as e:
            doc = json.loads(e.read().decode(errors="replace") or "{}")
            # Keycloak's own shape: {"error":"invalid_grant","error_description":"Invalid user credentials"}
            raise NotSignedIn(e.code, doc.get("error_description", "Keycloak refused"), doc.get("error"))
        except urllib.error.URLError as e:
            raise Unreachable(0, f"Keycloak at {keycloak} is not answering: {e.reason}")
        return self.token

    def me(self):
        return self._json("GET", "/me")

    # -- packages
    def packages(self):
        return self._json("GET", "/packages", auth=False)

    def user_packages(self, username):
        return self._json("GET", "/user-packages", params={"username": username}, auth=False)

    def package(self, path):
        return self._json("GET", "/package", params={"path": path}, auth=False)

    def upload(self, file_name, data, username=None, language="jar", content_type="application/java-archive"):
        """Publishes `data` as uploads/<user>/<language>/<file_name>. A jar must satisfy the package contract."""
        username = username or self.me()["username"]
        boundary = "----amie" + uuid.uuid4().hex
        parts = b""
        for k, v in {"username": username, "progLanguage": language}.items():
            parts += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
        parts += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file_name}\"\r\n"
                  f"Content-Type: {content_type}\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
        _, text, _ = self.request("POST", "/upload", raw=parts, content_type=f"multipart/form-data; boundary={boundary}", timeout=600)
        return text

    def delete(self, relative_path, username=None):
        """Removes uploads/<user>/<relative_path>; False when it was not there."""
        username = username or self.me()["username"]
        try:
            self.request("DELETE", "/package", params={"path": f"uploads/{username}/{relative_path}"})
            return True
        except NotFound:
            return False

    # -- saved states (devices among them) and the error log
    def states(self):
        return self._json("GET", "/state")

    def state(self, name):
        return self._json("GET", f"/state/{name}")

    def save_state(self, name, value):
        self.request("PUT", f"/state/{name}", raw=json.dumps(value).encode(), content_type="application/json")

    def delete_state(self, name):
        try:
            self.request("DELETE", f"/state/{name}")
            return True
        except NotFound:
            return False

    def errors(self, username=None):
        return self._json("GET", "/errors", params={"username": username or self.me()["username"]})

    def report_error(self, error, message):
        """What the desktop app does for every ERROR line in its console."""
        self.request("POST", "/errors", {"error": error, "message": message})

    def clear_errors(self, username=None):
        self.request("DELETE", "/errors", params={"username": username or self.me()["username"]})

    # -- notes on packages
    def notes(self, package_path):
        return self._json("GET", "/notes", params={"path": package_path}, auth=False)

    def add_note(self, package_path, text):
        """Plain text, 1 to 2000 characters after cleaning; at most 10 a minute (TooMany)."""
        return self._json("POST", "/notes", {"path": package_path, "text": text})

    def delete_note(self, note_id):
        try:
            self.request("DELETE", f"/notes/{note_id}")
            return True
        except NotFound:
            return False

    # -- nickname and picture
    def profile(self, username=None):
        return self._json("GET", "/profile", params={"username": username or self.me()["username"]}, auth=False)

    def set_nickname(self, nickname):
        """2 to 32 letters, digits, spaces, dots, dashes or underscores; Conflict when someone else has it."""
        me = self.me()
        body = {"fullName": me.get("fullName", ""), "email": me.get("email", ""), "bio": me.get("bio", ""), "nickname": nickname}
        self.request("POST", "/profile", body, params={"username": me["username"]})
        return self.profile(me["username"])

    def set_avatar(self, file_name, data):
        """PNG, JPEG, GIF or WebP, at most 512 KB and 2048 px; the backend decides the type from the bytes."""
        username = self.me()["username"]
        boundary = "----amie" + uuid.uuid4().hex
        parts = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file_name}\"\r\n"
                 f"Content-Type: application/octet-stream\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
        return self._json("POST", "/avatar", raw=parts, content_type=f"multipart/form-data; boundary={boundary}", params={"username": username})

    def get_avatar(self, username):
        """The picture's bytes and content type, or None."""
        try:
            url = f"{self.backend}/avatar?{urllib.parse.urlencode({'username': username})}"
            with urllib.request.urlopen(url, timeout=30) as resp:
                return resp.read(), resp.headers.get("Content-Type")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise error_for(e.code, e.read().decode(errors="replace"), "/avatar")

    def clear_avatar(self):
        try:
            self.request("DELETE", "/avatar", params={"username": self.me()["username"]})
            return True
        except NotFound:
            return False

    # -- background, likes, download counts
    def set_background(self, file_name, data):
        username = self.me()["username"]
        boundary = "----amie" + uuid.uuid4().hex
        parts = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file_name}\"\r\n"
                 f"Content-Type: application/octet-stream\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
        return self._json("POST", "/background", raw=parts, content_type=f"multipart/form-data; boundary={boundary}", params={"username": username})

    def clear_background(self):
        try:
            self.request("DELETE", "/background", params={"username": self.me()["username"]})
            return True
        except NotFound:
            return False

    def toggle_like(self, package_path):
        return self._json("POST", "/likes", params={"path": package_path})

    def likes(self):
        return self._json("GET", "/likes")

    def stats(self, username):
        return self._json("GET", "/stats", params={"username": username}, auth=False)

    # -- administration (a Keycloak token with the admin role; `keycloak` login, not `login`)
    def admin_users(self):
        return self._json("GET", "/admin/users")

    def admin_notes(self, limit=100):
        return self._json("GET", "/admin/notes", params={"limit": limit})

    def admin_delete_note(self, note_id):
        self.request("DELETE", f"/admin/notes/{note_id}")

    def admin_pending_avatars(self):
        return self._json("GET", "/admin/avatars/pending")

    def admin_approve_avatar(self, username):
        self.request("POST", f"/admin/users/{username}/avatar/approve")

    def admin_badge(self, username, badge, grant=True):
        if grant:
            return self._json("POST", f"/admin/users/{username}/badges/{badge}")
        self.request("DELETE", f"/admin/users/{username}/badges/{badge}")

    # -- publications and linked accounts
    def publications(self, username=None):
        return self._json("GET", "/publications", params={"username": username or self.me()["username"]}, auth=False)

    def add_publication(self, title, venue="", year=None, url="", doi=""):
        return self._json("POST", "/publications", {"title": title, "venue": venue, "year": year, "url": url, "doi": doi})

    def delete_publication(self, publication_id):
        try:
            self.request("DELETE", f"/publications/{publication_id}")
            return True
        except NotFound:
            return False

    def link_accounts(self, github="", hugging_face=""):
        """GitHub and Hugging Face usernames shown on the public page; the backend checks they look like handles."""
        me = self.me()
        body = {"fullName": me.get("fullName", ""), "email": me.get("email", ""), "bio": me.get("bio", ""), "nickname": me.get("nickname", ""),
                "github": github, "huggingFace": hugging_face}
        self.request("POST", "/profile", body, params={"username": me["username"]})
        return self.profile(me["username"])

    def admin_user_action(self, username, action):
        """action: nickname | avatar | delete (what to take away)."""
        path = {"nickname": f"/admin/users/{username}/nickname", "avatar": f"/admin/users/{username}/avatar", "delete": f"/admin/users/{username}"}[action]
        self.request("DELETE", path)


# ---- the CLI --------------------------------------------------------------------------------------------

def load_token():
    if os.environ.get("AMIE_TOKEN"):
        return os.environ["AMIE_TOKEN"]
    try:
        return json.load(open(TOKEN_FILE))["token"]
    except (OSError, ValueError, KeyError):
        return None

def save_token(token):
    with open(TOKEN_FILE, "w") as f:
        json.dump({"token": token, "backend": BACKEND, "saved": time.strftime("%Y-%m-%dT%H:%M:%S")}, f)
    os.chmod(TOKEN_FILE, 0o600)

def main(argv):
    if len(argv) < 2:
        print(__doc__); return 2
    cmd, args = argv[1], argv[2:]
    client = AmieClient(token=load_token())
    out = lambda v: print(json.dumps(v, indent=2) if not isinstance(v, str) else v)
    try:
        if cmd in ("login", "keycloak"):
            if len(args) != 2: print("usage: login|keycloak <username> <password>"); return 2
            token = client.login(*args) if cmd == "login" else client.keycloak_login(*args)
            save_token(token); out(f"signed in as {client.me()['username']}; token kept in {TOKEN_FILE}")
        elif cmd == "me": out(client.me())
        elif cmd == "packages": out(client.user_packages(args[0]) if args else client.packages())
        elif cmd == "view": out(client.package(args[0]))
        elif cmd == "upload":
            path = args[0]; lang = args[1] if len(args) > 1 else ("jar" if path.endswith(".jar") else "unknown")
            out(client.upload(os.path.basename(path), open(path, "rb").read(), language=lang,
                              content_type="application/java-archive" if lang == "jar" else "application/octet-stream"))
        elif cmd == "delete": out("removed" if client.delete(args[0]) else "was not there")
        elif cmd == "state":
            sub = args[0]
            if sub == "list": out(client.states())
            elif sub == "get": out(client.state(args[1]))
            elif sub == "put": client.save_state(args[1], json.loads(args[2])); out(f"saved state {args[1]}")
            elif sub == "delete": out("removed" if client.delete_state(args[1]) else "was not there")
            else: print("usage: state list|get <name>|put <name> <json>|delete <name>"); return 2
        elif cmd == "errors":
            sub = args[0]
            if sub == "list": out(client.errors())
            elif sub == "report": client.report_error(args[1], args[2]); out("reported")
            elif sub == "clear": client.clear_errors(); out("cleared")
            else: print("usage: errors list|report <error> <message>|clear"); return 2
        elif cmd == "notes":
            sub = args[0]
            if sub == "list": out(client.notes(args[1]))
            elif sub == "add": out(client.add_note(args[1], args[2]))
            elif sub == "delete": out("removed" if client.delete_note(int(args[1])) else "was not there")
            else: print("usage: notes list <path>|add <path> <text>|delete <id>"); return 2
        elif cmd == "nick": out(client.set_nickname(args[0] if args else ""))
        elif cmd == "link": out(client.link_accounts(args[0] if args else "", args[1] if len(args) > 1 else ""))
        elif cmd == "background":
            if args[0] == "set": out(client.set_background(os.path.basename(args[1]), open(args[1], "rb").read()))
            elif args[0] == "clear": out("removed" if client.clear_background() else "there was none")
            else: print("usage: background set <file>|clear"); return 2
        elif cmd == "like": out(client.toggle_like(args[0]))
        elif cmd == "likes": out(client.likes())
        elif cmd == "stats": out(client.stats(args[0]))
        elif cmd == "pubs":
            sub = args[0]
            if sub == "list": out(client.publications(args[1] if len(args) > 1 else None))
            elif sub == "add": out(client.add_publication(args[1], args[2] if len(args) > 2 else "", int(args[3]) if len(args) > 3 and args[3] else None, args[4] if len(args) > 4 else "", args[5] if len(args) > 5 else ""))
            elif sub == "delete": out("removed" if client.delete_publication(int(args[1])) else "was not there")
            else: print("usage: pubs list [user]|add <title> [venue] [year] [url] [doi]|delete <id>"); return 2
        elif cmd == "avatar":
            sub = args[0]
            if sub == "set": out(client.set_avatar(os.path.basename(args[1]), open(args[1], "rb").read()))
            elif sub == "get":
                got = client.get_avatar(args[2] if len(args) > 2 else client.me()["username"])
                if got is None: out("no picture")
                else: open(args[1], "wb").write(got[0]); out(f"wrote {args[1]} ({got[1]}, {len(got[0])} bytes)")
            elif sub == "clear": out("removed" if client.clear_avatar() else "there was none")
            else: print("usage: avatar set <file>|get <out> [user]|clear"); return 2
        elif cmd == "admin":
            sub = args[0]
            if sub == "users": out(client.admin_users())
            elif sub == "notes": out(client.admin_notes())
            elif sub == "note-delete": client.admin_delete_note(int(args[1])); out("removed")
            elif sub in ("user-delete", "user-nickname", "user-avatar"): client.admin_user_action(args[1], sub.split("-")[1]); out("done")
            elif sub == "avatars": out(client.admin_pending_avatars())
            elif sub == "approve": client.admin_approve_avatar(args[1]); out("approved")
            elif sub == "badge": out(client.admin_badge(args[1], args[2], True))
            elif sub == "unbadge": client.admin_badge(args[1], args[2], False); out("revoked")
            else: print("usage: admin users|notes|note-delete <id>|user-delete <user>|user-nickname <user>|user-avatar <user>|avatars|approve <user>|badge <user> <badge>|unbadge <user> <badge>"); return 2
        else:
            print(f"unknown command {cmd}"); print(__doc__); return 2
        return 0
    except (IndexError, ValueError) as e:
        print(f"usage error: {e}", file=sys.stderr); return 2
    except InvalidPackage as e:
        print(f"the jar was refused: {e.detail}\n(a package needs run(String): List and close(); see make-package-jar.sh)", file=sys.stderr); return 1
    except NotSignedIn as e:
        print(f"not signed in: {e.detail} (run `login` or `keycloak` first)", file=sys.stderr); return 1
    except TooMany as e:
        print(f"slow down: {e.detail}", file=sys.stderr); return 1
    except (Forbidden, NotFound, Conflict, TooLarge, BadRequest) as e:
        print(f"refused ({e.status} {e.code or ''}): {e.detail}", file=sys.stderr); return 1
    except StoreUnavailable as e:
        print(f"the package store is down ({e.status} {e.code or ''}): {e.detail}; try again in a moment", file=sys.stderr); return 3
    except BackendError as e:
        print(f"the backend failed: {e.detail}; look for {e.error_id} in logs/backend.log", file=sys.stderr); return 3
    except Unreachable as e:
        print(f"{e.detail} (start the stack with ./run.sh)", file=sys.stderr); return 3

if __name__ == "__main__":
    sys.exit(main(sys.argv))
