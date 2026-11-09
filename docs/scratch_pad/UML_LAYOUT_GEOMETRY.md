# UML layout — the geometry rules, and how they were measured

Reference for the rules in `.clinerules/d3-diagram.md`.  **Nothing here changes behaviour** — it is the
measurement record behind the constants and the invariants, kept out of the always-loaded rule file.

Measured over the bundled OntoUML export: **10 diagrams**, 86 boxes and 110 connectors in the largest
(UFO Skeleton), **217** generalizations and **378** association ends in total.

## Measuring — offline, no instance

The renderer is offline (`JenaRdfAdapter` reads the classpath `ontology.ttl`), so a measurement run needs
**no** app, database or SPARQL endpoint:

```bash
mvn -o -pl domox-adapter test -Dtest=UmlLayoutIntegrityTest   # asserts + dumps payloads
python3 scripts/uml-diagram-report.py --check --payload domox-adapter/target/uml-payloads
```

That is ~10 s of Maven startup plus ~0.05 s of analysis.  The alternative — `install` + boot + a live
SPARQL query — costs ~35 s and a database, which is why the dump exists.

## The invariants (asserted twice: Kotlin and Python)

| Invariant | Measured now | Before the fix |
|---|---:|---:|
| connector endpoints on a box **corner** | **0** | 44 |
| association ends sharing a point on one edge | **0** | 14 (of 378) |
| generalizations entering their super-class **sideways** | **0** | 12 groups |
| trunks shorter than 8 units | **0** | 4 (2–3 units) |
| generalizations leaving the sub-class's **top middle** | **217 / 217** | 0 |
| multi-generalization groups sharing one trunk / one arrowhead | **67 / 67** | 0 |
| segments off-axis | **0** | 0 |
| boxes narrower than their label (per diagram) | ≤ 3 | 99 when widening is off |

`UmlLayoutIntegrityTest` asserts these over the real model; `uml-diagram-report.py --check` asserts the
same over the dumped payloads (and over a live instance).  `scripts/check.sh` runs both.

## Which edge an inheritance trunk enters

Inheritance reads as a vertical tree, so the trunk enters the super-class's **bottom** edge when its
sub-classes are below it, the **top** when they are above, and a **side** only when they are essentially
level (`TRUNK_VERTICAL_BIAS` = 0.25 — the vertical wins unless the horizontal offset is more than four
times the vertical one).

| | bottom | top | side |
|---|---:|---:|---:|
| with `TRUNK_VERTICAL_BIAS` | **64** | 2 | 5 |
| taking the plain dominant axis | 56 | 2 | 12 |

The dominant axis put the trunk on the *side* of twelve super-classes whose sub-classes were all
underneath — which is what "orange lines are not entering from below" looked like.

## Trunk length, and the sub-class with no room

The connector is routed to a point `TRUNK_STUB` (24) outside the super-class edge, shortened to clear
whatever box is in the way (`TRUNK_GAP` 4) — a sub-class sitting just below its super-class used to put
that point *inside* itself, the search failed outright and the connector fell back to a side-approaching
route.  Result: trunk lengths 12 (minimum) / 24 (median) / 24 (maximum); before, four groups had 2–3
unit stubs and read as sideways entries.

A sub-class with no room to rise the 20-unit `DEPARTURE_STUB` before the trunk (`hasRoomToRise`) leaves
in whatever direction it likes — forcing the rise made the route overshoot the trunk and loop back.  The
attachment point is still the middle of its top edge.

## Crossings — and why the number is noisy

**128** true (transversal) crossings over the ten diagrams, 0 segments off-axis.  The path there:

| configuration | crossings |
|---|---:|
| reusing the export's own bends | 214 |
| centre-to-centre staircase for unusable paths | 227 |
| re-routed, attaching wherever the export did (corners allowed) | 85 |
| …corner attachment restored, **plus** the generalization merge | 127 |
| midpoint ports only | 181 |
| **midpoints + top-middle departure + spreading + vertical trunk (now)** | **128** |

The midpoint rule therefore costs essentially nothing against the 127 control.  Treat any single figure
as **one sample, not an optimum**: the measure is chaotic in the routing order and in small geometry
changes — free choice of side measured 192, longest-first 158, shortest-first 85.

What remains is largely structural: connectors that converge on the same box (or the same trunk) cross
one another near it, and a connector routed later still crosses one already placed rather than take a
very long way round.  Only a router that considers all connectors together (or a layered/hierarchical
layout) would remove those.

## Labels

156 labelled connectors.  Before the fix, 124 put the label on a corner (and the 28 two-point ones on an
end point); now all 156 sit mid-run, the nearest 14.5 units clear of any bend, and 42 still have another
line passing over the text — which is what the white halo is for.

## Box widening

**269 of 273** labels render at a uniform 12px after widening; the 4 boxes with no room either way keep
their width and the client shrinks that one label (`getComputedTextLength`, floor 6px — the worst lands
at 10.4px).  Wrapping is not an option: most names are compound words
(`ServiceProviderOnNegotiation`) with nothing to break on, and many boxes are only 28 units tall.
