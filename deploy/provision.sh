#!/usr/bin/env bash
# usage: deploy/provision.sh                         create what is missing, then print what goes into GitHub
#        deploy/provision.sh --maintenance on|off    switch the site's maintenance page on or off
#        deploy/provision.sh --private|--public      stop the container for good (Scaleway's 403) or open it again
# Runs on a workstation with the scw CLI initialised on the gradle-new project (scw init). Compatible with bash 3.2.
set -euo pipefail

name=gradle-new
ci_name=gradle-new-ci
hostname=gradle-new.nosphere.org
region=fr-par
repository=eskatos/gradle-new
memory=0.128GB
mvcpu=70

project="$(scw config get default-project-id)"
organization="$(scw config get default-organization-id)"
if [ -z "$project" ] || [ -z "$organization" ]; then
  echo "The scw CLI has no default project or organisation: run scw init first."
  exit 1
fi

namespace_id() {
  scw container namespace list name="$name" project-id="$project" region="$region" -o template='{{ .ID }}' | head -n 1
}

container_id() {
  scw container container list name="$name" namespace-id="$1" region="$region" -o template='{{ .ID }}' | head -n 1
}

container_field() {
  scw container container get "$1" region="$region" -o template="{{ .$2 }}"
}

wait_until_ready() {
  local status
  for _ in $(seq 1 60); do
    status="$("$@")"
    case "$status" in
      ready) return 0 ;;
      error) echo "Error status."; return 1 ;;
    esac
    sleep 10
  done
  echo "Still $status after 10 minutes."
  return 1
}

existing_container() {
  local namespace container
  namespace="$(namespace_id)"
  [ -n "$namespace" ] && container="$(container_id "$namespace")"
  if [ -z "${container:-}" ]; then
    echo "No $name container yet: run deploy/provision.sh without options first." >&2
    exit 1
  fi
  echo "$container"
}

case "${1:-}" in
  --maintenance)
    case "${2:-}" in
      on|off) ;;
      *) echo "usage: deploy/provision.sh --maintenance on|off"; exit 2 ;;
    esac
    container="$(existing_container)"
    scw container container update "$container" environment-variables.MAINTENANCE="$2" region="$region" > /dev/null
    wait_until_ready container_field "$container" Status
    echo "Maintenance mode is $2 on https://$hostname."
    exit 0 ;;
  --private|--public)
    container="$(existing_container)"
    scw container container update "$container" privacy="${1#--}" region="$region" > /dev/null
    wait_until_ready container_field "$container" Status
    echo "The container is ${1#--}."
    exit 0 ;;
  "") ;;
  *)
    echo "usage: deploy/provision.sh [--maintenance on|off | --private | --public]"
    exit 2 ;;
esac

namespace="$(namespace_id)"
if [ -z "$namespace" ]; then
  echo "Creating the namespace $name in $region."
  namespace="$(scw container namespace create name="$name" project-id="$project" region="$region" -o template='{{ .ID }}')"
  wait_until_ready scw container namespace get "$namespace" region="$region" -o template='{{ .Status }}'
fi

container="$(container_id "$namespace")"
if [ -z "$container" ]; then
  tag="$(curl -fsS "https://api.github.com/repos/$repository/releases/latest" | grep -o '"tag_name": *"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/')"
  image="ghcr.io/$repository:$tag"
  echo "Creating the container $name on $image: $memory, $mvcpu mvCPU, from 0 to 1 instance."
  container="$(scw container container create namespace-id="$namespace" name="$name" image="$image" port=8080 \
    min-scale=0 max-scale=1 memory-limit-bytes="$memory" mvcpu-limit="$mvcpu" privacy=public region="$region" \
    -o template='{{ .ID }}')"
fi
wait_until_ready container_field "$container" Status
endpoint="$(container_field "$container" PublicEndpoint)"
endpoint="${endpoint#https://}"
endpoint="${endpoint%/}"
echo "The container answers on https://$endpoint."

application="$(scw iam application list name="$ci_name" organization-id="$organization" -o template='{{ .ID }}' | head -n 1)"
if [ -z "$application" ]; then
  echo "Creating the IAM application $ci_name and its policy."
  application="$(scw iam application create name="$ci_name" organization-id="$organization" \
    description="gradle-new deployments from GitHub Actions" -o template='{{ .ID }}')"
  scw iam policy create name="$ci_name" application-id="$application" organization-id="$organization" \
    description="Serverless Containers in the gradle-new project, and billing read-only for the watchdog" \
    rules.0.permission-set-names.0=ContainersFullAccess rules.0.project-ids.0="$project" \
    rules.1.permission-set-names.0=BillingReadOnly rules.1.organization-id="$organization" > /dev/null
fi
if [ -z "$(scw iam api-key list bearer-id="$application" organization-id="$organization" -o template='{{ .AccessKey }}' | head -n 1)" ]; then
  echo "Creating the API key of $ci_name. Its secret key is shown only now: copy it into GitHub."
  scw iam api-key create application-id="$application" default-project-id="$project" description="GitHub Actions, environment production" \
    -o template='SCW_SECRET_KEY = {{ .SecretKey }}'
fi

if [ "$(scw container domain list container-id="$container" region="$region" -o template='{{ .Hostname }}' | head -n 1)" != "$hostname" ]; then
  echo
  echo "Add this record at Gandi (domain nosphere.org, LiveDNS), then press Enter:"
  echo "  gradle-new  CNAME  $endpoint."
  caa="$(dig +short CAA nosphere.org)"
  if [ -n "$caa" ] && ! printf '%s' "$caa" | grep -q 'letsencrypt.org'; then
    echo "nosphere.org has CAA records that do not allow Let's Encrypt, which Scaleway uses; also add:"
    echo "  @           CAA    0 issue \"letsencrypt.org\""
  fi
  read -r _
  until [ "$(dig +short CNAME "$hostname" | sed 's/\.$//')" = "$endpoint" ]; do
    echo "$hostname does not point at $endpoint yet; checking again in 30 seconds."
    sleep 30
  done
  echo "Adding the custom domain $hostname; Scaleway now requests its certificate."
  domain="$(scw container domain create container-id="$container" hostname="$hostname" region="$region" -o template='{{ .ID }}')"
  wait_until_ready scw container domain get "$domain" region="$region" -o template='{{ .Status }}'
fi

echo "Redirecting HTTP to HTTPS."
scw container container update "$container" https-connection-only=true region="$region" > /dev/null
wait_until_ready container_field "$container" Status

echo
echo "In GitHub, Settings > Environments > production:"
echo "  secret   SCW_SECRET_KEY       the secret key printed above, when the key was created"
echo "  variable SCW_REGION           $region"
echo "  variable SCW_ORGANIZATION_ID  $organization"
echo "  variable SCW_PROJECT_ID       $project"
echo "  variable SCW_CONTAINER_ID     $container"
echo "  variable SITE_URL             https://$hostname"
