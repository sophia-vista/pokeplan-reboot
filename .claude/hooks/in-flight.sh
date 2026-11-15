#!/bin/bash
# Parallel sessions: lists the work other sessions have pushed and not merged yet, so "continue with the next chunk" never
# picks a chunk somebody else already has. Runs at session start (its output goes into the session) and by hand.
#
#   .claude/hooks/in-flight.sh            list branches with unmerged work (last 3 days; IN_FLIGHT_DAYS=14 for more), newest first
#   .claude/hooks/in-flight.sh link       the same, only those that mention "link" in a branch name, commit subject or path
#
# A branch is "in flight" when it has commits that origin/main does not. The first commit a session pushes is its claim
# ("claim: <backlog id> <topic>", see claim.sh), so the list reads as who is doing what. Needs only git, no GitHub access.
cd "${CLAUDE_PROJECT_DIR:-$(pwd)}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0
git fetch -q --prune origin 2>/dev/null || { echo "in-flight: could not reach origin, so this list may be out of date." >&2; }
git rev-parse -q --verify origin/main >/dev/null || exit 0

TOPIC="${1:-}"
ME="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
DAYS="${IN_FLIGHT_DAYS:-3}"
SINCE=$(( $(date +%s) - DAYS * 86400 ))
SHOWN=0
out=""
while read -r ref; do
  b="${ref#refs/remotes/origin/}"
  case "$b" in HEAD|main) continue ;; esac
  ts=$(git log -1 --format=%ct "$ref")
  [ "$ts" -ge "$SINCE" ] || continue
  ahead=$(git rev-list --count "origin/main..$ref")
  [ "$ahead" -gt 0 ] || continue
  # Only what the branch itself added (its merges of main do not count as work).
  [ "$(git log --no-merges -1 --format=%s "$ref" | cut -c1-8)" = "release:" ] && continue
  subjects=$(git log --no-merges --reverse --format=%s "origin/main..$ref")
  [ -n "$subjects" ] || continue
  first=$(printf '%s\n' "$subjects" | head -1)
  areas=$(git diff --name-only "$(git merge-base origin/main "$ref")" "$ref" 2>/dev/null | awk -F/ 'NF>1{print $1}' | sort | uniq -c | sort -rn | head -4 | awk '{printf "%s ", $2}')
  hay="$b $subjects $areas"
  if [ -n "$TOPIC" ] && ! printf '%s' "$hay" | grep -qi -- "$TOPIC"; then continue; fi
  [ "$SHOWN" -lt 5 ] || continue
  SHOWN=$((SHOWN + 1))
  ver=$(git show "$ref:VERSION" 2>/dev/null | head -1)
  age=$(( ( $(date +%s) - ts ) / 3600 ))
  [ "$b" = "$ME" ] && mark=" (this session)" || mark=""
  out+="  $b$mark - $ahead commit(s), last push ${age}h ago
      first: $first
      touches: ${areas:-nothing yet}${ver:+; VERSION $ver}
"
done < <(git for-each-ref --sort=-committerdate --format='%(refname)' refs/remotes/origin)

echo "In flight (pushed by other sessions, not on main yet):"
if [ -n "$out" ]; then printf '%s' "$out"; else echo "  nothing${TOPIC:+ matching \"$TOPIC\"}."; fi
echo "Latest on main (VERSION $(git show origin/main:VERSION 2>/dev/null | head -1)):"
git log origin/main --first-parent --format='  %h %s' -4
echo "Before picking a chunk: skip anything above that covers it, then run .claude/hooks/claim.sh \"<backlog id> <topic>\" as your first action."
echo "Two branches with the same VERSION will collide on merge: bump to the next free number after fetching main, just before the PR."
