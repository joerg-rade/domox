#!/usr/bin/env python3
"""
Mermaid guardrail for DoMoX.

Why this exists:
  A mermaid node that carries **no `:::class`** is drawn with the *renderer's* own fill while the
  diagram's text colour still applies.  In the IntelliJ Cline plugin (whose canvas is the chat's own
  background, transparent SVG over it) that is **black text on a black box**: the label is invisible
  and the diagram reads as a row of empty rectangles.  It looks fine in the source and fine in any
  light-themed preview, so nothing else catches it.

  Verified 2026-11 by posting three blocks into the plugin: identical diagrams, one with `classDef`
  on every node (readable), one with only the `%%{init}%%` theme line (dark/invisible).  The theme
  line is therefore NOT sufficient — the per-node `classDef` is what carries the contrast.

  This is the same class of defect as the East Frisian flag: a white eagle on white ground.  There
  is no error, no warning, and no way to tell from the source that the diagram is blank.

What it checks (per ```mermaid block, in **any** `.md` in the repo — build output excluded):
  FAIL  the block is never closed
  FAIL  the first line inside the fence is not `%%{init`            (the theme pin)
  FAIL  a `flowchart`/`graph` block defines a node with no `:::class`
  FAIL  a `flowchart`/`graph` block has no `classDef` at all
  FAIL  no mermaid block found at all                                (wrong working directory?)
  WARN  a `classDef` omits `fill:` or `color:`                       (may render dark-on-dark)
  WARN  a non-flowchart diagram type (`sequenceDiagram`, `classDiagram`, ...) — those accept no
        `classDef`, so their boxes are entirely at the renderer's mercy

  `subgraph` is deliberately NOT treated as a node: it is a cluster, styled by the theme's
  `clusterBkg`/`clusterBorder`, and it cannot carry `:::`.  Flagging it would be a false positive.

Usage:  python3 scripts/check-mermaid.py
Exit:   0 clean · 1 violation · 2 nothing scanned
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# An id followed by a node-shape opener: A["..."] A("...") A{"..."} A[...]
NODE = re.compile(r'(?:^|[\s>|])([A-Za-z_][A-Za-z0-9_]*)\s*(?:\["|\[\(|\{"|\()')
# Edge operators, longest first — splitting a line on these isolates each node so that a line where
# only ONE of two nodes is styled still fails (see the note at the use site).
EDGE = re.compile(r'-->|-\.->|==>|---|-\.-|===|--o|--x')
# `class A,B className` — the alternative styling form, which leaves the node line itself bare.
CLASS_ASSIGN = re.compile(r'^\s*class\s+([A-Za-z0-9_,\s]+?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*$')

FLOWCHART_TYPES = ('flowchart', 'graph')


def blocks(path: Path):
    """Yield (first_line_no, [(line_no, text)], closed) for each ```mermaid fence."""
    lines = path.read_text(errors='replace').splitlines()
    i = 0
    while i < len(lines):
        if lines[i].startswith('```mermaid'):
            start = i + 1
            body = []
            i += 1
            closed = False
            while i < len(lines):
                if lines[i].startswith('```'):
                    closed = True
                    break
                body.append((start + len(body), lines[i]))
                i += 1
            yield start, body, closed
        i += 1


def main() -> int:
    EXCLUDE = {'target', 'node_modules', 'venv', '.git', 'build', 'dist', 'out'}
    roots = sorted(
        p for p in Path('.').rglob('*.md')
        if not any(part in EXCLUDE for part in p.parts)
    )

    failures: list[str] = []
    warnings: list[str] = []
    n_blocks = 0

    for path in roots:
        try:
            text = path.read_text(errors='replace')
        except OSError:
            continue
        if '```mermaid' not in text:
            continue

        for start, body, closed in blocks(path):
            n_blocks += 1
            where = f'{path}:{start}'

            if not closed:
                failures.append(f'{where} — ```mermaid block is never closed')
                continue
            if not body:
                failures.append(f'{where} — empty ```mermaid block')
                continue

            first = body[0][1].strip()
            if not first.startswith('%%{init'):
                failures.append(
                    f'{where} — first line inside the fence is not the `%%{{init` theme pin '
                    f'(found: {first[:60]!r})'
                )

            # Diagram type = first non-comment, non-blank line.
            kind = ''
            for _, ln in body:
                s = ln.strip()
                if s and not s.startswith('%%'):
                    kind = s.split()[0]
                    break

            if kind not in FLOWCHART_TYPES:
                warnings.append(
                    f'{where} — `{kind}` accepts no `classDef`; its boxes are at the renderer\'s '
                    f'mercy (prefer `flowchart` where legibility matters)'
                )
                continue

            # Nodes styled via `class A,B name` (the alternative form) count as styled.
            assigned: set[str] = set()
            for _, ln in body:
                m = CLASS_ASSIGN.match(ln)
                if m:
                    assigned.update(x.strip() for x in m.group(1).split(',') if x.strip())

            n_classdef = 0
            for _, ln in body:
                s = ln.strip()
                if s.startswith('classDef'):
                    n_classdef += 1
                    if 'fill:' not in s:
                        warnings.append(f'{where} — classDef without `fill:` → {s[:70]}')
                    if 'color:' not in s:
                        warnings.append(
                            f'{where} — classDef without `color:` (may render dark-on-dark) → {s[:60]}'
                        )
                if s.startswith(('%%', 'classDef', 'style ', 'subgraph', 'end', 'linkStyle', 'class ')):
                    continue
                # Check each node individually.  A line-level `':::' in ln` test is too coarse: on
                # `A["x"]:::ok --> B["y"]` it passes, because the styled node on the left supplies
                # the `:::` for the whole line, and B stays invisible.  (Found by negative-testing
                # this script — the defect is exactly a *partly* styled line.)
                for seg in EDGE.split(ln):
                    if ':::' in seg:
                        continue
                    hits = NODE.findall(seg)
                    if hits and not any(h in assigned for h in hits):
                        failures.append(
                            f'{where} — node(s) {hits} have no `:::class` '
                            f'→ invisible black-on-black: {seg.strip()[:70]}'
                        )

            if n_classdef == 0:
                failures.append(f'{where} — flowchart has no `classDef` at all')

    for w in warnings:
        print(f'    [WARN] {w}')
    for f in failures:
        print(f'    [FAIL] {f}')

    if n_blocks == 0:
        print('ERROR: no ```mermaid blocks found — wrong working directory?')
        return 2

    if not failures:
        print(f'OK: {n_blocks} mermaid block(s) carry a theme pin and style every node '
              f'({len(warnings)} warning(s)).')
        return 0
    return 1


if __name__ == '__main__':
    sys.exit(main())
