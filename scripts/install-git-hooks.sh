#!/usr/bin/env bash
#
# [DEPRECATED] Pre-commit hook was removed. Schema drift, domain unit tests, and metamodel
# guardrail are still runnable on demand via:
#   bash scripts/check.sh            # full check (schema + tests + metamodel)
#   bash scripts/check.sh --quick    # drift only
#
# This script is a no-op. It exists to prevent confusion for developers who previously
# installed the hook — running it now prints a message and exits cleanly.
set -uo pipefail
cd "$(dirname "$0")/.."

HOOK=".git/hooks/pre-commit"
if [ -f "$HOOK" ] && head -2 "$HOOK" | grep -q 'DoMoX pre-commit'; then
  rm "$HOOK"
  echo "removed stale $HOOK (pre-commit hook was deprecated)"
fi
echo "Pre-commit hook is no longer used. Run 'bash scripts/check.sh' manually when needed."