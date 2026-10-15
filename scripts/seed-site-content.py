#!/usr/bin/env python3
"""Publishes content to browse on the site: a throwaway Keycloak user with a package jar, the desktop app's dummy
ONNX model (module.json, model.onnx, guide.md) and a few text files. Prints JSON with the user, tokens and files.

    python3 scripts/seed-site-content.py > seed.json
    python3 scripts/seed-site-content.py --cleanup seed.json
"""
import json, os, secrets, subprocess, sys, tempfile, time, urllib.parse
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location("e2e", os.path.join(os.path.dirname(__file__), "e2e-auth-publish.py"))
e2e = importlib.util.module_from_spec(spec); spec.loader.exec_module(e2e)
BACKEND, SITE, KEYCLOAK, REALM = e2e.BACKEND, e2e.SITE, e2e.KEYCLOAK, e2e.REALM
MODULE_DIR = os.environ.get("AMIE_DUMMY_MODULE", os.path.expanduser("~/Documents/GitHub/amieAppStable/amieApplication/desktopApp/AMP/modules/dummy"))

def upload(token, user, lang, name, data, ctype="application/octet-stream"):
    body, ct = e2e.multipart({"progLanguage": lang, "username": user}, "file", name, data, ctype)
    st, text, _ = e2e.call("POST", f"{BACKEND}/upload", raw=body, headers={"Authorization": f"Bearer {token}", "Content-Type": ct})
    return st, text

def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--cleanup":
        seed = json.load(open(sys.argv[2]))
        admin = e2e.keycloak_admin_token()
        for lang, name in seed["files"]:
            e2e.call("DELETE", f"{BACKEND}/upload?filename={urllib.parse.quote(name)}&progLanguage={lang}&username={seed['user']}", headers={"Authorization": f"Bearer {seed['access_token']}"})
        st, _, _ = e2e.call("DELETE", f"{KEYCLOAK}/admin/realms/master/users/{seed['user_id']}", headers={"Authorization": f"Bearer {admin}"})
        psql = subprocess.run(["which", "psql"], capture_output=True, text=True).stdout.strip()
        if psql:
            u = seed["user"]
            subprocess.run([psql, "-d", "AmiesUsersDB", "-U", "samuel", "-At", "-c", f"delete from user_activities where user_id in (select id from users where username = '{u}'); delete from device_statuses where user_id in (select id from users where username = '{u}'); delete from users where username = '{u}';"], capture_output=True)
        print(f"removed {seed['user']} ({st})", file=sys.stderr)
        return
    client_secret = json.load(open(e2e.APP_KEYCLOAK_JSON))["clientSecret"]
    stamp = time.strftime("%Y%m%d%H%M%S")
    user, pw = f"e2e-site-{stamp}", "Site-pass-" + secrets.token_hex(4)
    admin = e2e.keycloak_admin_token()
    st, body, headers = e2e.call("POST", f"{KEYCLOAK}/admin/realms/master/users", headers={"Authorization": f"Bearer {admin}"}, body={
        "username": user, "enabled": True, "emailVerified": True, "email": f"{user}@example.test", "firstName": "Site", "lastName": "Tester",
        "credentials": [{"type": "password", "value": pw, "temporary": False}]})
    assert st == 201, body
    user_id = headers["Location"].rstrip("/").split("/")[-1]
    st, body, _ = e2e.call("POST", f"{REALM}/protocol/openid-connect/token", form={"grant_type": "password", "client_id": "amie", "client_secret": client_secret, "username": user, "password": pw, "scope": "openid profile email offline_access"})
    assert st == 200, body
    tokens = json.loads(body)
    token = tokens["access_token"]
    e2e.call("GET", f"{BACKEND}/me", headers={"Authorization": f"Bearer {token}"})
    files = []
    jar = e2e.build_jar()
    st, text = upload(token, user, "jar", os.path.basename(jar), open(jar, "rb").read()); files.append(("jar", os.path.basename(jar))); print("jar", st, text, file=sys.stderr)
    for name in ("module.json", "model.onnx", "guide.md"):
        p = os.path.join(MODULE_DIR, name)
        if os.path.isfile(p):
            st, text = upload(token, user, "onnx", name, open(p, "rb").read()); files.append(("onnx", name)); print(name, st, text, file=sys.stderr)
    st, text = upload(token, user, "python", "predict.py", b"import json\n\ndef predict(text: str) -> dict:\n    \"\"\"Ask the served model.\"\"\"\n    return json.loads('{\"label\": \"lights\"}')\n", "text/x-python"); files.append(("python", "predict.py"))
    st, text = upload(token, user, "kotlin", "main.kts", b"val m = initMod(\"dummy\")\nlog(m.predict(\"turn the lights on\"))\n", "text/plain"); files.append(("kotlin", "main.kts"))
    st, text = upload(token, user, "data", "intents.csv", b"text,label\nhello there,greeting\nturn the lights on,lights\nwill it rain,weather\n", "text/csv"); files.append(("data", "intents.csv"))
    e2e.call("GET", f"{SITE}/api/repo-by-username?username={user}&refresh=1")
    e2e.call("GET", f"{SITE}/api/all-packages?refresh=1")
    print(json.dumps({"user": user, "password": pw, "user_id": user_id, "access_token": token, "refresh_token": tokens.get("refresh_token"), "expires_in": tokens.get("expires_in"), "files": files}))

if __name__ == "__main__":
    main()
