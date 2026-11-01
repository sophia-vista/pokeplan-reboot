#!/usr/bin/env bash
# Runs the migrations and SQL tests on a throwaway PostgreSQL. Needs PostgreSQL 15+ binaries: PGBIN if set, else the newest
# /usr/lib/postgresql/*/bin, else wherever initdb is on the PATH (the CI runner image changes which version it ships).
#   supabase/tests/run.sh
set -euo pipefail

find_pgbin() {
  local newest
  newest="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -n 1 || true)"
  if [ -n "$newest" ] && [ -x "$newest/initdb" ]; then echo "$newest"; return; fi
  if command -v initdb >/dev/null 2>&1; then dirname "$(readlink -f "$(command -v initdb)")"; return; fi
  if command -v pg_config >/dev/null 2>&1 && [ -x "$(pg_config --bindir)/initdb" ]; then pg_config --bindir; return; fi
}
PGBIN="${PGBIN:-$(find_pgbin)}"
[ -n "$PGBIN" ] && [ -x "$PGBIN/initdb" ] || { echo "PostgreSQL 15+ binaries not found: install postgresql or set PGBIN" >&2; exit 127; }
echo "using PostgreSQL binaries in $PGBIN"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="$(mktemp -d)"
PORT="${PGPORT:-54329}"
trap 'run pg_ctl -D "$WORK/data" -m immediate stop >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

# PostgreSQL refuses to run as root, so drop to the postgres user when needed.
if [ "$(id -u)" = 0 ]; then
  chown postgres "$WORK" && chmod 755 "$WORK"
  run() { local cmd="$1"; shift; runuser -u postgres -- "$PGBIN/$cmd" "$@"; }
else
  run() { local cmd="$1"; shift; "$PGBIN/$cmd" "$@"; }
fi

run initdb -D "$WORK/data" -U postgres --auth=trust >/dev/null
run pg_ctl -D "$WORK/data" -o "-p $PORT -k $WORK -c listen_addresses=''" -l "$WORK/log" -w start >/dev/null
psql_() { run psql -h "$WORK" -p "$PORT" -U postgres -v ON_ERROR_STOP=1 -q "$@"; }

psql_ -d postgres -c "create database app" >/dev/null
# Hand the SQL files to the postgres user through stdin so file permissions never matter.
apply() { psql_ -d app <"$1" >/dev/null; }

apply "$HERE/stub.sql"
apply "$HERE/helpers.sql"
for f in "$ROOT"/supabase/migrations/*.sql; do
  echo "migration: $(basename "$f")"
  apply "$f"
done

# The tests read the golden vectors and the rank thresholds, so the SQL cannot drift from the Kotlin code.
cp "$ROOT/tools/server/golden.json" "$WORK/golden.json" && chmod 644 "$WORK/golden.json"
export GOLDEN="$WORK/golden.json"
THRESHOLDS="$(sed -n 's/.*val thresholds: List<Int> = listOf(\(.*\)).*/\1/p' "$ROOT/pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/TrainerRank.kt" | tr -d ' _')"
[ -n "$THRESHOLDS" ] || { echo "could not read TrainerRank.thresholds"; exit 1; }

status=0
for f in "$HERE"/*.test.sql; do
  echo "test: $(basename "$f")"
  if ! psql_ -d app -o /dev/null -v thresholds="{$THRESHOLDS}" <"$f"; then status=1; fi
done
[ "$status" = 0 ] && echo "all SQL tests passed" || { echo "SQL tests FAILED"; exit 1; }
