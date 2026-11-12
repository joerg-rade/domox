#!/usr/bin/env python3
"""Report — and optionally fail on — the per-class mutation-testing floors of `domox-domain`.

Why: pitest gives one number for the whole module (~54 % killed, ~79 % test strength), and that number
hides everything that matters.  The module average was carried for a long time by the ~40
`TypedDependencyRule` classes and the CRC candidates, while the UML-layout collaborators that were
*extracted* from `UmlLayoutDiagrams` sat far below it — `RouteScoring` at 2 %, `LabelLayout` at 6 %,
`RouteGeometry` at 34 % — because the only tests that touched them pushed a whole RDF graph through
`UmlLayoutDiagrams.renderLayout` and asserted substrings of the resulting JSON.  A per-class floor
makes that visible, and keeps it visible: the extracted types are the ones a future change is most
likely to break, since nothing else asserts on their behaviour directly.

The same reasoning covers the second wave of extractions: the diagram collaborators shared by
`CandidateD3Graph` / `CandidateVegaGraph` / `DomainModelDiagramsD3` (`DiagramNodeSizing`,
`DiagramJson`, `DiagramLemmas`, `PosPalette`) and the per-vocabulary collaborators carved out of
`TypedDependencyPredicates` (`BasicAttributeVocabulary`, `BlockedVerbVocabulary`,
`NounTypeVocabulary`, `ActionVocabulary`, `GeneralizationVocabulary`, `SynonymVocabulary`).  Both
sets were pulled out of larger classes whose end-to-end tests exercised them only incidentally, so
they need their own floors for the same reason.

A third wave continued the same cleanup: `CandidateNeighbourhood` and `PosLegend` (the algorithm and
the POS-legend table that the three lexical diagram renderers each carried a private copy of), and
`CandidateFactory` + `UseCaseBlockedNouns` (the match-to-candidate mapping and the blocked-noun
predicate carved out of `RuleMatches`).  Each new class gets a floor; the renderers and
`RuleMatches` keep their own behaviour pinned by the existing end-to-end tests.

A fourth wave split four more over-loaded classes along single responsibilities: `Candidate` (the
review click-through flow → `CandidateReviewWorkflow`, and the rule-match de-duplication →
`RuleMatchSignature`), `TypedDependencyPredicates` (its three unrelated predicate families → the
`DependencyPosPredicates` / `DependencyTypePredicates` / `DependencyVocabularyPredicates` trio),
`LexicalGraphGenerator` (`DotLiterals`, `LexicalGraphNodes`, `LexicalGraphLegend`,
`LexicalGraphBorders`) and `RdfGraphDiagrams` (`RdfIriNames`, `RdfGraphIndex`, `RdfDiagramJson`,
`RdfJson`, plus the `RdfDiagramNode` / `RdfNodeKind` value types, which carry no mutants).  The
`DependencyTypePredicates` and `DependencyVocabularyPredicates` floors are low by design: they are
mostly one-line delegations to the vocab registries, so a large share of their mutants are
`NO_COVERAGE` — the floor is there to catch a *regression*, not to claim strong coverage.

The floors are set just below what the per-collaborator tests achieve, so they fail on a *regression*
(a new branch left untested, a test deleted) rather than on an unrelated refactor that moves the
denominator.

Usage:
  scripts/pitest-floors.py                            # the floor classes, exit 0 either way
  scripts/pitest-floors.py --check                    # exit 1 when any class is below its floor
  scripts/pitest-floors.py --table                    # every class in the report, floor classes marked
  scripts/pitest-floors.py --detail OrthogonalRouter  # the mutants no test killed, with line + mutator
  scripts/pitest-floors.py --report path/to/mutations.xml

`--table` and `--detail` are read-only inspection modes: they answer "what does the whole module look
like" and "what exactly goes untested in this one class", which otherwise means hand-parsing the XML.

The report comes from the opt-in mutation profile, which is **not** part of `mvn verify` (~2 min):

  mvn -o -pl domox-domain verify -Pmutation     # writes domox-domain/target/pit-reports/mutations.xml

**Run that last.**  pitest mutates the production classes but takes the *test* classes as its input,
so editing a test after the run leaves `mutations.xml` looking perfectly healthy while its numbers
describe a suite that no longer exists.  The report is therefore refused when any file under
`domox-domain/src` (or either `pom.xml`) is newer than it, and `--check` exits 3 rather than
reporting a pass.  `--allow-stale` bypasses the guard when you know better — e.g. a build cache
restored the report with a checkout-fresh mtime.

Exit codes:
  0  every floor met, and the report is current
  1  a class is below its floor
  2  no report at the given path — or, under --detail, no class matching the selector
  3  the report is stale (only under --check; --allow-stale suppresses it)

Two caveats on reading the numbers:

* A mutant whose status is `NO_COVERAGE` was never executed — a test that cannot *reach* the code.
  `SURVIVED` means it ran and nothing noticed — a test that reaches it but does not assert on it.
  Both count against the kill rate, but they point at different fixes.
* A handful of minions die with `MEMORY_ERROR` / `TIMED_OUT` on the larger classes (concentrated in
  `OrthogonalRouter`'s Hanan-grid A*); those mutants are neither killed nor survived, and they push
  the denominator up.  The floors are set with that in mind.
"""
import argparse
import os
import sys
import xml.etree.ElementTree as ET

DEFAULT_REPORT = os.path.join("domox-domain", "target", "pit-reports", "mutations.xml")

# Everything the mutation run consumes: the module's sources, and the poms that configure the run.  A
# file here that is newer than the report means the report describes an older suite — see the docstring.
SOURCE_ROOTS = (os.path.join("domox-domain", "src"), os.path.join("domox-domain", "pom.xml"), "pom.xml")
SOURCE_SUFFIXES = (".java", ".kt")

# The extracted collaborators, and the kill rate each must keep.  See the module docstring.
# Each floor sits a few points below what the per-collaborator tests achieve, so a regression trips it
# while an unrelated refactor that moves the denominator does not.
#
#   class                       achieved  floor   (achieved when the floors were set, 2026-11)
FLOORS = {
    # --- the eight UML-layout collaborators extracted from UmlLayoutDiagrams ---
    "RouteScoring": 45,       # 54 %  — the fallback chooser, unreachable from the end-to-end test
    "LabelLayout": 70,        # 78 %
    "LabelMetrics": 85,       # 92 %
    "RouteGeometry": 65,      # 73 %
    "PortLayout": 55,         # 61 %
    "OrthogonalRouter": 60,   # 67 %
    "DiagramIndex": 75,       # 83 %
    "DiagramJsonWriter": 80,  # 88 %

    # --- the diagram collaborators shared by CandidateD3Graph / CandidateVegaGraph /
    #     DomainModelDiagramsD3, and the per-vocabulary collaborators carved out of
    #     TypedDependencyPredicates ---
    # Each scored 100 % when its per-collaborator test class landed.  The floors sit one or two
    # mutants below that (e.g. 4 of 5 mutants is 80 %), so one new untested branch trips them.
    "DiagramNodeSizing": 90,          # 100 %  (11 mutants)
    "DiagramJson": 85,                # 100 %  (8)
    "DiagramLemmas": 90,              # 100 %  (15)
    "PosPalette": 80,                 # 100 %  (1)
    "BasicAttributeVocabulary": 80,   # 100 %  (5)
    "BlockedVerbVocabulary": 80,      # 100 %  (6)
    "NounTypeVocabulary": 80,         # 100 %  (5)
    "ActionVocabulary": 90,           # 100 %  (13)
    "GeneralizationVocabulary": 90,   # 100 %  (28)
    "SynonymVocabulary": 80,          # 100 %  (6)

    # --- the third wave: the shared candidate-neighbourhood algorithm and POS legend, and the
    #     candidate factory carved out of RuleMatches ---
    "CandidateNeighbourhood": 95,     # 100 %  (48 mutants)
    "PosLegend": 85,                  # 100 %  (17)
    "CandidateFactory": 95,           # 100 %  (63)
    "UseCaseBlockedNouns": 85,        # 100 %  (12)

    # --- the fourth wave: the Candidate review workflow and rule-match signature, the split of
    #     TypedDependencyPredicates, and the lexical-DOT / RDF-diagram collaborators ---
    "CandidateReviewWorkflow": 90,          # 100 %  (10 mutants)
    "RuleMatchSignature": 90,               # 100 %  (14)
    "DependencyPosPredicates": 65,          # 75 %   (12)
    "DependencyVocabularyPredicates": 45,   # 52 %   (40; 16 no-coverage)
    "DependencyTypePredicates": 20,         # 25 %   (69; 35 no-coverage — mostly delegation)
    "DotLiterals": 80,                      # 100 %  (6)
    "LexicalGraphNodes": 90,                # 98 %   (44)
    "LexicalGraphLegend": 80,               # 100 %  (3)
    "LexicalGraphBorders": 85,              # 100 %  (14)
    "RdfIriNames": 90,                      # 100 %  (36)
    "RdfGraphIndex": 90,                    # 100 %  (41)
    "RdfDiagramJson": 90,                   # 100 %  (15)
    "RdfJson": 85,                          # 100 %  (7)
}

# Statuses that count as a detected defect.
KILLED = {"KILLED"}

# Statuses that are neither killed nor survived: the minion never returned a verdict, so the mutant
# only inflates the denominator.  Reported separately so the rates can be read honestly.
NO_VERDICT = ("MEMORY_ERROR", "TIMED_OUT", "RUN_ERROR")

# Reporting order for the un-killed mutants: the two that name an actionable gap first.
MISSED_ORDER = ("SURVIVED", "NO_COVERAGE", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR", "UNKNOWN")


def parse_report(path):
    """Every mutant in a pitest XML report, as a list of dicts (class, method, line, status, mutator)."""
    mutants = []
    for m in ET.parse(path).getroot().findall("mutation"):
        fqcn = m.findtext("mutatedClass", "")
        mutants.append({
            "class": fqcn.rsplit(".", 1)[-1],
            "fqcn": fqcn,
            "method": m.findtext("mutatedMethod", ""),
            "line": m.findtext("lineNumber", ""),
            "status": m.get("status", "UNKNOWN"),
            "mutator": (m.findtext("mutator", "") or "").rsplit(".", 1)[-1],
            "description": m.findtext("description", ""),
        })
    return mutants


def counts_by_class(mutants):
    """{simple class name: {status: count}}."""
    classes = {}
    for m in mutants:
        bucket = classes.setdefault(m["class"], {})
        bucket[m["status"]] = bucket.get(m["status"], 0) + 1
    return classes


def kill_rate(counts):
    """(killed, total, percentage) for one class' status counts."""
    total = sum(counts.values())
    killed = sum(counts.get(s, 0) for s in KILLED)
    return killed, total, (100.0 * killed / total if total else 0.0)


def floor_failures(classes):
    """The floor classes below their floor, as (name, rate, floor).  A class with no mutants is not one."""
    failures = []
    for name, floor in sorted(FLOORS.items()):
        counts = classes.get(name)
        if not counts:
            continue
        _, _, rate = kill_rate(counts)
        if rate < floor:
            failures.append((name, rate, floor))
    return failures


def stale_sources(report, roots, suffixes):
    """Files newer than the report: it predates them, so its numbers describe an older test suite."""
    report_mtime = os.path.getmtime(report)
    stale = []
    for root in roots:
        if os.path.isfile(root):
            if os.path.getmtime(root) > report_mtime:
                stale.append(root)
            continue
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d != "target"]
            for filename in filenames:
                if filename.endswith(suffixes):
                    path = os.path.join(dirpath, filename)
                    if os.path.getmtime(path) > report_mtime:
                        stale.append(path)
    return sorted(stale)


def print_floor_table(classes):
    """Every floor class and whether each meets its floor.  Returns the below-floor list."""
    print(f"{'class':<20} {'killed':>7} {'total':>7} {'rate':>6}  {'floor':>5}")
    for name, floor in sorted(FLOORS.items()):
        counts = classes.get(name)
        if not counts:
            print(f"{name:<20} {'-':>7} {'0':>7} {'n/a':>6}  {floor:>5}  (no mutants)")
            continue
        killed, total, rate = kill_rate(counts)
        flag = ""
        if rate < floor:
            flag = "  <-- BELOW FLOOR"
        print(f"{name:<20} {killed:>7} {total:>7} {rate:>5.0f}%  {floor:>5}  {flag}")
    return floor_failures(classes)


def print_full_table(classes):
    """Every class in the report — total, killed, rate, no-coverage — with the floor classes marked.

    The floor table answers "did anything regress?"; this one answers "where is the module as a whole",
    which is what you need before deciding whether a floor should move.
    """
    width = max([len(n) for n in classes] + [len("class")])
    print(f"{'class':<{width}} {'killed':>7} {'total':>7} {'rate':>6} {'no-cov':>7}  {'floor':>5}")
    for name in sorted(classes):
        counts = classes[name]
        killed, total, rate = kill_rate(counts)
        marker = ""
        floor = FLOORS.get(name)
        if floor is not None:
            marker = f"  {floor:>5}" + ("  <-- BELOW FLOOR" if rate < floor else "")
        print(f"{name:<{width}} {killed:>7} {total:>7} {rate:>5.0f}% {counts.get('NO_COVERAGE', 0):>7}{marker}")

    killed = sum(sum(c.get(s, 0) for s in KILLED) for c in classes.values())
    total = sum(sum(c.values()) for c in classes.values())
    rate = 100.0 * killed / total if total else 0.0
    print(f"\nmodule: {killed}/{total} killed ({rate:.0f} %), {len(classes)} classes")
    no_verdict = {s: sum(c.get(s, 0) for c in classes.values()) for s in NO_VERDICT}
    no_verdict = {s: n for s, n in no_verdict.items() if n}
    if no_verdict:
        print("no verdict (only inflate the denominator): "
              + ", ".join(f"{s}={n}" for s, n in no_verdict.items()))
    return floor_failures(classes)


def print_detail(mutants, selector):
    """Every mutant of the selected class(es) that no test killed, with its line, method and mutator.

    `selector` is a simple class name, or any fragment of the fully-qualified one (so `--detail dom.rdf`
    covers the whole package).
    """
    selected = [m for m in mutants if m["class"] == selector]
    if not selected:
        selected = [m for m in mutants if selector in m["fqcn"]]
    if not selected:
        print(f"no class matching {selector!r} in the report", file=sys.stderr)
        return False

    for fqcn in sorted({m["fqcn"] for m in selected}):
        rows = [m for m in selected if m["fqcn"] == fqcn]
        killed = sum(1 for m in rows if m["status"] in KILLED)
        print(f"\n{fqcn.rsplit('.', 1)[-1]}  ({fqcn})  {killed}/{len(rows)} killed")
        missed = [m for m in rows if m["status"] not in KILLED]
        if not missed:
            print("  every mutant is killed")
            continue
        order = [s for s in MISSED_ORDER if any(m["status"] == s for m in missed)]
        order += sorted({m["status"] for m in missed} - set(MISSED_ORDER))
        for status in order:
            for m in sorted((m for m in missed if m["status"] == status),
                            key=lambda m: (int(m["line"] or 0), m["method"])):
                print(f"  {status:<12} L{m['line']:>4}  {m['method']:<24} "
                      f"{m['mutator']:<34} {m['description']}")
        by_method = {}
        for m in missed:
            by_method[m["method"]] = by_method.get(m["method"], 0) + 1
        top = ", ".join(f"{k}={v}" for k, v in sorted(by_method.items(), key=lambda kv: -kv[1])[:5])
        print(f"  not killed, by method: {top}")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", default=DEFAULT_REPORT,
                        help=f"pitest XML report (default: {DEFAULT_REPORT})")
    parser.add_argument("--check", action="store_true",
                        help="exit 1 when any class is below its floor, 3 when the report is stale")
    parser.add_argument("--allow-stale", action="store_true",
                        help="do not refuse a report older than domox-domain/src (see the docstring)")
    parser.add_argument("--table", action="store_true",
                        help="print every class in the report (with no-coverage and the module total) "
                             "instead of just the floor classes")
    parser.add_argument("--detail", metavar="CLASS",
                        help="print every mutant of CLASS that no test killed, with line and mutator; "
                             "CLASS is a simple name or any fragment of the fully-qualified one")
    args = parser.parse_args()

    # stdout is block-buffered when piped while stderr is not, so without this the diagnostics are
    # emitted *ahead* of the table they are about — and `tail` then hides the warning entirely.
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, ValueError):
        pass

    if not os.path.exists(args.report):
        print(f"no report at {args.report}", file=sys.stderr)
        print("run: mvn -o -pl domox-domain verify -Pmutation", file=sys.stderr)
        return 2

    mutants = parse_report(args.report)
    classes = counts_by_class(mutants)
    stale = [] if args.allow_stale else stale_sources(args.report, SOURCE_ROOTS, SOURCE_SUFFIXES)

    # --detail is an inspection mode: its output *is* the report, so the floor table would only be noise.
    # --check still governs the exit code either way.
    if args.detail:
        found = print_detail(mutants, args.detail)
        failures = floor_failures(classes)
    elif args.table:
        failures = print_full_table(classes)
    else:
        failures = print_floor_table(classes)

    if failures:
        print()
        for name, rate, floor in failures:
            print(f"{name}: {rate:.0f}% killed, floor is {floor}%", file=sys.stderr)
        if not args.check:
            print("(--check not given: reporting only)")

    if stale:
        print(f"\n!! STALE REPORT: {len(stale)} source file(s) are newer than it:", file=sys.stderr)
        for path in stale[:10]:
            print(f"     {path}", file=sys.stderr)
        if len(stale) > 10:
            print(f"     … and {len(stale) - 10} more", file=sys.stderr)
        print("   The numbers above describe an older test suite.  Re-run:", file=sys.stderr)
        print("     mvn -o -pl domox-domain verify -Pmutation", file=sys.stderr)
        print("   … or pass --allow-stale if you know the report is current "
              "(e.g. restored from a build cache).", file=sys.stderr)
        if not args.check:
            print("(--check not given: reporting only)", file=sys.stderr)

    if args.detail and not found:
        return 2
    if args.check and stale:
        return 3
    if args.check and failures:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
