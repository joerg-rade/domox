#!/usr/bin/env bash
# Install the pre-commit hook that runs `scripts/check.sh --quick` before each commit.
# Run this once per clone:  bash scripts/install-pre-commit-hook.sh
#
# Scope note — the hook is intentionally CHEAP: every instant, Maven-free step of the bundle — schema
# drift vs the dev DB, layout-XML structure, the .mjs / scripts/*.py syntax check, the pom
# dependency-alignment check, and the mermaid styling check.  The full guardrail (unit tests +
# UI/UML/ontology render + webjar + metamodel veto) runs in CI (.github/workflows/ci.yml) and on
# demand locally:
#   bash scripts/check.sh            # full check
#   bash scripts/check.sh --quick    # the five instant steps (what the hook runs)

HOOK_DIR="$(cd "$(dirname "$0")/.." && pwd)/.git/hooks"
HOOK_FILE="$HOOK_DIR/pre-commit"

mkdir -p "$HOOK_DIR"

cat > "$HOOK_FILE" << 'HOOK'
#!/usr/bin/env bash
# Pre-commit hook — cheap guardrails: schema drift + layout-XML structure + .mjs/.py syntax +
# dependency alignment + mermaid styling. Installed by scripts/install-pre-commit-hook.sh.  The full
# check (unit tests + UI render + metamodel veto) runs in CI and on demand via:  bash scripts/check.sh
cd "$(dirname "$0")/../.."   # repo root
exec bash scripts/check.sh --quick
HOOK

chmod +x "$HOOK_FILE"
echo "✅ Pre-commit hook installed at $HOOK_FILE (five instant steps; full check: bash scripts/check.sh)"
