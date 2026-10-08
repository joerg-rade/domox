#!/usr/bin/env bash
#
# Installs the DoMoX pre-commit hook: runs the FAST quick check (schema-drift guardrail),
# which skips itself when the dev DB container is not running, so commits stay fast.
# Idempotent: re-running replaces our own hook and refuses to clobber a foreign one.
set -uo pipefail
cd "$(dirname "$0")/.."   # repo root

HOOK=".git/hooks/pre-commit"
mkdir -p .git/hooks

if [ -f "$HOOK" ] && ! head -2 "$HOOK" | grep -q 'DoMoX pre-commit'; then
  echo "NOT INSTALLED: $HOOK already exists and was not written by this script."
  echo "Re-run after replacing it, or add this line manually:"
  echo "  bash scripts/check.sh --quick"
  exit 1
fi

cat > "$HOOK" <<'EOF'
#!/usr/bin/env bash
# DoMoX pre-commit (installed by scripts/install-git-hooks.sh): fast schema-drift guardrail.
# Skips itself when the 'domox-db' container is not running.
bash scripts/check.sh --quick
EOF
chmod +x "$HOOK"
echo "installed $HOOK"