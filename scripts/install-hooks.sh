#!/usr/bin/env bash
# Install the pre-commit hook that runs `scripts/check.sh --quick` before each commit.
# Run this once per clone:  bash scripts/install-hooks.sh

HOOK_DIR="$(cd "$(dirname "$0")/.." && pwd)/.git/hooks"
HOOK_FILE="$HOOK_DIR/pre-commit"

mkdir -p "$HOOK_DIR"

cat > "$HOOK_FILE" << 'HOOK'
#!/usr/bin/env bash
# Pre-commit hook — fast schema-drift guardrail. Installed by scripts/install-hooks.sh.
cd "$(dirname "$0")/../.."   # repo root
exec bash scripts/check.sh --quick
HOOK

chmod +x "$HOOK_FILE"
echo "✅ Pre-commit hook installed at $HOOK_FILE"