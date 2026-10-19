#!/usr/bin/env bash
# Copies the compiled reward engine next to the function, ready to deploy:
#   supabase/functions/reward/build.sh && supabase functions deploy reward
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
( cd "$ROOT" && ./gradlew :pokemon-core:jsProductionLibraryCompileSync )
rm -rf "$ROOT/supabase/functions/reward/engine"
mkdir -p "$ROOT/supabase/functions/reward/engine"
cp "$ROOT"/pokemon-core/build/compileSync/js/main/productionLibrary/kotlin/*.mjs "$ROOT/supabase/functions/reward/engine/"
echo "engine copied to supabase/functions/reward/engine"
