#!/usr/bin/env bash
# usage: watchdog.sh
# Hourly guard of the deployment, run with the CI's Scaleway key:
# - configuration: the container keeps the size and scale that keep it within the free tier; drift is put back and
#   fails the run, and another container in the project fails the run;
# - spending: anything billed this month switches the site to maintenance and fails the run; if the bill keeps
#   growing in maintenance, the container goes private and the run fails;
# - uptime: while the site is up, the page, the offered versions and one build must answer.
# Needs SCW_SECRET_KEY, SCW_REGION, SCW_ORGANIZATION_ID, SCW_PROJECT_ID, SCW_CONTAINER_ID and SITE_URL.
# PRETEND_CONSUMPTION, a number of euros, replaces the month's consumption, for drills.
set -euo pipefail

api="https://api.scaleway.com"
container_url="$api/containers/v1/regions/$SCW_REGION/containers/$SCW_CONTAINER_ID"
failed=0

scaleway() {
  curl -fsS --retry 3 -H "X-Auth-Token: $SCW_SECRET_KEY" -H "Content-Type: application/json" "$@"
}

read_json() {
  python3 -c 'import json, sys; d = json.load(sys.stdin); print(eval(sys.argv[1], {}, {"d": d}))' "$1"
}

container="$(scaleway "$container_url")"

repair="$(printf '%s' "$container" | python3 -c '
import json, sys
c = json.load(sys.stdin)
limits = {"min_scale": 0, "max_scale": 1, "memory_limit_bytes": 128000000, "mvcpu_limit": 70}
print(json.dumps({key: limit for key, limit in limits.items() if int(c.get(key) or 0) > limit}))')"
if [ "$repair" != "{}" ]; then
  echo "The container left its free-tier configuration; putting back $repair."
  scaleway -X PATCH "$container_url" -d "$repair" > /dev/null
  failed=1
fi

others="$(scaleway "$api/containers/v1/regions/$SCW_REGION/containers?project_id=$SCW_PROJECT_ID&page_size=100" \
  | python3 -c 'import json, sys; print(" ".join(c["name"] + " (" + c["id"] + ")" for c in json.load(sys.stdin)["containers"] if c["id"] != sys.argv[1]))' "$SCW_CONTAINER_ID")"
if [ -n "$others" ]; then
  echo "Other containers in the project share the free tier: $others."
  failed=1
fi

if [ -n "${PRETEND_CONSUMPTION:-}" ]; then
  consumption="$PRETEND_CONSUMPTION"
  echo "Pretending this month's consumption is $consumption euros."
else
  consumption=0
  page=1
  while true; do
    response="$(scaleway "$api/billing/v2beta1/consumptions?organization_id=$SCW_ORGANIZATION_ID&page=$page&page_size=100")"
    consumption="$(printf '%s' "$response" | python3 -c '
import json, sys
total = float(sys.argv[1]) + sum(int((c.get("value") or {}).get("units") or 0) + int((c.get("value") or {}).get("nanos") or 0) / 1e9 for c in json.load(sys.stdin)["consumptions"])
print(f"{total:.4f}")' "$consumption")"
    [ "$(printf '%s' "$response" | read_json 'len(d["consumptions"])')" -lt 100 ] && break
    page=$((page + 1))
  done
fi

maintenance="$(printf '%s' "$container" | read_json '(d.get("environment_variables") or {}).get("MAINTENANCE", "off")')"
seen="$(printf '%s' "$container" | read_json '(d.get("environment_variables") or {}).get("WATCHDOG_CONSUMPTION", "")')"
privacy="$(printf '%s' "$container" | read_json 'd["privacy"]')"
above() { python3 -c 'import sys; sys.exit(0 if float(sys.argv[1]) > float(sys.argv[2]) else 1)' "$1" "$2"; }

if above "$consumption" 0; then
  if [ "$maintenance" != on ]; then
    echo "Scaleway bills $consumption euros this month: switching the site to maintenance."
    environment="$(printf '%s' "$container" | python3 -c '
import json, sys
variables = dict(json.load(sys.stdin).get("environment_variables") or {})
variables.update(MAINTENANCE="on", WATCHDOG_CONSUMPTION=sys.argv[1])
print(json.dumps({"environment_variables": variables}))' "$consumption")"
    scaleway -X PATCH "$container_url" -d "$environment" > /dev/null
    maintenance=on
    failed=1
  elif [ "$privacy" != private ] && [ -n "$seen" ] && above "$consumption" "$seen"; then
    echo "The bill grew from $seen to $consumption euros in maintenance: switching the container to private."
    scaleway -X PATCH "$container_url" -d '{"privacy": "private"}' > /dev/null
    privacy=private
    failed=1
  else
    echo "Scaleway bills $consumption euros this month; the site is already down ($maintenance maintenance, $privacy)."
  fi
fi

if [ "$maintenance" = on ] || [ "$privacy" = private ]; then
  echo "The site is down on purpose (maintenance $maintenance, $privacy): no uptime check."
else
  for path in "/" "/api/versions" "/api/generate?type=basic&project-name=uptime"; do
    if ! curl -fsS --retry 2 --max-time 60 -o /dev/null "$SITE_URL$path"; then
      echo "The site does not answer $SITE_URL$path."
      failed=1
    fi
  done
fi

exit "$failed"
