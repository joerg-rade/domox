#!/usr/bin/env python3
"""Report — and optionally fail on — the geometry invariants of the reconstructed UML diagrams.

Why: every defect found in the UML rendering so far was invisible to the unit tests and only showed
up as "the picture looks wrong" — bends stated out of order (a zig-zag that seems to stop in
mid-air), paths built from `(0,0)` sentinels, points leaked from another connector, and arrow tips
buried under the box drawn on top of them.  This turns that inspection into one command, instead of
a fresh throwaway script per question.

Usage:
  scripts/uml-diagram-report.py                      # the richest diagram, from the running instance
  scripts/uml-diagram-report.py --diagram 'UFO Skeleton' --diagram 'UFO-S Lite'   # specific ones
  scripts/uml-diagram-report.py --payload payload.json   # analyse a payload file (no instance)
  scripts/uml-diagram-report.py --check              # exit 1 when any invariant is violated

Env: DOMOX_URL (default http://localhost:8080), DOMOX_USER / DOMOX_PASS (default sven / pass).
Mirrors `UmlLayoutDiagrams`'s constants, so the thresholds mean the same thing as in the domain.
"""
import argparse
import base64
import json
import math
import os
import sys
import urllib.request

BASE = os.environ.get("DOMOX_URL", "http://localhost:8080").rstrip("/")
USER = os.environ.get("DOMOX_USER", "sven")
PASS = os.environ.get("DOMOX_PASS", "pass")

# Mirrors UmlLayoutDiagrams.ROUTE_SLACK — slack added to a route's box-to-box distance.
SLACK = 60.0
# "On the box edge" tolerance, in model units.
ON_EDGE = 1.5
# A tip this far outside its box is floating in empty space rather than merely nudged off the edge.
# Mirrors UmlLayoutDiagrams.END_SLACK.
FLOATING = 30.0


def _headers():
    return {
        "Authorization": "Basic " + base64.b64encode(f"{USER}:{PASS}".encode()).decode(),
        "Accept": "application/json",
    }


def _get(path):
    req = urllib.request.Request(BASE + path, headers=_headers())
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.loads(response.read().decode())


def _invoke(action, parameters):
    """Invokes a service action.  RestfulObjects states each parameter as `{"value": …}` — a bare
    string is rejected (and Causeway's failure handler then dies with a misleading "does not
    represent map", which is how this was found)."""
    headers = dict(_headers())
    headers["Content-Type"] = "application/json"
    body = {name: {"value": value} for name, value in parameters.items()}
    req = urllib.request.Request(f"{BASE}/restful/services/domox.Corpora/actions/{action}/invoke",
                                 data=json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.loads(response.read().decode())


def _self_link(representation):
    for link in representation.get("links", []):
        if link.get("rel") == "self":
            return link["href"]
    raise RuntimeError("the action result carries no self link")


def diagram_payload(name):
    """Invokes `Corpora#openUmlDiagram` and reads the `diagram` property back off the view model."""
    href = _self_link(_invoke("openUmlDiagram", {"diagramName": name}))
    path = href[len(BASE):] if href.startswith(BASE) else href
    value = _get(path + "/properties/diagram")["value"]
    return json.loads(value) if isinstance(value, str) else value


# NOTE: there is no RestfulObjects resource that lists this action's parameter choices (Causeway
# returns "No static resource …/params/0/choices"), so the diagrams cannot be enumerated over the API —
# name them with `--diagram`, or let the blank name select the richest one.


# ---- invariants (the same ones UmlLayoutIntegrityTest asserts) --------------------------------

def _boxes(payload):
    return {n["id"]: (n["x"], n["y"], n["w"], n["h"]) for n in payload.get("nodes", [])}


def _inside(point, box, tol=ON_EDGE):
    x, y, w, h = box
    return x - tol <= point[0] <= x + w + tol and y - tol <= point[1] <= y + h + tol


def _edge_distance(point, box):
    x, y, w, h = box
    dx = max(x - point[0], 0, point[0] - (x + w))
    dy = max(y - point[1], 0, point[1] - (y + h))
    return math.hypot(dx, dy)


def _centre_distance(a, b):
    return math.hypot((a[0] + a[2] / 2) - (b[0] + b[2] / 2), (a[1] + a[3] / 2) - (b[1] + b[3] / 2))


def _backtracks(points):
    """Exact 180-degree reversals — a segment immediately retraced, which no real route does."""
    count = 0
    for i in range(2, len(points)):
        ax, ay = points[i - 1][0] - points[i - 2][0], points[i - 1][1] - points[i - 2][1]
        bx, by = points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]
        if ax * bx + ay * by < 0 and abs(ax * by - ay * bx) < 1e-9:
            count += 1
    return count


def analyse(payload):
    boxes = _boxes(payload)
    report = {
        "title": payload.get("title", "?"),
        "canvas": payload.get("canvas", {}),
        "nodes": len(payload.get("nodes", [])),
        "links": len(payload.get("links", [])),
        "arrows": 0, "dangling": 0, "hiddenTips": 0, "floatingTips": 0,
        "backtracks": 0, "strays": 0, "straight": 0,
    }
    for link in payload.get("links", []):
        source, target = link.get("source"), link.get("target")
        points = link.get("points") or []
        if source not in boxes or target not in boxes:
            report["dangling"] += 1
            continue
        if len(points) < 2:
            report["straight"] += 1
            continue
        if link.get("kind") == "generalization":
            # The arrow lands on `source` — the super-class end of the path.
            report["arrows"] += 1
            tip, box = points[-1], boxes[source]
            if _inside(tip, box):
                if _edge_distance(tip, box) > ON_EDGE:
                    report["hiddenTips"] += 1      # inside the box -> the box paints the arrowhead over
            elif _edge_distance(tip, box) > FLOATING:
                report["floatingTips"] += 1        # well outside -> ends in empty space
        report["backtracks"] += _backtracks(points)
        limit = max(SLACK, _centre_distance(boxes[source], boxes[target]))
        report["strays"] += sum(
            1 for p in points
            if _edge_distance(p, boxes[source]) > limit and _edge_distance(p, boxes[target]) > limit)
    report["ok"] = not any(report[key] for key in
                           ("dangling", "hiddenTips", "floatingTips", "backtracks", "strays"))
    return report


# ---- entry point ------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--diagram", action="append", default=[],
                        help="diagram name to report; repeatable (default: every diagram)")
    parser.add_argument("--payload", help="analyse a payload JSON file instead of a live instance")
    parser.add_argument("--check", action="store_true",
                        help="exit 1 when any diagram violates an invariant")
    args = parser.parse_args()

    payloads = []
    if args.payload:
        with open(args.payload) as handle:
            payloads.append(json.load(handle))
    else:
        names = args.diagram or [""]
        for name in names:
            try:
                payloads.append(diagram_payload(name))
            except Exception as exc:                                   # noqa: BLE001 - reported
                print(f"  [FAIL] {name or '(richest)'}: {exc}")
                if args.check:
                    sys.exit(1)

    print(f"  {'diagram':<40} {'nodes':>5} {'links':>5} {'arrows':>6} {'hidden':>6}"
          f" {'float':>5} {'back':>4} {'stray':>5}  canvas")
    failed = 0
    for payload in payloads:
        r = analyse(payload)
        failed += 0 if r["ok"] else 1
        canvas = r["canvas"]
        print(f"  {r['title']:<40} {r['nodes']:>5} {r['links']:>5} {r['arrows']:>6}"
              f" {r['hiddenTips']:>6} {r['floatingTips']:>5} {r['backtracks']:>4} {r['strays']:>5}"
              f"  {canvas.get('width', '?')}x{canvas.get('height', '?')}")

    if failed:
        print(f"\n{failed} of {len(payloads)} diagram(s) violate the geometry invariants"
              " (hidden tips / floating tips / backtracks / strays / dangling links).")
        sys.exit(1 if args.check else 0)
    print(f"\nOK: {len(payloads)} diagram(s), no geometry violations.")
    sys.exit(0)


if __name__ == "__main__":
    main()
