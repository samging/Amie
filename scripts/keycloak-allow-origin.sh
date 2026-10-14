#!/bin/zsh
# Adds a site origin to the Keycloak `amie` client's valid redirect URIs, so the browser sign-in works from that
# address. Needed every time the machine's LAN address changes (Keycloak answers "Invalid parameter: redirect_uri"
# otherwise), since the site redirects to its own origin (`<origin>/oauthpage`).
#
#   scripts/keycloak-allow-origin.sh                         # this machine's LAN address, port 8081
#   scripts/keycloak-allow-origin.sh http://192.168.1.50:8081
#
# Uses the admin account of the Keycloak Docker container (KEYCLOAK_CONTAINER, default my-ubuntu-image-keycloak-1)
# at KEYCLOAK_URL (default http://localhost:8080), realm master, client amie. Set KEYCLOAK_ADMIN and
# KEYCLOAK_ADMIN_PASSWORD to use another account instead of reading the container's.
set -euo pipefail
origin=${1:-}
if [[ -z $origin ]]; then
  lan=$(ipconfig getifaddr en0 2>/dev/null || ifconfig | awk '/inet / && $2 != "127.0.0.1" {print $2; exit}')
  origin="http://$lan:${AMIE_SITE_PORT:-8081}"
fi
origin=${origin%/}
kc=${KEYCLOAK_URL:-http://localhost:8080}
container=${KEYCLOAK_CONTAINER:-my-ubuntu-image-keycloak-1}
realm=${KEYCLOAK_REALM:-master}
client=${KEYCLOAK_CLIENT:-amie}

container_env() { docker inspect "$container" --format '{{range .Config.Env}}{{println .}}{{end}}' | awk -v k="$1" -F= '$1 == k {print substr($0, index($0, "=") + 1)}'; }
user=${KEYCLOAK_ADMIN:-$(container_env KEYCLOAK_ADMIN)}
pass=${KEYCLOAK_ADMIN_PASSWORD:-$(container_env KEYCLOAK_ADMIN_PASSWORD)}
token=$(curl -sf -m 10 --data-urlencode "username=$user" --data-urlencode "password=$pass" -d 'client_id=admin-cli&grant_type=password' \
  "$kc/realms/master/protocol/openid-connect/token" | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

python3 - "$kc" "$realm" "$client" "$origin" "$token" <<'PY'
import sys, json, urllib.request
kc, realm, client, origin, token = sys.argv[1:]
hdr = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

def call(method, path, body=None):
    req = urllib.request.Request(f"{kc}/admin/realms/{realm}{path}", method=method, headers=hdr,
                                 data=json.dumps(body).encode() if body is not None else None)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read() or b"null")

c = call("GET", f"/clients?clientId={client}")[0]
uris = list(c.get("redirectUris") or [])
wanted = [f"{origin}/*", f"{origin}/"]
new = [u for u in wanted if u not in uris]
if not new:
    print(f"{client}: {origin} already allowed")
    sys.exit(0)
call("PUT", f"/clients/{c['id']}", {**c, "redirectUris": uris + new})
print(f"{client}: added {', '.join(new)}")
print("redirect URIs now:", ", ".join(call("GET", f"/clients/{c['id']}")["redirectUris"]))
PY
