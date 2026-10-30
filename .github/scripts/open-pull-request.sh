#!/usr/bin/env bash
# usage: open-pull-request.sh <branch> <title> <auto-merge: true|false> <body> <path>...
# Proposes the working tree changes of <path>... on top of origin/main as <branch> and its pull request,
# or closes that pull request when there is no change. Leaves the working tree at origin/main.
set -euo pipefail

branch="$1"
title="$2"
auto_merge="$3"
body="$4"
shift 4

base="$(git rev-parse origin/main)"
open_pull_request="$(gh pr list --head "$branch" --state open --json number --jq '.[0].number // empty')"

git add --all -- "$@"
if git diff --cached --quiet "$base"; then
  git reset --quiet --hard "$base"
  if [ -n "$open_pull_request" ]; then
    gh pr close "$open_pull_request" --delete-branch --comment "Main already has this change, or it is no longer needed."
  fi
  echo "$branch: nothing to propose."
  exit 0
fi

date="$(git log -1 --format=%cI "$base")"
commit="$(
  GIT_AUTHOR_NAME='github-actions[bot]' GIT_AUTHOR_EMAIL='41898282+github-actions[bot]@users.noreply.github.com' GIT_AUTHOR_DATE="$date" \
  GIT_COMMITTER_NAME='github-actions[bot]' GIT_COMMITTER_EMAIL='41898282+github-actions[bot]@users.noreply.github.com' GIT_COMMITTER_DATE="$date" \
  git commit-tree "$(git write-tree)" -p "$base" -m "$title"
)"
git reset --quiet --hard "$base"

if [ "$(git ls-remote origin "refs/heads/$branch" | cut -f1)" = "$commit" ]; then
  echo "$branch: already up to date at $commit."
else
  git push --force origin "$commit:refs/heads/$branch"
fi

if [ -z "$open_pull_request" ]; then
  gh pr create --base main --head "$branch" --title "$title" --body "$body"
else
  gh pr edit "$open_pull_request" --title "$title"
fi
if [ "$auto_merge" = true ] && [ "$(gh pr view "$branch" --json autoMergeRequest --jq '.autoMergeRequest != null')" != true ]; then
  gh api graphql \
    -f query='mutation($id: ID!) { enablePullRequestAutoMerge(input: {pullRequestId: $id, mergeMethod: SQUASH}) { clientMutationId } }' \
    -f id="$(gh pr view "$branch" --json id --jq .id)"
fi
