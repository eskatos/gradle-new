#!/usr/bin/env bash
# usage: cleanup-images.sh <keep>
# Deletes the images of this repository's container package older than the <keep> most recent, with their
# provenance attestations and GitHub Releases. With DEPLOYED_TAG set, the image with that tag and the one before it are
# kept whatever their age. With DRY_RUN=true it only lists what it would delete.
set -euo pipefail

keep="$1"
owner="${GH_REPO%%/*}"
package="${GH_REPO#*/}"
versions_url="users/$owner/packages/container/$package/versions"

versions="$(gh api --paginate "$versions_url?per_page=100" \
  --jq '.[] | [.id, .name, .created_at, ((.metadata.container.tags // []) | join(" "))] | @tsv')"
images="$(printf '%s\n' "$versions" | awk -F'\t' '$4 != "" && $4 !~ /^sha256-/' | sort -t$'\t' -k3,3r)"
protected=""
if [ -n "${DEPLOYED_TAG:-}" ]; then
  protected="$(printf '%s\n' "$images" | awk -F'\t' -v t="$DEPLOYED_TAG" '{ n = split($4, a, " "); for (i = 1; i <= n; i++) if (a[i] == t) found = NR } found && NR <= found + 1 { print $1 }' | tr '\n' ' ')"
  [ -n "$protected" ] || { echo "The deployed image $DEPLOYED_TAG is not in the package; deleting nothing."; exit 1; }
  echo "Keeping the deployed image $DEPLOYED_TAG and the one before it."
fi
old="$(printf '%s\n' "$images" | tail -n +"$((keep + 1))" | awk -F'\t' -v p="$protected" 'BEGIN { n = split(p, a, " "); for (i = 1; i <= n; i++) kept[a[i]] = 1 } !($1 in kept)')"

if [ -z "$old" ]; then
  echo "$(printf '%s\n' "$images" | grep -c . || true) images, keeping the $keep most recent: nothing to delete."
  exit 0
fi

delete_version() {
  if [ "${DRY_RUN:-false}" = true ]; then
    echo "  would delete $2"
  else
    gh api -X DELETE "$versions_url/$1" > /dev/null
    echo "  deleted $2"
  fi
}

while IFS=$'\t' read -r id digest created tags; do
  echo "$tags, pushed $created:"
  attestation="sha256-${digest#sha256:}"
  for attestation_id in $(printf '%s\n' "$versions" | awk -F'\t' -v t="$attestation" '{ n = split($4, a, " "); for (i = 1; i <= n; i++) if (a[i] == t) print $1 }'); do
    delete_version "$attestation_id" "attestation $attestation"
  done
  delete_version "$id" "image $digest"
  for tag in $tags; do
    if gh release view "$tag" > /dev/null 2>&1; then
      if [ "${DRY_RUN:-false}" = true ]; then
        echo "  would delete release $tag"
      else
        gh release delete "$tag" --cleanup-tag --yes
        echo "  deleted release $tag"
      fi
    fi
  done
done <<< "$old"
