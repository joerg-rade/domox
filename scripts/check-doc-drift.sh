#!/usr/bin/env bash
#
# Doc-drift guardrail: a symbol this change RETIRED from a file, still described by the steering docs.
#
# Why: `.clinerules/*.md` (top level) is loaded on EVERY turn, so a line describing deleted code is worse
# than a missing line — it steers the next session towards code that is not there.  Verified: commit
# 5b19a2e deleted `DiagramPort#buildPlantUmlDiagram`, `DiagramBuilder#buildPlantUmlDiagram`,
# `UmlClassDiagram#getPlantUml` and its `@PdfJsViewer Blob` property, and updated
# `.clinerules/d3-diagram.md` — but left `.clinerules/commands.md` describing "the `plantUml` member …
# and the Kroki-rendered PDF".  The stale line survived a whole commit and was found only by luck, when
# the next session happened to read that region of the file.
#
# The heuristic, and why each step is there:
#   1. the diff under test — default `git diff -U0 HEAD` (working tree + index: what a pre-commit hook
#      sees); `--range A..B` for a commit or a range;
#   2. keep the removed lines that look like a DECLARATION (`private`/`class`/`fun`/`val`/…).  A javadoc
#      continuation line has no keyword, and that is what keeps this quiet: on 5b19a2e the removed javadoc
#      contributed `PdfJsConfig.initialScale`, `PdfJsViewerPanel.css`, `.pdfPanel`, `multiLine` — five
#      false positives and no true positive.  (`-U0` carries no context lines, so "this line is inside a
#      comment block" cannot be detected any other way.)
#   3. keep the identifiers of >= 6 characters that have an uppercase letter after position 0 —
#      `plantUml` and `buildPlantUmlDiagram` qualify; prose (`diagram`, `responsible`) and single-word
#      class names (`Diagram`) cannot;
#   4. report a name only where a doc line names it AND the file it was removed from — the class it
#      belonged to (`UmlClassDiagram.java` → `UmlClassDiagram`).  That co-occurrence is the signal, and
#      it is what makes the check usable: without it, every name a diff happens to remove from one file
#      is reported wherever any doc mentions it (`AssociationType` removed from a test file is reported
#      against 20 doc lines that are all still true).  A global "absent from src/" test is the other
#      extreme and too strong — `plantUml` survived as a local variable in another file, so the real
#      case slipped through it (verified: with that test the historical commit reported nothing).
#   5. …and not where the line says the thing is gone (`gone`, `removed`, `deleted`, `retired`,
#      `no longer`, `any more`, `used to`, …) — which is how a doc that names a retired symbol ON
#      PURPOSE passes.  Verified: d3-diagram.md's "`DiagramPort#buildPlantUmlDiagram` … are all gone"
#      is not reported, while commands.md's stale "`plantUml` member … Kroki-rendered PDF" is.
#
# Limits, on purpose: a retired name shorter than 6 characters, or with no internal capital (`diagram`,
# `PDF`), is not checked — grepping for those drowns the check.  A doc that describes a retired member
# WITHOUT naming its class is not reported; nor is a symbol moved between files.  Both are why this is a
# hint to look, not a proof of correctness.
#
# It is a LOCAL guardrail (pre-commit hook, `check.sh`), not a CI one: it needs history to diff against,
# and CI checks out shallow — there the diff is empty and the check skips, like the schema-drift check.
#
# Usage:  bash scripts/check-doc-drift.sh [--range A..B]
# Exit:   0 clean (or nothing to check), 1 drift found, 2 usage error.
set -uo pipefail
cd "$(dirname "$0")/.."

RANGE="HEAD"
while [ $# -gt 0 ]; do
  case "$1" in
    --range) RANGE="$2"; shift 2 ;;
    -h|--help) sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "    [SKIP] not a git work tree — nothing to diff"
  exit 0
fi

DIFF="$(git diff -U0 "$RANGE" -- '*.java' '*.kt' '*.xml' '*.properties' '*.mjs' 2>/dev/null)" || {
  echo "    [SKIP] cannot diff '$RANGE' (unknown revision?)"
  exit 0
}
if [ -z "$DIFF" ]; then
  echo "    [SKIP] no code change in '$RANGE'"
  exit 0
fi

# "file<TAB>removed line", one pair per removed line, then the declaration filter.  A deleted file has
# `+++ /dev/null`, so its path is taken from the `--- a/…` line that precedes it — otherwise its removed
# lines would be attributed to the previous file, and the owner name in the report would be wrong.
# The declaration filter keeps only a MEMBER or TYPE declaration: a visibility keyword
# (`public`/`private`/`protected`) or a Kotlin/Java type or property
# (`class`/`interface`/`enum`/`record`/`fun`/`val`/`var`/`def`).  A local statement
# (`final String plantUml = …(domainModel, …)`) is deliberately excluded — its `domainModel` is a
# parameter name that still exists and that the docs legitimately use, and it was the loudest false
# positive of the first version of this check.
DECL='(^|[^A-Za-z0-9_])(class|interface|enum|record|public|protected|private|fun|val|var|def)[^A-Za-z0-9_]'
PAIRS="$(printf '%s\n' "$DIFF" | awk '
    /^--- a\//         { old = substr($0, 7); next }
    /^\+\+\+ b\//      { f = substr($0, 7); next }
    /^\+\+\+ \/dev\/null/ { f = old; next }
    /^-/               { if (f != "") print f "\t" substr($0, 2) }' \
  | grep -E "$DECL" || true)"
if [ -z "$PAIRS" ]; then
  echo "    [SKIP] no removed line looks like a declaration — nothing to check"
  exit 0
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
: > "$TMP"
while IFS=$'\t' read -r file line; do
  [ -n "$file" ] || continue
  # The name the line DECLARES — not every identifier on it.  Everything from the parameter list, the
  # initialiser, the body or the terminator is cut off, and what remains yields the declared name: after
  # the keyword for a type/Kotlin declaration (`class UmlClassDiagram …`, `fun buildPlantUmlDiagram(`,
  # `val plantUml =`), the last identifier otherwise (`private String plantUml`, `public Blob getDiagram`).
  # This is what keeps a *parameter* out of the candidate set: on `public PumlCode downloadPlantUml(…,
  # String subDomainName)` the declared name is `downloadPlantUml`, which still exists — not
  # `subDomainName`, which is a live parameter the docs rightly name.
  head="${line%%(*}"; head="${head%%;*}"; head="${head%%=*}"; head="${head%%\{*}"
  if printf '%s' "$head" | grep -qE '(^|[^A-Za-z0-9_])(class|interface|enum|record|fun|val|var|def)[[:space:]]'; then
    name="$(printf '%s' "$head" \
      | sed -E 's/.*(^|[^A-Za-z0-9_])(class|interface|enum|record|fun|val|var|def)[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\3/')"
  else
    name="$(printf '%s' "$head" | grep -oE '[A-Za-z_][A-Za-z0-9_]*' | tail -1)"
  fi
  # symbol-shaped: >= 6 characters, and an uppercase letter after position 0
  case "$name" in ??????*) ;; *) continue ;; esac
  printf '%s' "$name" | grep -qE '^.[^A-Z]*[A-Z]' || continue
  # retired = the file it was declared in no longer contains the name at all
  if [ -f "$file" ] && grep -qwF -- "$name" "$file"; then continue; fi
  printf '%s\t%s\n' "$name" "$file" >> "$TMP"
done <<< "$PAIRS"

CANDIDATES="$(sort -u "$TMP")"
if [ -z "$CANDIDATES" ]; then
  echo "    [OK]   no declared member or type was retired by this change"
  exit 0
fi

# A doc line that names a retired symbol in order to say it is gone is not drift.
GONE='gone|removed|removes|deleted|retired|no longer|any more|used to|not a property|was dropped'
HITS=0
REPORT=""
while IFS=$'\t' read -r name file; do
  [ -n "$name" ] || continue
  # The class the name belonged to: the file's stem, without the extension or a layout suffix.
  OWNER="$(basename "$file" | cut -d. -f1)"
  FOUND="$(grep -rnwF --include='*.md' -e "$name" .clinerules docs 2>/dev/null \
    | grep -Fw -- "$OWNER" | grep -viE "$GONE" || true)"
  if [ -n "$FOUND" ]; then
    HITS=$((HITS + 1))
    REPORT="$REPORT
  '$name' — retired from $file, but these doc lines still name it next to its class '$OWNER':
$(printf '%s\n' "$FOUND" | sed 's/^/    /')"
  fi
done <<< "$CANDIDATES"

if [ "$HITS" -gt 0 ]; then
  echo "    [FAIL] $HITS retired symbol(s) still described in .clinerules/ or docs/:"
  printf '%s\n' "$REPORT"
  echo "    Fix the doc line — or add the word that says the thing is gone.  A steering doc is loaded"
  echo "    on every turn, so a stale line is a wrong instruction, not a cosmetic defect."
  exit 1
fi

echo "    [OK]   $(printf '%s\n' "$CANDIDATES" | grep -c . || true) retired symbol(s) — none is still described next to the class it left"
exit 0
