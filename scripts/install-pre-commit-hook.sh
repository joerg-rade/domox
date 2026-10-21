#!/usr/bin/env bash
# Install the pre-commit hook that runs `scripts/check.sh --quick` before each commit.
# Run this once per clone:  bash scripts/install-pre-commit-hook.sh
#
# Scope note — the hook is intentionally DRIFT-ONLY (schema drift vs the dev DB) so commits
# stay fast and don't need Maven. The full guardrail (JS syntax + unit tests + metamodel veto)
# runs in CI (.github/workflows/ci.yml) and on demand locally:
#   bash scripts/check.sh            # full check (drift + JS + tests + metamodel)
#   bash scripts/check.sh --quick    # drift only (what the hook runs)

HOOK_DIR="$(cd "$(dirname "$0")/.." && pwd)/.git/hooks"
HOOK_FILE="$HOOK_DIR/pre-commit"

mkdir -p "$HOOK_DIR"

cat > "$HOOK_FILE" << 'HOOK'
#!/usr/bin/env bash
# Pre-commit hook — fast schema-drift guardrail. Installed by scripts/install-pre-commit-hook.sh.
# Drift-only by design to keep commits fast; the full guardrail (JS syntax + unit tests +
# metamodel veto) runs in CI and on demand via:  bash scripts/check.sh
cd "$(dirname "$0")/../.."   # repo root
exec bash scripts/check.sh --quick
HOOK

chmod +x "$HOOK_FILE"
echo "✅ Pre-commit hook installed at $HOOK_FILE (drift-only; full check: bash scripts/check.sh)"
