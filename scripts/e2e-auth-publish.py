#!/usr/bin/env python3
"""End-to-end check of sign-in, registration, publishing and SSO across the running Amie stack.

Needs: Keycloak (docker, localhost:8080), the backend, the S3 service and the site started with ./run.sh,
Postgres for the backend, and docker access to read the Keycloak admin credentials. Creates a throwaway
Keycloak user and a throwaway backend account, publishes a small package jar, and removes everything again.

    python3 scripts/e2e-auth-publish.py            # all steps
    AMIE_BACKEND_URL=... AMIE_SITE_URL=... KEYCLOAK_URL=... override the addresses
"""
import base64, hashlib, http.cookiejar, json, os, re, secrets, subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request

BACKEND = os.environ.get("AMIE_BACKEND_URL", "http://localhost:8090").rstrip("/")
SITE = os.environ.get("AMIE_SITE_URL", "http://localhost:8081").rstrip("/")
KEYCLOAK = os.environ.get("KEYCLOAK_URL", "http://localhost:8080").rstrip("/")
REALM = f"{KEYCLOAK}/realms/master"
APP_KEYCLOAK_JSON = os.environ.get("AMIE_APP_KEYCLOAK_JSON", os.path.expanduser("~/Documents/GitHub/amieAppStable/amieApplication/desktopApp/AMP/keycloak.json"))
STAMP = time.strftime("%Y%m%d%H%M%S")
results = []

def step(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail else ""))
    return ok

def call(method, url, body=None, headers=None, form=None, raw=None, opener=None, timeout=60):
    data = None
    h = dict(headers or {})
    if form is not None:
        data = urllib.parse.urlencode(form).encode(); h.setdefault("Content-Type", "application/x-www-form-urlencoded")
    elif body is not None:
        data = json.dumps(body).encode(); h.setdefault("Content-Type", "application/json")
    elif raw is not None:
        data = raw
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        resp = (opener or urllib.request).urlopen(req, timeout=timeout) if opener is None else opener.open(req, timeout=timeout)
        return resp.status, resp.read().decode(errors="replace"), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace"), dict(e.headers)

def multipart(fields, file_field, file_name, file_bytes, content_type="application/octet-stream"):
    boundary = "----amie" + secrets.token_hex(8)
    out = b""
    for k, v in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
    out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; filename=\"{file_name}\"\r\nContent-Type: {content_type}\r\n\r\n".encode() + file_bytes + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return out, f"multipart/form-data; boundary={boundary}"

def jwt_claims(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))

def build_jar():
    """A package jar the backend accepts: one class with run(String): List and close()."""
    java_home = os.environ.get("JAVA_HOME") or "/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home"
    d = tempfile.mkdtemp(prefix="amie-e2e-")
    src = os.path.join(d, "EchoE2E.java")
    open(src, "w").write("public class EchoE2E { public java.util.List<String> run(String text) { return java.util.List.of(\"echo: \" + text); } public void close() {} }\n")
    subprocess.run([os.path.join(java_home, "bin", "javac"), "-d", d, src], check=True)
    jar = os.path.join(d, f"echo-e2e-{STAMP}.jar")
    subprocess.run([os.path.join(java_home, "bin", "jar"), "cf", jar, "-C", d, "EchoE2E.class"], check=True)
    return jar

def keycloak_admin_token():
    env = subprocess.run(["docker", "inspect", "my-ubuntu-image-keycloak-1", "--format", "{{range .Config.Env}}{{println .}}{{end}}"], capture_output=True, text=True, check=True).stdout
    kv = dict(line.split("=", 1) for line in env.splitlines() if "=" in line)
    st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "password", "client_id": "admin-cli", "username": kv["KEYCLOAK_ADMIN"], "password": kv["KEYCLOAK_ADMIN_PASSWORD"]})
    if st != 200: raise SystemExit(f"no Keycloak admin token: {st} {body[:200]}")
    return json.loads(body)["access_token"]

def main():
    client_secret = json.load(open(APP_KEYCLOAK_JSON))["clientSecret"]
    kc_user, kc_pw = f"e2e-kc-{STAMP}", "E2e-pass-" + secrets.token_hex(4)
    be_user, be_pw = f"e2e-be-{STAMP}", "backend-pw-" + secrets.token_hex(4)
    admin = None; kc_user_id = None; jar = None

    # ---- the services answer
    st, body, _ = call("GET", f"{BACKEND}/packages")
    step("backend answers /packages with a list", st == 200 and body.strip().startswith("["), f"{BACKEND}")
    st, body, _ = call("GET", f"{REALM}")
    step("Keycloak realm answers", st == 200 and "public_key" in body)
    st, body, _ = call("GET", f"{SITE}/api/jars")
    step("site answers /api/jars", st == 200, f"{SITE} -> {st}")

    try:
        # ---- backend accounts: register, login, guest, token routes
        st, body, _ = call("POST", f"{BACKEND}/register", body={"username": be_user, "password": be_pw})
        step("backend /register creates an account", st == 200 and "registered" in body, body[:80])
        st, body, _ = call("POST", f"{BACKEND}/register", body={"username": be_user, "password": be_pw})
        step("backend /register refuses the same name with 409", st == 409)
        st, body, _ = call("POST", f"{BACKEND}/register", body={"username": f"{be_user}-x"})
        step("backend /register without a password is 400", st == 400)
        st, body, _ = call("POST", f"{BACKEND}/login", body={"username": be_user, "password": be_pw})
        be_token = json.loads(body).get("token") if st == 200 else None
        step("backend /login answers a token", st == 200 and bool(be_token))
        st, body, _ = call("POST", f"{BACKEND}/login", body={"username": be_user, "password": "wrong"})
        step("backend /login with a wrong password is 401", st == 401)
        st, body, _ = call("POST", f"{BACKEND}/login", body={"username": f"guest-e2e-{STAMP}", "password": ""})
        guest_token = json.loads(body).get("token") if st == 200 else None
        step("backend /login as guest-* with no password answers a guest token", st == 200 and bool(guest_token) and jwt_claims(guest_token).get("sub") == "guest")
        st, body, _ = call("GET", f"{BACKEND}/dashboard", headers={"Authorization": f"Bearer {be_token}"})
        step("backend /dashboard greets the account's token", st == 200 and be_user in body, body[:80])
        st, body, _ = call("GET", f"{BACKEND}/dashboard", headers={"Authorization": "Bearer not-a-token"})
        step("backend /dashboard refuses a bad token with 401", st == 401)

        # ---- site proxies for the backend account
        st, body, _ = call("POST", f"{SITE}/api/login", body={"username": be_user, "password": be_pw})
        step("site /api/login proxies the backend login", st == 200 and "token" in body, body[:80])
        st, body, _ = call("POST", f"{SITE}/api/login", body={"username": be_user, "password": "wrong"})
        step("site /api/login answers invalid_credentials for a wrong password", st == 401 and "invalid_credentials" in body, f"{st} {body[:60]}")
        st, body, _ = call("POST", f"{SITE}/api/register", body={"username": be_user, "password": be_pw})
        step("site /api/register says username_taken for an existing name", st == 409 and "username_taken" in body, f"{st} {body[:60]}")

        # ---- Keycloak: a throwaway user, direct-grant sign-in as the desktop app does
        admin = keycloak_admin_token()
        st, body, headers = call("POST", f"{KEYCLOAK}/admin/realms/master/users", headers={"Authorization": f"Bearer {admin}"}, body={
            "username": kc_user, "enabled": True, "emailVerified": True, "email": f"{kc_user}@example.test", "firstName": "E2E", "lastName": "Test",
            "credentials": [{"type": "password", "value": kc_pw, "temporary": False}]})
        kc_user_id = headers.get("Location", "").rstrip("/").split("/")[-1] if st == 201 else None
        step("Keycloak admin API creates a throwaway user", st == 201 and bool(kc_user_id), f"{st}")
        st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "password", "client_id": "amie", "client_secret": client_secret, "username": kc_user, "password": kc_pw, "scope": "openid profile email offline_access"})
        tokens = json.loads(body) if st == 200 else {}
        kc_token = tokens.get("access_token")
        step("Keycloak direct grant (the app's password login) answers tokens", st == 200 and bool(kc_token) and bool(tokens.get("refresh_token")), f"{st} {body[:80] if st != 200 else 'iss=' + jwt_claims(kc_token).get('iss', '')}")
        st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "password", "client_id": "amie", "client_secret": client_secret, "username": kc_user, "password": "wrong"})
        step("Keycloak refuses a wrong password with invalid_grant", st == 401 and "invalid_grant" in body)
        st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "refresh_token", "client_id": "amie", "client_secret": client_secret, "refresh_token": tokens.get("refresh_token", "")})
        step("Keycloak refresh grant (the app's session restore) works", st == 200 and "access_token" in body)

        # ---- the backend accepts the Keycloak token
        st, body, _ = call("GET", f"{BACKEND}/me", headers={"Authorization": f"Bearer {kc_token}"})
        me = json.loads(body) if st == 200 else {}
        step("backend /me accepts the Keycloak token and names the user", st == 200 and me.get("username") == kc_user and "password" not in body, f"{st} {body[:100]}")

        # ---- publishing from the app: POST /upload with the Keycloak token
        jar = build_jar(); jar_name = os.path.basename(jar)
        data, ctype = multipart({"progLanguage": "jar", "username": kc_user}, "file", jar_name, open(jar, "rb").read())
        st, body, _ = call("POST", f"{BACKEND}/upload", raw=data, headers={"Authorization": f"Bearer {kc_token}", "Content-Type": ctype})
        step("backend /upload stores a valid package jar", st == 200 and "uploaded" in body.lower(), f"{st} {body[:100]}")
        bad, ctype2 = multipart({"progLanguage": "jar", "username": kc_user}, "file", "broken.jar", b"not a jar at all")
        st, body, _ = call("POST", f"{BACKEND}/upload", raw=bad, headers={"Authorization": f"Bearer {kc_token}", "Content-Type": ctype2})
        step("backend /upload refuses a jar that is not a package with 400 and a reason", st == 400 and len(body) > 5, f"{st} {body[:100]}")
        st, body, _ = call("POST", f"{BACKEND}/upload", raw=data, headers={"Authorization": f"Bearer {be_token}", "Content-Type": ctype})
        step("backend /upload under another user's name is 403", st == 403, f"{st}")
        st, body, _ = call("GET", f"{BACKEND}/user-packages?username={kc_user}")
        listed = json.loads(body) if st == 200 else []
        step("backend /user-packages lists the published jar", st == 200 and any(p.get("name") == jar_name for p in listed), f"{[p.get('name') for p in listed]}")
        st, body, _ = call("GET", f"{BACKEND}/package?path=" + urllib.parse.quote(f"uploads/{kc_user}/jar/{jar_name}"))
        step("backend /package describes the stored file", st == 200 and jar_name in body, f"{st}")
        st, body, _ = call("GET", f"{SITE}/api/repo-by-username?username={kc_user}&refresh=1")
        step("site lists the author's packages (what the app checks after publishing)", st == 200 and jar_name in body, f"{st} {body[:100]}")

        # ---- publishing through the site's upload proxy (the website's upload page)
        st, body, _ = call("POST", f"{SITE}/api/upload", raw=data, headers={"Authorization": f"Bearer {kc_token}", "Content-Type": ctype})
        step("site /api/upload forwards a publish to the backend", st == 200, f"{st} {body[:100]}")
        st, body, _ = call("POST", f"{SITE}/api/upload", raw=data, headers={"Content-Type": ctype})
        step("site /api/upload without a bearer is 401", st == 401, f"{st}")

        # ---- saved states and the error log with the Keycloak token
        st, body, _ = call("PUT", f"{BACKEND}/state/e2e-{STAMP}", raw=b'{"threshold": 0.6}', headers={"Authorization": f"Bearer {kc_token}", "Content-Type": "application/json"})
        step("backend PUT /state stores a JSON state", st in (200, 204), f"{st} {body[:60]}")
        st, body, _ = call("GET", f"{BACKEND}/state/e2e-{STAMP}", headers={"Authorization": f"Bearer {kc_token}"})
        step("backend GET /state reads it back", st == 200 and "0.6" in body, f"{st} {body[:60]}")
        st, body, _ = call("GET", f"{BACKEND}/state", headers={"Authorization": f"Bearer {kc_token}"})
        step("backend GET /state lists it", st == 200 and f"e2e-{STAMP}" in body)
        st, body, _ = call("DELETE", f"{BACKEND}/state/e2e-{STAMP}", headers={"Authorization": f"Bearer {kc_token}"})
        step("backend DELETE /state removes it", st in (200, 204))
        st, body, _ = call("POST", f"{BACKEND}/errors", body={"error": "E2E", "message": "hello from the test"}, headers={"Authorization": f"Bearer {kc_token}"})
        step("backend POST /errors files an app error", st in (200, 204), f"{st}")
        st, body, _ = call("GET", f"{BACKEND}/errors?username={kc_user}", headers={"Authorization": f"Bearer {kc_token}"})
        step("backend GET /errors lists it", st == 200 and "hello from the test" in body, f"{st} {body[:80]}")
        call("DELETE", f"{BACKEND}/errors?username={kc_user}", headers={"Authorization": f"Bearer {kc_token}"})

        # ---- SSO handoff: the app opens the website signed in
        st, body, _ = call("POST", f"{SITE}/api/sso/handoff", body=tokens)
        code = json.loads(body).get("code") if st == 200 else None
        step("site /api/sso/handoff turns the app's tokens into a one-time code", st == 200 and bool(code), f"{st} {body[:80]}")
        st, body, _ = call("POST", f"{SITE}/api/sso/claim", form={"code": code or ""})
        claimed = json.loads(body) if st == 200 else {}
        step("site /api/sso/claim hands the tokens back once", st == 200 and claimed.get("access_token") == kc_token, f"{st}")
        st, body, _ = call("POST", f"{SITE}/api/sso/claim", form={"code": code or ""})
        step("a second claim of the same code is 404", st == 404)
        st, body, _ = call("POST", f"{SITE}/api/sso/handoff", body={"access_token": "junk"})
        step("a handoff without a real token is 400", st == 400)

        # ---- the site's own Keycloak sign-in (password through /api/keycloak/token)
        st, body, _ = call("POST", f"{SITE}/api/keycloak/token", form={"username": kc_user, "password": kc_pw})
        step("site /api/keycloak/token signs in with username and password", st == 200 and "access_token" in body, f"{st} {body[:80]}")
        st, body, _ = call("POST", f"{SITE}/api/keycloak/token", form={"refresh_token": tokens.get("refresh_token", "")})
        step("site /api/keycloak/token refreshes", st == 200 and "access_token" in body, f"{st}")
        st, body, _ = call("POST", f"{SITE}/api/keycloak/device", form={})
        step("site /api/keycloak/device starts the QR flow (or says it is off)", st in (200, 400), f"{st} {body[:60]}")

        # ---- browser SSO as the desktop app does it: authorization code + PKCE on the loopback redirect
        verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).rstrip(b"=").decode()
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
        state = secrets.token_urlsafe(18)
        redirect = "http://localhost:48080/callback"
        auth_url = f"{REALM}/protocol/openid-connect/auth?" + urllib.parse.urlencode({"client_id": "amie", "response_type": "code", "scope": "openid profile email offline_access", "redirect_uri": redirect, "state": state, "code_challenge": challenge, "code_challenge_method": "S256"})
        jar_cookies = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar_cookies), type("NoRedirect", (urllib.request.HTTPRedirectHandler,), {"redirect_request": lambda *a, **k: None})())
        st, page, _ = call("GET", auth_url, opener=opener)
        action = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', page) or re.search(r'action="([^"]+)"[^>]*id="kc-form-login"', page)
        step("Keycloak serves the hosted login page for the app's redirect URI", st == 200 and action is not None, f"{st}")
        location = ""
        if action:
            st, body, headers = call("POST", action.group(1).replace("&amp;", "&"), form={"username": kc_user, "password": kc_pw, "credentialId": ""}, opener=opener)
            location = headers.get("Location", "")
            params = dict(urllib.parse.parse_qsl(urllib.parse.urlparse(location).query))
            step("the login form redirects back to the loopback with a code and the state", st in (302, 303) and location.startswith(redirect) and "code" in params and params.get("state") == state, f"{st} {location[:60]}")
            st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "authorization_code", "client_id": "amie", "client_secret": client_secret, "code": params.get("code", ""), "redirect_uri": redirect, "code_verifier": verifier})
            step("the code exchanges for tokens with the PKCE verifier", st == 200 and "access_token" in body, f"{st} {body[:80]}")
            st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "authorization_code", "client_id": "amie", "client_secret": client_secret, "code": params.get("code", ""), "redirect_uri": redirect, "code_verifier": "wrong-verifier"})
            step("a used code or wrong verifier is refused", st == 400)

        # ---- unpublish, as the app's "remove publish" does
        st, body, _ = call("DELETE", f"{BACKEND}/upload?filename={urllib.parse.quote(jar_name)}&progLanguage=jar&username={kc_user}", headers={"Authorization": f"Bearer {kc_token}"})
        step("backend DELETE /upload removes the published jar", st == 200 and body.strip() == "removed", f"{st} {body}")
        st, body, _ = call("GET", f"{BACKEND}/user-packages?username={kc_user}")
        step("the jar is gone from the listing", st == 200 and jar_name not in body)
        st, body, _ = call("POST", f"{REALM}/protocol/openid-connect/logout", form={"client_id": "amie", "client_secret": client_secret, "refresh_token": tokens.get("refresh_token", "")})
        step("Keycloak logout ends the session", st in (200, 204))
    finally:
        # ---- cleanup
        st, body, _ = call("DELETE", f"{BACKEND}/user?username={be_user}&password={urllib.parse.quote(be_pw)}")
        step("cleanup: backend account removed", st == 200)
        if admin and kc_user_id:
            st, body, _ = call("DELETE", f"{KEYCLOAK}/admin/realms/master/users/{kc_user_id}", headers={"Authorization": f"Bearer {admin}"})
            step("cleanup: Keycloak user removed", st == 204, f"{st}")
        if jar:
            try: os.remove(jar)
            except OSError: pass
        # The backend made rows for the Keycloak user (/me) and the guest login; they have no password, so DELETE /user
        # cannot remove them. psql on the backend's database (application.properties) takes them out when it is there.
        psql = subprocess.run(["which", "psql"], capture_output=True, text=True).stdout.strip()
        if psql:
            names = f"'{kc_user}', 'guest-e2e-{STAMP}'"
            sql = (f"delete from user_activities where user_id in (select id from users where username in ({names}));"
                   f"delete from device_statuses where user_id in (select id from users where username in ({names}));"
                   f"delete from users where username in ({names});")
            done = subprocess.run([psql, "-d", os.environ.get("AMIE_DB", "AmiesUsersDB"), "-U", os.environ.get("AMIE_DB_USER", "samuel"), "-At", "-c", sql], capture_output=True, text=True)
            step("cleanup: backend rows of the Keycloak and guest users removed", done.returncode == 0, done.stderr.strip()[:80])
        else:
            step("cleanup: backend rows of the Keycloak and guest users removed", False, "no psql on PATH; remove them by hand")

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)} of {len(results)} checks passed")
    sys.exit(1 if failed else 0)

if __name__ == "__main__":
    main()
