#!/bin/bash
STATUS="$HOME/.pokeplan-setup"
[ "${CLAUDE_CODE_REMOTE:-}" != "true" ] && exit 0
for _ in $(seq 1 300); do
  [ -f "$STATUS.done" ] && exit 0
  if [ -f "$STATUS.failed" ]; then
    echo "Session setup $(cat "$STATUS.failed"); run it by hand: CLAUDE_CODE_REMOTE=true .claude/hooks/session-start.sh" >&2
    exit 1
  fi
  sleep 2
done
echo "Session setup is still running after 10 minutes." >&2
exit 1
