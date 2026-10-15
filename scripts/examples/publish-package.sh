#!/usr/bin/env bash
# Publishes a package jar to the Amie store with curl and handles every answer the backend can give.
#
#   AMIE_USER=sam AMIE_PASSWORD=secret scripts/examples/publish-package.sh echo-package.jar
#   AMIE_TOKEN=<keycloak access token> scripts/examples/publish-package.sh echo-package.jar   # already signed in
#
# Environment: AMIE_BACKEND_URL (default http://localhost:8090), AMIE_USER / AMIE_PASSWORD (a backend account,
# registered with POST /register; the Keycloak way is in amie_client.py) or AMIE_TOKEN, AMIE_LANG (default jar).
# Exit codes: 0 published, 1 the backend refused (the reason is printed), 2 wrong usage, 3 the store is down.
set -uo pipefail

BACKEND="${AMIE_BACKEND_URL:-http://localhost:8090}"
LANG_FOLDER="${AMIE_LANG:-jar}"
file="${1:-}"
[[ -n "$file" && -f "$file" ]] || { echo "usage: $0 <package.jar>" >&2; exit 2; }
name="$(basename "$file")"

# ---- one helper for every call: body in a temp file, status in a variable ----------------------------
body="$(mktemp)"; trap 'rm -f "$body"' EXIT
call() { # call METHOD URL [curl args...] -> prints nothing, sets $status, body in $body
  status="$(curl -s -o "$body" -w '%{http_code}' -X "$1" "$2" "${@:3}")" || { status=000; }
}
# The backend answers thrown errors as application/problem+json: {"status","title","detail","code",...}.
# Returned refusals (a bad jar) are plain text. This prints whichever it got.
reason() {
  if grep -q '"detail"' "$body" 2>/dev/null; then
    sed -n 's/.*"detail":"\([^"]*\)".*/\1/p' "$body"
  else
    tr -d '\n' < "$body" | cut -c1-300
  fi
}
explain() { # explain STATUS -> what the status means for this call, and the exit code to use
  case "$1" in
    000) echo "the backend at $BACKEND is not answering (is ./run.sh running?)"; return 3 ;;
    400) echo "the backend refused the request: $(reason)"; return 1 ;;
    401) echo "not signed in, or the token expired: $(reason)"; return 1 ;;
    403) echo "the token belongs to someone else: $(reason)"; return 1 ;;
    404) echo "not found: $(reason)"; return 1 ;;
    409) echo "already there: $(reason)"; return 1 ;;
    413) echo "the file is over the upload limit (100 MB): $(reason)"; return 1 ;;
    502|503) echo "the package store (the S3 service) is down or refused: $(reason). Try again later."; return 3 ;;
    5*) echo "the backend failed: $(reason)"; return 3 ;;
    *) echo "unexpected answer $1: $(reason)"; return 3 ;;
  esac
}
fail() { local message; message="$(explain "$status")"; local code=$?; echo "FAIL ($status): $message" >&2; exit "$code"; }

# ---- 1. a token ---------------------------------------------------------------------------------------
if [[ -n "${AMIE_TOKEN:-}" ]]; then
  token="$AMIE_TOKEN"
  call GET "$BACKEND/me" -H "Authorization: Bearer $token"
  [[ "$status" == 200 ]] || fail
  user="$(sed -n 's/.*"username":"\([^"]*\)".*/\1/p' "$body")"
  echo "signed in as $user (from the token)"
else
  user="${AMIE_USER:-}"; password="${AMIE_PASSWORD:-}"
  [[ -n "$user" && -n "$password" ]] || { echo "set AMIE_TOKEN, or AMIE_USER and AMIE_PASSWORD" >&2; exit 2; }
  call POST "$BACKEND/login" -H 'Content-Type: application/json' -d "{\"username\":\"$user\",\"password\":\"$password\"}"
  if [[ "$status" == 401 ]]; then
    # Maybe the account does not exist yet: register it, then sign in again
    call POST "$BACKEND/register" -H 'Content-Type: application/json' -d "{\"username\":\"$user\",\"password\":\"$password\"}"
    case "$status" in
      200) echo "registered $user" ;;
      409) echo "FAIL (409): $user exists and the password is wrong: $(reason)" >&2; exit 1 ;;
      *) fail ;;
    esac
    call POST "$BACKEND/login" -H 'Content-Type: application/json' -d "{\"username\":\"$user\",\"password\":\"$password\"}"
  fi
  [[ "$status" == 200 ]] || fail
  token="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$body")"
  echo "signed in as $user"
fi

# ---- 2. publish ---------------------------------------------------------------------------------------
call POST "$BACKEND/upload" -H "Authorization: Bearer $token" \
  -F "file=@$file;type=application/java-archive" -F "username=$user" -F "progLanguage=$LANG_FOLDER"
case "$status" in
  200) echo "published $name: $(cat "$body")" ;;
  400) echo "FAIL (400): the jar was refused: $(reason)" >&2
       echo "       a package needs a public class with run(String): List and close(); see scripts/examples/make-package-jar.sh" >&2; exit 1 ;;
  *) fail ;;
esac

# ---- 3. check it is listed and readable -------------------------------------------------------------
call GET "$BACKEND/user-packages?username=$user"
[[ "$status" == 200 ]] || fail
grep -q "\"name\":\"$name\"" "$body" && echo "listed under uploads/$user/$LANG_FOLDER/$name" || echo "warning: not in the listing yet (the store caches listings for a minute)"

call GET "$BACKEND/package?path=uploads/$user/$LANG_FOLDER/$name"
case "$status" in
  200) echo "readable: $(sed -n 's/.*"size":\([0-9]*\).*/\1/p' "$body") bytes, ETag $(grep -o '"sha":"[^,]*' "$body" | head -1 | sed 's/^"sha":"//; s/\\"//g; s/"$//')" ;;
  *) fail ;;
esac
echo "done: view it at ${AMIE_SITE_URL:-http://localhost:8081}/view?package=uploads/$user/$LANG_FOLDER/$name"
