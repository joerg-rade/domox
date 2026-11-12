#!/usr/bin/env python3
"""Report — and optionally fail on — the per-class mutation-testing floors of `domox-domain`.

Why: pitest gives one number for the whole module (~39 % killed, ~71 % test strength), and that number
hides everything that matters.  The module average was carried for a long time by the ~40
`TypedDependencyRule` classes and the CRC candidates, while the UML-layout collaborators that were
*extracted* from `UmlLayoutDiagrams` sat far below it — `RouteScoring` at 2 %, `LabelLayout` at 6 %,
`RouteGeometry` at 34 % — because the only tests that touched them pushed a whole RDF graph through
`UmlLayoutDiagrams.renderLayout` and asserted substrings of the resulting JSON.  A per-class floor
makes that visible, and keeps it visible: the extracted types are the ones a future change is most
likely to break, since nothing else asserts on their behaviour directly.

The floors are set just below what the per-collaborator tests achieve, so they fail on a *regression*
(a new branch left untested, a test deleted) rather than on an unrelated refactor that moves the
denominator.

Usage:
  scripts/pitest-floors.py                      # report the table, exit 0 either way
  scripts/pitest-floors.py --check              # exit 1 when any class is below its floor
  scripts/pitest-floors.py --report path/to/mutations.xml

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
  2  no report at the given path
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

# The extracted UML-layout collaborators, and the kill rate each must keep.  See the module docstring.
# Each floor sits a few points below what the per-collaborator tests achieve, so a regression trips it
# while an unrelated refactor that moves the denominator does not.
#
#   class                achieved  floor   (achieved when the floors were set, 2026-11)
FLOORS = {
    "RouteScoring": 45,       # 54 %  — the fallback chooser, unreachable from the end-to-end test
    "LabelLayout": 70,        # 78 %
    "LabelMetrics": 85,       # 92 %
    "RouteGeometry": 65,      # 73 %
    "PortLayout": 55,         # 61 %
    "OrthogonalRouter": 60,   # 67 %
    "DiagramIndex": 75,       # 83 %
    "DiagramJsonWriter": 80,  # 88 %
}

# Statuses that count as a detected defect.
KILLED = {"KILLED"}


def read_report(path):
    """Return {simple class name: Counter of status -> count} from a pitest XML report."""
    root = ET.parse(path).getroot()
    classes = {}
    for mutation in root.findall("mutation"):
        name = mutation.findtext("mutatedClass", "")
        status = mutation.get("status", "UNKNOWN")
        classes.setdefault(name.rsplit(".", 1)[-1], {})
        bucket = classes[name.rsplit(".", 1)[-1]]
        bucket[status] = bucket.get(status, 0) + 1
    return classes


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


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", default=DEFAULT_REPORT,
                        help=f"pitest XML report (default: {DEFAULT_REPORT})")
    parser.add_argument("--check", action="store_true",
                        help="exit 1 when any class is below its floor, 3 when the report is stale")
    parser.add_argument("--allow-stale", action="store_true",
                        help="do not refuse a report older than domox-domain/src (see the docstring)")
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

    classes = read_report(args.report)
    stale = [] if args.allow_stale else stale_sources(args.report, SOURCE_ROOTS, SOURCE_SUFFIXES)
    failures = []
    print(f"{'class':<20} {'killed':>7} {'total':>7} {'rate':>6}  {'floor':>5}")
    for name, floor in sorted(FLOORS.items()):
        counts = classes.get(name)
        if not counts:
            print(f"{name:<20} {'-':>7} {'0':>7} {'n/a':>6}  {floor:>5}  (no mutants)")
            continue
        total = sum(counts.values())
        killed = sum(counts.get(s, 0) for s in KILLED)
        rate = 100.0 * killed / total if total else 0.0
        flag = ""
        if rate < floor:
            flag = "  <-- BELOW FLOOR"
            failures.append((name, rate, floor))
        print(f"{name:<20} {killed:>7} {total:>7} {rate:>5.0f}%  {floor:>5}  {flag}")

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
        if not args.check:
            print("(--check not given: reporting only)", file=sys.stderr)

    if args.check and stale:
        return 3
    if args.check and failures:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
