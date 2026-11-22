#!/bin/bash
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"
if [ "${1:-}" = "--release" ]; then
  shift
  git commit -q --allow-empty -m "release: ${*:-chunk given back}" || exit 1
  git push -q -u origin HEAD && echo "Released." && exit 0
  echo "release: the push failed; the release commit is local only." >&2; exit 1
fi
WHAT="${*:-}"
[ -n "$WHAT" ] || { echo "usage: claim.sh \"<backlog id> <topic>\"" >&2; exit 1; }
git fetch -q --prune origin || echo "claim: could not reach origin; the check below may be out of date." >&2
ME="$(git rev-parse --abbrev-ref HEAD)"
SINCE=$(( $(date +%s) - ${IN_FLIGHT_DAYS:-3} * 86400 ))

clash=""
for ref in $(git for-each-ref --format='%(refname)' refs/remotes/origin); do
  b="${ref#refs/remotes/origin/}"
  case "$b" in HEAD|main|"$ME") continue ;; esac
  [ "$(git log -1 --format=%ct "$ref")" -ge "$SINCE" ] || continue
  [ "$(git rev-list --count "origin/main..$ref")" -gt 0 ] || continue
  [ "$(git log --no-merges -1 --format=%s "$ref" | cut -c1-8)" = "release:" ] && continue
  hay="$b $(git log --no-merges --format=%s "origin/main..$ref")"
  hits=0
  for w in $(printf '%s' "$WHAT" | tr -c 'A-Za-z0-9-' ' '); do
    [ "${#w}" -ge 4 ] || continue
    printf '%s' "$hay" | grep -qiF -- "$w" && hits=$((hits + 1))
  done
  [ "$hits" -ge 2 ] && clash+="  $b: $(git log --no-merges --reverse --format=%s "origin/main..$ref" | head -1)"$'\n'
done
if [ -n "$clash" ] && [ "${FORCE:-}" != "1" ]; then
  printf 'Not claimed. Another session already has something like "%s":\n%s' "$WHAT" "$clash" >&2
  echo "Pick a different chunk, or FORCE=1 .claude/hooks/claim.sh ... if it is really different." >&2
  exit 2
fi

git commit -q --allow-empty -m "claim: $WHAT

Marks this chunk as taken by this session so parallel sessions skip it.
Co-Authored-By: Claude <noreply@anthropic.com>" || exit 1
for wait in 0 2 4 8 16; do
  sleep "$wait"
  git push -q -u origin HEAD && { echo "Claimed: $WHAT (branch $ME)"; exit 0; }
done
echo "claim: the push failed; the claim commit is local only." >&2
exit 1
