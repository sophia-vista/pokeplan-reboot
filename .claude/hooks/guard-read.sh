#!/bin/bash
command -v python3 >/dev/null 2>&1 || exit 0
python3 -I -c '
import json, os, sys
LIMIT = 32 * 1024
EXEMPT = ("docs/pokelife/README.md", "docs/pokelife/PRODUCT_BIBLE.md", "CLAUDE.md")
SKIP_EXT = (".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".ipynb")
try:
    d = json.load(sys.stdin)
    ti = d.get("tool_input") or {}
    p = ti.get("file_path") or ""
    if not p or any(ti.get(k) for k in ("offset", "limit", "pages", "allow_large")):
        sys.exit(0)
    if p.lower().endswith(SKIP_EXT) or not os.path.isfile(p):
        sys.exit(0)
    if any(p.endswith(e) for e in EXEMPT):
        sys.exit(0)
    size = os.path.getsize(p)
except SystemExit:
    raise
except Exception:
    sys.exit(0)
if size > LIMIT:
    sys.stderr.write(
        "Blocked: %s is %d KB (about %d tokens). Grep it for the heading or symbol you need, then Read a range "
        "with offset and limit. See CLAUDE.md, Swarm workflow and token use.\n" % (p, size // 1024, size // 4000 * 1000))
    sys.exit(2)
sys.exit(0)
'
exit $?
