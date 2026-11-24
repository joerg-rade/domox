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

The same applies to the candidate context-evidence rules added with the "Context Sentences" tab:
`ContextHighlight` (the highlighting half of `ContextMatching`, kept pure) and `ContextSentence` (the
row the tab renders — a view model, because the highlight belongs to the (candidate, sentence) pair,
one sentence being evidence for several candidates).  Both scored 100 % when their test classes
landed; the floors sit one mutant below.

A fifth wave split `ContextGraphs`, which was misnamed: it returns no graph — no nodes, no links — only
the sentences a candidate's name occurs in and the use cases its rule-match evidence comes from.  It
is now two classes, one per axis of change: `ContextEvidence` keeps the repositories (the lemma
look-up behind the candidate's *Context Sentences* tab), and `UseCases` is pure (it walks the
rule-match → sentence → document chain already loaded with the candidate, and touches no repository at
all).  `ContextEvidence` is an orchestrator like `Documents`, so it carries no floor; `UseCases` is an
extracted collaborator, so it does.

A sixth wave continued the same cleanup in the diagram and layout code.  The three lexical diagram
renderers each carried their own copy of the ~60 lines that assemble a candidate neighbourhood — the
breadth-first expansion, the artifact-compound removal, the node set, the display names, the sub-domain
names, the click-through ids and the approval flags — and two of the three had already drifted apart
(`CandidateDiagrams` did not use the null-safe snapshot helper the other two did).  That assembly is now
`CandidateGraphModel`, whose `snapshot` is the single place that collects the candidate set from the four
repositories.  `OrthogonalRouter` similarly owned two things at once: the A* search over the Hanan grid,
and the arithmetic of where a box's attachment points are — the four edges, their midpoints, and the
inward normal a generalization arrives along.  The latter is now `PortGeometry`, which is what `PortLayout`
(a port chooser that never routes anything) depends on instead of the router.  Both new types are pure and
floored; `DocumentLexicalDiagrams`, the diagram half split out of `Documents`, keeps a `DiagramPort` and so
joins `Documents` and `ContextEvidence` as an orchestrator with no floor.

The most recent wave came out of the MCP *reason* feature — every MCP write tool now has to state why.
The rule behind the free-text `rationale` both `Candidate` and `SubDomain` record is `RationaleText`
(trim, blank→null, clamp to 2000), extracted so the two setters cannot drift apart; it is floored.  The
wave also closed a gap this file had not seen before: `SubDomains` was at **0 %** (44 mutants, none
covered) because the only thing touching it was a *webapp* integration test, which pitest — configured
for `domox-domain` only — cannot see at all.  A floor would not have caught that either, since the class
was never floored; what caught it was reading `--table` after a change to the class.  `SubDomainsTest`
now covers it directly, and `Reviews`/`Candidate`, the other two classes the feature touched, were
already covered (their new `recordReason` / `setRationale` code is 100 % killed; their remaining
survivors are long-standing).

Usage:
  scripts/pitest-floors.py                            # the floor classes, exit 0 either way
  scripts/pitest-floors.py --check                    # exit 1 when any class is below its floor
  scripts/pitest-floors.py --table                    # every class in the report, floor classes marked
  scripts/pitest-floors.py --detail OrthogonalRouter  # the mutants no test killed, with line + mutator
  scripts/pitest-floors.py --report path/to/mutations.xml

`--table` and `--detail` are read-only inspection modes: they answer "what does the whole module look
like" and "what exactly goes untested in this one class", which otherwise means hand-parsing the XML.

Every mode also prints an advisory NOTE for a class that is at least `UNFLOORED_MIN_MUTANTS` (20)
mutants large, kills at least `UNFLOORED_MIN_RATE` (95 %) of them, and is in neither `FLOORS` nor
`NO_FLOOR`.  It never changes the exit code: the floors are per-collaborator decisions, and the note
exists because a *forgotten* floor is otherwise invisible — `--check` gates only the names it is
given, so a newly extracted collaborator with 40 mutants and no entry passes silently.  Both bounds
are needed to keep the note actionable: without the kill-rate bound it lists every entity, service and
Drools rule class in the module, and a warning that always fires is not read.

The report comes from the opt-in mutation profile, which is **not** part of `mvn verify` (~2 min):

  mvn -o -pl domox-domain verify -Pmutation     # writes domox-domain/target/pit-reports/mutations.xml

**The profile is the last thing you run.**  pitest mutates the production classes but takes the *test*
classes as its input, so editing a test after the run leaves `mutations.xml` looking perfectly healthy
while its numbers describe a suite that no longer exists.  "Last" is an interval, not a moment: treat
`domox-domain/src` (and both `pom.xml`s) as frozen from the instant you launch the run until the floors
report, because the run takes minutes and the natural thing to do while polling it is to tidy up a file
— the one edit that is otherwise invisible.  The report is refused when any file there is newer than the
run's *start* (`check-mutation.sh` stamps it — see `RUN_START_STAMP`), and `--check` exits 3 rather than
reporting a pass.  `--allow-stale` bypasses the guard when you know better — e.g. a build cache
restored the report with a checkout-fresh mtime.

Before a run whose *point* is to close a coverage gap, save the current table first:
`python3 scripts/pitest-floors.py --table > /tmp/before.txt`.  pitest overwrites `mutations.xml`, so
without it the "before" numbers are gone and the improvement cannot be shown after the fact.

Exit codes:
  0  every floor met, and the report is current
  1  a class is below its floor
  2  no report at the given path — or, under --detail, no class matching the selector
  3  the report is stale (only under --check; --allow-stale suppresses it)

Three caveats on reading the numbers:

* pitest is configured for `domox-domain` alone, so a class whose only test lives in `domox-webapp` (an
  integration test) reads `0 %` here however well tested it is.  That is this module's *normal* shape,
  not an anomaly: ~19 % of the mutants (751 of 4037) sit in classes no domain test reaches, the `TDR*`
  Drools rules among them.  Read the module rate against that, and check the no-coverage column for any
  class you changed — a class at 0 % with 44 mutants is gated by no floor at all, because a floor only
  speaks about the names it is given.
* A mutant whose status is `NO_COVERAGE` was never executed — a test that cannot *reach* the code.
  `SURVIVED` means it ran and nothing noticed — a test that reaches it but does not assert on it.
  Both count against the kill rate, but they point at different fixes.  A `NO_COVERAGE` mutant on a
  defensive null-guard is usually a third case: the branch is *unreachable*, so the fix is to delete it
  (or move the guard out to the boundary where the value enters), not to write a test whose only job is
  to reach dead code.  `SubDomains.recordReason`'s guard went that way — both callers already
  dereferenced, so no test could ever have covered it.
* A handful of minions die with `MEMORY_ERROR` / `TIMED_OUT` on the larger classes (concentrated in
  `OrthogonalRouter`'s Hanan-grid A*); those mutants are neither killed nor survived, and they push
  the denominator up.  The floors are set with that in mind.
"""
import argparse
import os
import sys
import xml.etree.ElementTree as ET

DEFAULT_REPORT = os.path.join("domox-domain", "target", "pit-reports", "mutations.xml")

# The instant the run that produced the report started, written by check-mutation.sh immediately before
# Maven and removed once it has checked the floors.  It is what makes the staleness guard cover the run
# *itself*: the report's own mtime is when the run ended, so a source edited while pitest was mutating
# is older than it — and the guard passed on a report whose inputs had already changed.  A stamp left
# behind by a killed run is older than the next report, so it can only ever make the guard report stale
# when it might not be, never the other way round.
RUN_START_STAMP = os.path.join("domox-domain", "target", "pit-reports", ".run-start")

# Everything the mutation run consumes: the module's sources, and the poms that configure the run.  A
# file here that is newer than the report's *reference time* means the report describes an older suite —
# see the docstring and report_reference_time().
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

    # --- the force flavour of the same diagram: it seeds each box and lets the client's simulation move
    #     them, so its geometry is only ever the *starting* picture — which is exactly why the seed grid,
    #     the box sizing, the inheritance layering and the legend need pinning by a test rather than by
    #     the render guardrails.  UmlDiagramLayoutTest scores 99 %: the one survivor is an *equivalent*
    #     mutant (the relaxation loop's `round < size` bound, where one extra round settles to exactly
    #     the same depths) — see `--detail UmlDiagramLayout`. ---
    "UmlDiagramLayout": 90,   # 99 %  (83 mutants; the 1 survivor is equivalent)

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

    # --- the candidate context evidence: the highlighting rule and the row the candidate's
    #     "Context Sentences" tab renders.  Both are pure — the row is a view model built from the
    #     match, not an entity — so their tests assert directly and both score 100 %. ---
    "ContextHighlight": 90,                 # 100 %  (20 mutants)
    "ContextSentence": 80,                  # 100 %  (5)

    # --- the fifth wave: the two halves of the former ContextGraphs, split along the axis each
    #     changes on.  ContextEvidence keeps the repositories (an orchestrator, like Documents, so
    #     no floor); UseCases is pure — the extracted collaborator — and so is floored. ---
    "UseCases": 90,                         # 100 %  (19 mutants)

    # --- the sixth wave: the shared candidate-graph assembler — the neighbourhood, node set and
    #     per-lemma decoration that CandidateD3Graph, CandidateVegaGraph and DomainModelDiagramsD3
    #     each carried a copy of — and the port/edge vocabulary carved out of OrthogonalRouter.
    #     DocumentLexicalDiagrams (the diagram half split out of Documents) keeps a DiagramPort and
    #     so is an orchestrator, like Documents and ContextEvidence: no floor. ---
    "CandidateGraphModel": 90,              # 100 %  (20 mutants)
    "PortGeometry": 75,                     # 84 %   (67; every mutant reached, 11 unasserted)

    # --- the UML gap-fix wave: the three pure collaborators carved out of CandidateFactory /
    #     CollaboratorCdd (which is floored at 95 % and must not grow untestable branches), and the
    #     metadata-noun predicate.  Each is handed its inputs and returns a value — no Spring, no
    #     database — so each has a test class that asserts the mapping directly and scores 100 %. ---
    "MultiplicityEvidence": 85,             # 100 %  (per MultiplicityEvidenceTest)
    "AssociationLabel": 85,                 # 100 %  (per AssociationLabelTest)
    "ResponsibilityParameterExtraction": 85,  # 100 %  (per ResponsibilityParameterExtractionTest)
    "ResponsibilityOwnerExtraction": 85,      # 100 %  (per ResponsibilityOwnerExtractionTest)
    # The raw determiner/number token → UML multiplicity translation.  A `final` utility with no
    # injected collaborator (so it is not one of the three NO_FLOOR orchestrators), 44 mutants at
    # 95 %.  It was the first class the unfloored-class note below caught, which is exactly what
    # that note is for: the test class existed, the floor did not.
    "MultiplicityNotation": 90,             # 95 %   (44 mutants)

    # --- the MCP "reason" wave: the one rule behind the free-text rationale both Candidate and
    #     SubDomain record.  It was extracted so the two setters cannot drift apart on length or on
    #     what a blank means, and its own test class pins all three behaviours (trim, blank→null,
    #     clamp) — so it is floored.  85 rather than 100 because the last mutant is *equivalent*:
    #     `length() > MAX` mutated to `>=` differs only at exactly MAX, where
    #     `substring(0, length())` returns the same string (JDK 9+ returns `this`), so no assertion
    #     can distinguish it.  Verified 2026-11: 6/7, the survivor being that one boundary mutant. ---
    "RationaleText": 85,                    # 86 %   (7 mutants, 1 of them equivalent)
    # The service half of that same wave, and the class the `--table` read caught at 0 % (44 mutants,
    # none covered — the only thing touching it was a *webapp* integration test, invisible to pitest).
    # SubDomainsTest now covers it directly; the floor guards the idempotence the sub-domain pass
    # relies on (a re-run must not create a second sub-domain).  95 rather than 98 because the last
    # mutant is equivalent: `assignAll(null, …)` returning `Collections.emptyList()` instead of the
    # freshly built, still-empty `ArrayList` differs only in mutability, and no assertion on the
    # returned *value* can see it.
    "SubDomains": 95,                       # 98 %   (42 mutants, 1 of them equivalent)

    # --- the review-aware-export wave: the one predicate behind "what is in the assembled model?".
    #     Both the export (generate.ClassDiagram) and the empty-class sweep read it, so its two
    #     answers must stay complements — a floor is what keeps a later refactor from quietly
    #     diverging them.  95 rather than 97 because the count moves with every branch added to the
    #     predicate, and the point of the floor is the invariant, not the last two mutants. ---
    "ModelContent": 95,                     # 97 %   (29 mutants)

    # --- the PlantUML download: the class-diagram export's *text* half.  `downloadPlantUml` is where
    #     the diagram kind is read (a blank kind is the whole model), the file is named for the diagram
    #     and the review-aware source is assembled — three behaviours DomainModelsTest pins directly.
    #     90 rather than 95 because the rest of the class is orchestrator glue (listAll, create, the
    #     empty-class sweep) whose mutants move with the delegating calls, not with this action. ---
    "DomainModels": 90,                     # 95 %   (21 mutants)
}

# Classes that deliberately carry NO floor, so the unfloored-class warning below does not nag about
# them.  Each is an orchestrator: it holds the repositories/ports and delegates, so its kill rate is
# not a statement about its own logic.  Keep this list to *deliberate* decisions — the point of the
# warning is that a new extracted collaborator is otherwise invisible to --check, because a class with
# 40 mutants and no entry simply is not gated by anything.
NO_FLOOR = {
    "Documents": "orchestrator — holds the repositories, delegates the work",
    "ContextEvidence": "orchestrator — the lemma look-up behind the Context Sentences tab",
    "DocumentLexicalDiagrams": "orchestrator — the diagram half of Documents, keeps a DiagramPort",
    "CandidateResolver": "@DomainService orchestrator — injected AttributeCandidates / BasicAttributeCatalog",
    "CorpusIngestion": "orchestrator — RepositoryService, NlpPort and five domain services",
}

# A class this well covered, and this large, with no floor entry is worth a warning.  Both bounds
# matter: the mutant count keeps small helpers out, and the kill rate keeps out the classes that are
# *deliberately* unfloored — an entity, a service, a Drools rule class or a renderer scores near zero
# and is not the thing this warns about.  A class a test class kills almost completely is exactly the
# "collaborator extracted, test written, floor forgotten" case: every wave-2/3/5 collaborator scored
# 100 %, and their floors sit a mutant or two below that.
UNFLOORED_MIN_MUTANTS = 20
UNFLOORED_MIN_RATE = 95.0

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


def unfloored_classes(classes, threshold=UNFLOORED_MIN_MUTANTS, min_rate=UNFLOORED_MIN_RATE):
    """Well-covered classes no floor gates: (name, mutants, rate), largest first.

    The floors exist to stop extracted collaborators decaying, but they only gate the names in FLOORS —
    a new collaborator with 40 mutants and no entry is invisible to --check.  This surfaces it at check
    time, when the extraction is still fresh, instead of relying on the agent remembering to add one.
    See UNFLOORED_MIN_MUTANTS / UNFLOORED_MIN_RATE for why both bounds are needed.
    """
    missing = []
    for name, counts in classes.items():
        if name in FLOORS or name in NO_FLOOR:
            continue
        killed, total, rate = kill_rate(counts)
        if total >= threshold and rate >= min_rate:
            missing.append((name, total, rate))
    return sorted(missing, key=lambda row: (-row[1], row[0]))


def print_unfloored(classes):
    """Warn (never fail) about well-covered classes with no floor entry.  Returns how many were reported."""
    missing = unfloored_classes(classes)
    if not missing:
        return 0
    print()
    print(f"NOTE: {len(missing)} class(es) are >= {UNFLOORED_MIN_MUTANTS} mutants at "
          f">= {UNFLOORED_MIN_RATE:.0f}% killed but have no floor in FLOORS:")
    for name, total, rate in missing:
        print(f"  {name:<28} {total:>4} mutants  {rate:5.0f}% killed  "
              f"— add a floor, or add it to NO_FLOOR with a reason")
    print("  (not an error: the floors are per-collaborator decisions, not a blanket coverage gate)")
    return len(missing)


def report_reference_time(report):
    """When the report's inputs were last fixed: the run's start when that is known, else its completion.

    check-mutation.sh stamps the start of the run (RUN_START_STAMP).  Anything edited after that stamp is
    not represented in the report, whether the edit landed before the report was written or after.  The
    stamp is used only when it *predates* the report: a stamp left by an older, killed run would
    otherwise make every later report look stale.
    """
    report_mtime = os.path.getmtime(report)
    try:
        with open(RUN_START_STAMP) as fh:
            started = float(fh.read().strip())
    except (OSError, ValueError):
        return report_mtime
    return started if started < report_mtime else report_mtime


def stale_sources(report, roots, suffixes):
    """Files newer than the report's inputs: it predates them, so its numbers describe an older suite."""
    reference = report_reference_time(report)
    stale = []
    for root in roots:
        if os.path.isfile(root):
            if os.path.getmtime(root) > reference:
                stale.append(root)
            continue
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d != "target"]
            for filename in filenames:
                if filename.endswith(suffixes):
                    path = os.path.join(dirpath, filename)
                    if os.path.getmtime(path) > reference:
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

    # Advisory, in every mode and never a failure: a collaborator that was extracted and forgotten.
    print_unfloored(classes)

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
