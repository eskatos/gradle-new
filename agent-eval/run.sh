#!/usr/bin/env bash
# usage: agent-eval/run.sh [origin] [-- claude plugin eval options...]
# Assembles the evaluation plugin under build/agent-eval, with the skill rendered for <origin>
# (default https://gradle-new.nosphere.org), and runs claude plugin eval on it.
# EVAL_SKILL renders another skill template, EVAL_CASES keeps only the named cases, EVAL_DIR assembles elsewhere.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
origin="https://gradle-new.nosphere.org"
if [ $# -gt 0 ] && [ "$1" != "--" ]; then
  origin="${1%/}"
  shift
fi
[ "${1:-}" = "--" ] && shift

plugin="${EVAL_DIR:-$root/build/agent-eval}"
rm -rf "$plugin"
mkdir -p "$plugin/skills/gradle-new" "$plugin/evals"
cp -R "$root/agent-eval/.claude-plugin" "$plugin/"
if [ -n "${EVAL_CASES:-}" ]; then
  for case in $EVAL_CASES; do
    cp -R "$root/agent-eval/evals/$case" "$plugin/evals/"
  done
else
  cp -R "$root/agent-eval/evals/." "$plugin/evals/"
fi
sed "s#{{origin}}#$origin#g" "${EVAL_SKILL:-$root/ui/src/agent/SKILL.md}" > "$plugin/skills/gradle-new/SKILL.md"

host="${origin#*://}"
host="${host%%[:/]*}"
exec claude plugin eval "$plugin" --trust-plugin --no-publish \
  --model "${EVAL_MODEL:-sonnet}" --judge-model "${EVAL_JUDGE_MODEL:-haiku}" \
  --max-cost-usd "${EVAL_MAX_COST_USD:-10}" \
  --allow-tools Bash "WebFetch(domain:$host)" \
  "$@"
