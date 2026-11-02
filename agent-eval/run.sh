#!/usr/bin/env bash
# usage: agent-eval/run.sh [origin] [-- claude plugin eval options...]
# Assembles the evaluation plugin under build/agent-eval, with the skill rendered for <origin>
# (default https://gradle-new.nosphere.org), and runs claude plugin eval on it.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
origin="https://gradle-new.nosphere.org"
if [ $# -gt 0 ] && [ "$1" != "--" ]; then
  origin="${1%/}"
  shift
fi
[ "${1:-}" = "--" ] && shift

plugin="$root/build/agent-eval"
rm -rf "$plugin"
mkdir -p "$plugin/skills/gradle-new"
cp -R "$root/agent-eval/.claude-plugin" "$root/agent-eval/evals" "$plugin/"
sed "s#{{origin}}#$origin#g" "$root/ui/src/agent/SKILL.md" > "$plugin/skills/gradle-new/SKILL.md"

host="${origin#*://}"
host="${host%%[:/]*}"
exec claude plugin eval "$plugin" --trust-plugin --no-publish \
  --model "${EVAL_MODEL:-sonnet}" --judge-model "${EVAL_JUDGE_MODEL:-haiku}" \
  --max-cost-usd "${EVAL_MAX_COST_USD:-10}" \
  --allow-tools Bash "WebFetch(domain:$host)" \
  "$@"
