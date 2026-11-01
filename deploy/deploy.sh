#!/usr/bin/env bash
# usage: deploy.sh <image> <smoke test command>...
# Points the Serverless Container at <image>, waits until it is ready, then runs the smoke test command against the
# site; if anything fails, points the container back at the image it ran before and fails.
# Needs SCW_SECRET_KEY, SCW_REGION and SCW_CONTAINER_ID; the smoke test runs without SCW_SECRET_KEY.
set -euo pipefail

image="$1"
shift
container="https://api.scaleway.com/containers/v1/regions/$SCW_REGION/containers/$SCW_CONTAINER_ID"
wait_seconds="${DEPLOY_WAIT_SECONDS:-600}"
poll_seconds="${DEPLOY_POLL_SECONDS:-10}"

scaleway() {
  curl -fsS --retry 3 -H "X-Auth-Token: $SCW_SECRET_KEY" -H "Content-Type: application/json" "$@"
}

read_json() {
  python3 -c 'import json, sys; d = json.load(sys.stdin); print(eval(sys.argv[1], {}, {"d": d}))' "$1"
}

point_at() {
  echo "Pointing the container at $1."
  scaleway -X PATCH "$container" -d "$(python3 -c 'import json, sys; print(json.dumps({"image": sys.argv[1]}))' "$1")" > /dev/null
  local waited=0 state
  while true; do
    state="$(scaleway "$container" | read_json 'd["status"] + " " + d["image"]')"
    case "$state" in
      "ready $1") echo "The container is ready on $1."; return 0 ;;
      error*) echo "The container is in error on $1: $(scaleway "$container" | read_json 'd.get("error_message") or ""')"; return 1 ;;
    esac
    if [ "$waited" -ge "$wait_seconds" ]; then
      echo "The container was not ready on $1 after $wait_seconds seconds (status and image: $state)."
      return 1
    fi
    sleep "$poll_seconds"
    waited=$((waited + poll_seconds))
  done
}

previous="$(scaleway "$container" | read_json 'd["image"]')"
maintenance="$(scaleway "$container" | read_json '(d.get("environment_variables") or {}).get("MAINTENANCE", "off")')"

if [ "$previous" != "$image" ] && ! point_at "$image"; then
  point_at "$previous" || echo "The rollback to $previous failed too."
  exit 1
fi

if [ "$maintenance" = on ]; then
  echo "The site is in maintenance: the smoke test is skipped."
  exit 0
fi

if env -u SCW_SECRET_KEY "$@"; then
  echo "Deployed $image."
else
  echo "The smoke test failed on $image."
  if [ "$previous" != "$image" ]; then
    point_at "$previous" && echo "Rolled back to $previous." || echo "The rollback to $previous failed too."
  fi
  exit 1
fi
