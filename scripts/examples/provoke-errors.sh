#!/usr/bin/env bash
# Provokes every error the backend and the S3 service answer, on purpose, and prints what comes back:
# a live catalogue of the statuses, codes and messages a client has to handle (see ERRORS.md).
#
#   scripts/examples/provoke-errors.sh                 # against http://localhost:8090 and :8088
#   AMIE_TOKEN=<token> scripts/examples/provoke-errors.sh   # also the cases that need a signed-in user
#   AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/provoke-errors.sh   # signs in to a backend account
#
# Nothing is left behind: the jar it publishes is removed again, the state it saves is deleted.
set -uo pipefail

BACKEND="${AMIE_BACKEND_URL:-http://localhost:8090}"
S3="${AMIE_S3_SERVICE_URL:-http://localhost:8088}"
body="$(mktemp)"; jar="$(mktemp -d)"; trap 'rm -rf "$body" "$jar"' EXIT

call() { status="$(curl -sS -o "$body" -w '%{http_code}' -X "$1" "$2" "${@:3}" 2>/dev/null)" || status=000; }
show() { # show "what" -> one line: status, the problem's code when there is one, and the detail or text
  local code detail
  code="$(sed -n 's/.*"code":"\([^"]*\)".*/\1/p' "$body")"
  detail="$(sed -n 's/.*"detail":"\([^"]*\)".*/\1/p' "$body")"
  [[ -n "$detail" ]] || detail="$(tr -d '\n' < "$body" | cut -c1-120)"
  printf '%-58s %s %-22s %s\n' "$1" "$status" "${code:--}" "$detail"
}
hr() { printf '\n== %s\n' "$1"; }

[[ "$(curl -s -o /dev/null -w '%{http_code}' "$BACKEND/packages")" != 000 ]] || { echo "the backend at $BACKEND is not answering; start it with ./run.sh" >&2; exit 3; }

hr "Requests the backend refuses before touching anything (no token needed)"
call GET "$BACKEND/package?path=../etc/passwd";                                   show "GET /package outside uploads/"
call GET "$BACKEND/package";                                                      show "GET /package without ?path"
call GET "$BACKEND/package?path=uploads/nobody/jar/missing.jar";                  show "GET /package of a missing file"
call GET "$BACKEND/profile?username=no-such-user-$RANDOM";                        show "GET /profile of an unknown user"
call POST "$BACKEND/register" -H 'Content-Type: application/json' -d '{"username":"x"}'; show "POST /register without a password"
call POST "$BACKEND/register" -H 'Content-Type: application/json' -d '{bad json';      show "POST /register with broken JSON"
call POST "$BACKEND/login" -H 'Content-Type: application/json' -d '{"username":"nobody","password":"wrong"}'; show "POST /login with a wrong password"
call GET "$BACKEND/state";                                                        show "GET /state without a token"
call GET "$BACKEND/state" -H 'Authorization: Bearer not.a.token';                 show "GET /state with a bad token"
call DELETE "$BACKEND/package?path=uploads/sam/jar/x.jar";                        show "DELETE /package without a token"
call GET "$BACKEND/upload";                                                       show "GET on a POST-only route"
call POST "$BACKEND/device-status?username=sam" -H 'Content-Type: text/plain' -d 'x'; show "POST with an unsupported Content-Type"
call POST "$BACKEND/device-repository-controller?action=UPSERT&username=sam" -H 'Content-Type: application/json' -d '{}'; show "unknown enum value for a parameter"

hr "The S3 service's own refusals (the backend hides most of these behind a 502)"
if [[ "$(curl -s -o /dev/null -w '%{http_code}' "$S3/api/aws/files/root")" == 000 ]]; then
  echo "the S3 service at $S3 is not answering; skipped"
else
  call GET "$S3/api/aws/files/content?key=../secret";                               show "GET files/content outside uploads/"
  call GET "$S3/api/aws/files/content?key=uploads/nobody/none.txt";                show "GET files/content of a missing object"
  call GET "$S3/api/aws/files/meta";                                               show "GET files/meta without ?key"
  call GET "$S3/api/aws/downloadAmie?keyName=no-such-object-$RANDOM";              show "GET downloadAmie of a missing key"
  call POST "$S3/api/aws/upload-data?username=&fileName=x" -H 'Content-Type: application/octet-stream' -d 'x'; show "POST upload-data without a username"
  call GET "$S3/api/aws/jars/bad%20name.jar";                                       show "GET jars/<bad name>"
fi

# ---- signed in ----------------------------------------------------------------------------------------
token="${AMIE_TOKEN:-}"
if [[ -z "$token" && -n "${AMIE_USER:-}" && -n "${AMIE_PASSWORD:-}" ]]; then
  call POST "$BACKEND/login" -H 'Content-Type: application/json' -d "{\"username\":\"$AMIE_USER\",\"password\":\"$AMIE_PASSWORD\"}"
  [[ "$status" == 200 ]] || { call POST "$BACKEND/register" -H 'Content-Type: application/json' -d "{\"username\":\"$AMIE_USER\",\"password\":\"$AMIE_PASSWORD\"}";
    call POST "$BACKEND/login" -H 'Content-Type: application/json' -d "{\"username\":\"$AMIE_USER\",\"password\":\"$AMIE_PASSWORD\"}"; }
  token="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$body")"
fi
if [[ -z "$token" ]]; then
  echo; echo "set AMIE_TOKEN or AMIE_USER/AMIE_PASSWORD to see the refusals a signed-in user can get"; exit 0
fi
auth=(-H "Authorization: Bearer $token")
call GET "$BACKEND/me" "${auth[@]}"
[[ "$status" == 200 ]] || { show "GET /me with the given token"; exit 1; }
user="$(sed -n 's/.*"username":"\([^"]*\)".*/\1/p' "$body")"

hr "Refusals a signed-in user ($user) can get"
printf 'PK\003\004 not really a jar' > "$jar/fake.jar"
call POST "$BACKEND/upload" "${auth[@]}" -F "file=@$jar/fake.jar" -F "username=$user" -F "progLanguage=jar";   show "POST /upload with a file that is not a jar"
JAVA_HOME="${JAVA_HOME:-/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home}"
if [[ -x "$JAVA_HOME/bin/javac" ]]; then
  "$(dirname "$0")/make-package-jar.sh" --broken "$jar/noclose.jar" NoClose >/dev/null 2>&1
  call POST "$BACKEND/upload" "${auth[@]}" -F "file=@$jar/noclose.jar" -F "username=$user" -F "progLanguage=jar"; show "POST /upload with a jar that has no close()"
  "$(dirname "$0")/make-package-jar.sh" "$jar/good.jar" Provoke >/dev/null 2>&1
  call POST "$BACKEND/upload" "${auth[@]}" -F "file=@$jar/good.jar" -F "username=$user" -F "progLanguage=jar";  show "POST /upload with a valid package (the one success here)"
  call DELETE "$BACKEND/package?path=uploads/$user/jar/good.jar" "${auth[@]}";                                  show "DELETE /package of that jar (204 = gone)"
  call DELETE "$BACKEND/package?path=uploads/$user/jar/good.jar" "${auth[@]}";                                  show "DELETE /package of it again"
fi
call POST "$BACKEND/upload" "${auth[@]}" -F "file=@$jar/fake.jar" -F "username=someone-else" -F "progLanguage=jar"; show "POST /upload in another user's name"
call DELETE "$BACKEND/package?path=uploads/someone-else/jar/x.jar" "${auth[@]}";                                 show "DELETE /package of another user's file"
call PUT "$BACKEND/state/bad%20name" "${auth[@]}" -H 'Content-Type: application/json' -d '{}';                    show "PUT /state with a bad name"
call PUT "$BACKEND/state/probe" "${auth[@]}" -H 'Content-Type: application/json' -d '{not json';                 show "PUT /state with invalid JSON"
head -c 1100000 /dev/zero | tr '\0' 'x' | sed 's/^/"/; s/$/"/' > "$jar/big.json"
call PUT "$BACKEND/state/probe" "${auth[@]}" -H 'Content-Type: application/json' --data-binary "@$jar/big.json"; show "PUT /state over 1 MiB"
call GET "$BACKEND/state/never-saved-$RANDOM" "${auth[@]}";                                                      show "GET /state/<unknown>"
call DELETE "$BACKEND/state/never-saved-$RANDOM" "${auth[@]}";                                                   show "DELETE /state/<unknown>"
call POST "$BACKEND/errors" "${auth[@]}" -H 'Content-Type: application/json' -d '{"error":"","message":""}';      show "POST /errors with nothing in it"
call GET "$BACKEND/errors?username=someone-else" "${auth[@]}";                                                   show "GET /errors of another user"
call POST "$BACKEND/profile?username=someone-else" "${auth[@]}" -H 'Content-Type: application/json' -d '{"fullName":"x","email":"","bio":""}'; show "POST /profile of another user"
echo
echo "every line above is one of the cases in ERRORS.md; a 502 anywhere means the S3 service was down"
