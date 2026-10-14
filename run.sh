#!/bin/zsh
# Runs the Amie stack on this machine: the awsS3 service (the package store, in front of the S3 bucket), the Spring
# Boot backend (packageRepository) and the Kobweb website (app/site).
#
#   ./run.sh                     start everything that is not already running
#   ./run.sh --only backend,s3   start some of: s3, backend, site
#   ./run.sh --restart           stop what this script started, then start again
#   ./run.sh stop                stop the services (s3, backend and the Kobweb server)
#   ./run.sh status              which ports answer, with the addresses to open
#   ./run.sh logs                follow logs/*.log
#
# Order: s3 first (the backend calls it over HTTP), then the backend, then the site. Each service is waited for
# until its port answers. Logs go to logs/<service>.log, pids to .run/<service>.pid. See README.md "Running".
set -uo pipefail

repo=${0:A:h}
cd "$repo"
mkdir -p logs .run

# ---- environment ---------------------------------------------------------------------------------------------
export JAVA_HOME=${JAVA_HOME_21:-/Users/samuel/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home}
export AWS_PROFILE=${AWS_PROFILE:-default}                  # the profile awsS3 reads the bucket with
export AWS_REGION=${AWS_REGION:-eu-north-1}
export KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}  # Keycloak (Docker), used by the site server
# The realm has no fixed frontend URL, so the issuer in its tokens is the address the browser used for Keycloak:
# this machine's LAN address when the site is opened from the LAN. Set KEYCLOAK_ISSUER to pin another one.
lan_ip() { ipconfig getifaddr en0 2>/dev/null || ifconfig | awk '/inet / && $2 != "127.0.0.1" {print $2; exit}'; }
export KEYCLOAK_ISSUER=${KEYCLOAK_ISSUER:-http://$(lan_ip):8080/realms/master}
export KEYCLOAK_JWKS_URI=${KEYCLOAK_JWKS_URI:-$KEYCLOAK_URL/realms/master/protocol/openid-connect/certs}
# The site server exchanges passwords, codes and device codes for tokens with the confidential `amie` client, so it
# needs the client's secret; without it every sign-in on the site fails (Keycloak: invalid_client_credentials).
# Default: the secret the desktop app keeps in its AMP/keycloak.json (AMIE_APP_DIR names that checkout).
app_keycloak_json=${AMIE_APP_DIR:-$repo/../amieAppStable}/amieApplication/desktopApp/AMP/keycloak.json
if [[ -z ${KEYCLOAK_CLIENT_SECRET:-} && -f $app_keycloak_json ]]; then
  KEYCLOAK_CLIENT_SECRET=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("clientSecret",""))' "$app_keycloak_json" 2>/dev/null)
fi
export KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET:-}
export AMIE_S3_PORT=${AMIE_S3_PORT:-8088}
export AMIE_SITE_PORT=${AMIE_SITE_PORT:-8081}
export AMIE_S3_SERVICE_URL=${AMIE_S3_SERVICE_URL:-http://localhost:$AMIE_S3_PORT}
export AMIE_S3_PUBLIC_URL=${AMIE_S3_PUBLIC_URL:-http://localhost:$AMIE_S3_PORT}
export AMIE_SITE_URL=${AMIE_SITE_URL:-http://localhost:$AMIE_SITE_PORT}
unset GITHUB_TOKEN                                          # the bucket is the only package store

listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1; }
is_keycloak() { curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:$1/realms/master" 2>/dev/null | grep -q '^200$'; }

# The backend listens on 8080 unless Keycloak already holds that port on this machine; then it takes 8090.
if [[ -z ${AMIE_BACKEND_PORT:-} ]]; then
  if [[ -n $(listening 8080) ]] && is_keycloak 8080; then AMIE_BACKEND_PORT=8090; else AMIE_BACKEND_PORT=8080; fi
fi
export AMIE_BACKEND_PORT
export AMIE_BACKEND_URL=${AMIE_BACKEND_URL:-http://localhost:$AMIE_BACKEND_PORT}

# ---- arguments -----------------------------------------------------------------------------------------------
command=start; only="s3,backend,site"; restart=0
while (( $# )); do
  case $1 in
    start|stop|status|logs) command=$1 ;;
    --only) only=$2; shift ;;
    --only=*) only=${1#--only=} ;;
    --restart) restart=1 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "run.sh: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done
wants() { [[ ",$only," == *",$1,"* ]]; }

# ---- helpers -------------------------------------------------------------------------------------------------
port_of() { case $1 in s3) echo $AMIE_S3_PORT ;; backend) echo $AMIE_BACKEND_PORT ;; site) echo $AMIE_SITE_PORT ;; esac; }
url_of() { case $1 in s3) echo "$AMIE_S3_SERVICE_URL/api/aws/jars" ;; backend) echo "$AMIE_BACKEND_URL/packages" ;; site) echo "$AMIE_SITE_URL/" ;; esac; }

wait_for() {  # wait_for <service> <seconds>
  local port; port=$(port_of "$1")
  for ((i = 0; i < $2; i++)); do
    [[ -n $(listening "$port") ]] && { echo "  $1 answers on port $port"; return 0; }
    if [[ -f .run/$1.pid ]] && ! kill -0 "$(cat .run/$1.pid)" 2>/dev/null; then
      echo "  $1 exited before listening; see logs/$1.log:" >&2; tail -20 "logs/$1.log" >&2; return 1
    fi
    sleep 1
  done
  echo "  $1 did not open port $port within $2 s; see logs/$1.log" >&2; return 1
}

start_one() {  # start_one <service> <dir> <gradle task...>
  local name=$1 dir=$2; shift 2
  local port; port=$(port_of "$name")
  if [[ -n $(listening "$port") ]]; then
    echo "  $name already running on port $port (use --restart or 'stop' first)"; return 0
  fi
  echo "  starting $name on port $port ($dir: $*) -> logs/$name.log"
  (cd "$dir" && nohup ./gradlew "$@" > "$repo/logs/$name.log" 2>&1 &; echo $! > "$repo/.run/$name.pid")
}

stop_one() {
  local name=$1 port; port=$(port_of "$name")
  if [[ $name == site ]]; then (cd app && ./gradlew :site:kobwebStop -q > /dev/null 2>&1); fi
  if [[ -f .run/$name.pid ]]; then kill "$(cat .run/$name.pid)" 2>/dev/null; rm -f .run/$name.pid; fi
  for ((i = 0; i < 10; i++)); do [[ -z $(listening "$port") ]] && break; sleep 1; done
  local pid; pid=$(listening "$port")
  if [[ -n $pid ]]; then kill "$pid" 2>/dev/null; sleep 2; pid=$(listening "$port"); [[ -n $pid ]] && kill -9 "$pid" 2>/dev/null; fi
  echo "  $name stopped (port $port free)"
}

check_prerequisites() {
  local ok=1
  [[ -x $JAVA_HOME/bin/java ]] || { echo "run.sh: no JDK at JAVA_HOME=$JAVA_HOME (set JAVA_HOME_21)" >&2; ok=0; }
  if wants s3 && [[ -z ${AWS_ACCESS_KEY_ID:-} ]] && ! grep -qs "^\[$AWS_PROFILE\]" ~/.aws/credentials ~/.aws/config; then
    echo "run.sh: no AWS credentials: set AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or put profile [$AWS_PROFILE] in ~/.aws/credentials" >&2; ok=0
  fi
  if wants backend && [[ -z $(listening 5432) ]]; then
    echo "run.sh: nothing listens on 5432: start Postgres (the backend's users database) first" >&2; ok=0
  fi
  if ! is_keycloak "${KEYCLOAK_URL##*:}"; then
    echo "  warning: Keycloak does not answer at $KEYCLOAK_URL; sign-in will not work until it does"
  fi
  if wants site && [[ -z $KEYCLOAK_CLIENT_SECRET ]]; then
    echo "  warning: KEYCLOAK_CLIENT_SECRET is empty (no $app_keycloak_json): password, QR and browser sign-in on the site will fail"
  fi
  (( ok ))
}

print_status() {
  echo "Amie services:"
  local s port pid
  for s in s3 backend site; do
    port=$(port_of "$s"); pid=$(listening "$port")
    if [[ -n $pid ]]; then printf '  %-8s up    port %-5s pid %-7s %s\n' "$s" "$port" "$pid" "$(url_of "$s")"
    else printf '  %-8s down  port %s\n' "$s" "$port"; fi
  done
  echo "Keycloak: $KEYCLOAK_URL $(is_keycloak "${KEYCLOAK_URL##*:}" && echo up || echo down)"
  local lan; lan=$(lan_ip)
  [[ -n $lan ]] && echo "From the LAN (this address changes with DHCP): http://$lan:$AMIE_SITE_PORT/  backend http://$lan:$AMIE_BACKEND_PORT  s3 http://$lan:$AMIE_S3_PORT"
  echo "Client secret for the site's sign-in: $([[ -n $KEYCLOAK_CLIENT_SECRET ]] && echo set || echo MISSING)"
  echo "Token issuer the backend accepts: $KEYCLOAK_ISSUER (after an address change run scripts/keycloak-allow-origin.sh and restart the backend)"
}

# ---- commands ------------------------------------------------------------------------------------------------
case $command in
  status) print_status ;;
  logs) tail -n 20 -F logs/*.log ;;
  stop)
    for s in site backend s3; do wants $s && stop_one $s; done ;;
  start)
    check_prerequisites || exit 1
    if (( restart )); then for s in site backend s3; do wants $s && stop_one $s; done; fi
    echo "Starting Amie (backend port $AMIE_BACKEND_PORT, s3 service $AMIE_S3_SERVICE_URL, site $AMIE_SITE_URL)"
    if wants s3; then
      start_one s3 awsS3 bootRun && wait_for s3 180 || exit 1
    fi
    if wants backend; then
      start_one backend packageRepository bootRun -Dorg.gradle.jvmargs=-Xmx2g && wait_for backend 240 || exit 1
    fi
    if wants site; then
      start_one site app :site:kobwebStart && wait_for site 300 || exit 1
    fi
    echo
    print_status
    if [[ $AMIE_BACKEND_PORT != 8080 ]]; then
      echo "Note: the backend is on $AMIE_BACKEND_PORT. The site's /api proxies know; for direct browser calls run"
      echo "  localStorage.setItem(\"amie.backendUrl\", \"http://localhost:$AMIE_BACKEND_PORT\") in the console, and point"
      echo "  the desktop app at it (AMIE_BACKEND_URL or baseUrl in its AMP/backend.json)."
    fi ;;
esac
