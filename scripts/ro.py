#!/usr/bin/env python3
"""Minimal RestfulObjects client for live checks against a running instance.

Why: the Wicket pages need a session and the sign-in form is an Ajax form curl cannot drive, so the
RO API is the way to inspect a running instance.  Doing it by hand means re-writing the same urllib
boilerplate (basic auth, JSON body, self-link extraction, member dump) in a throwaway /tmp script
every time — eight of them in one session.

Usage:
  scripts/ro.py invoke   <service> <action> [name=value ...]
      e.g. scripts/ro.py invoke domox.Corpora searchOntologyAsD3Graph searchTerm=Pet
  scripts/ro.py property <objectRef> <member>
  scripts/ro.py members  <objectRef>
  scripts/ro.py layout   <objectRef>          # top-level columns with their span and members
  scripts/ro.py object   <objectRef>

<objectRef> is a /restful/... path or a full URL (e.g. the self-link printed by `invoke`).

Env: DOMOX_URL (default http://localhost:8080), DOMOX_USER / DOMOX_PASS (default sven / pass).
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("DOMOX_URL", "http://localhost:8080").rstrip("/")
USER = os.environ.get("DOMOX_USER", "sven")
PASS = os.environ.get("DOMOX_PASS", "pass")


def call(path, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Authorization",
                   "Basic " + base64.b64encode(f"{USER}:{PASS}".encode()).decode())
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())


def to_path(ref):
    return ref[len(BASE):] if ref.startswith(BASE) else ref


def link(rep, rel):
    for l in rep.get("links", []):
        if l.get("rel") == rel:
            return l["href"]
    return None


def show(rep, limit=70):
    print("title:", rep.get("title"))
    for name, m in (rep.get("members") or {}).items():
        value = m.get("value") if isinstance(m, dict) else None
        if isinstance(value, str) and len(value) > limit:
            value = value[:limit] + "..."
        if value is not None or name in ("message", "diagram"):
            print(f"  {name:<20} {value!r}")


def cmd_invoke(args):
    service, action = args[0], args[1]
    body = {}
    for kv in args[2:]:
        k, _, v = kv.partition("=")
        body[k] = {"value": v}
    rep = call(f"/restful/services/{service}/actions/{action}/invoke", "POST", body)
    print("invoke:", rep.get("title"))
    self_url = link(rep, "self")
    if not self_url:
        print(json.dumps(rep, indent=1)[:2000])
        return
    print("self:  ", self_url)
    print("--- object ---")
    show(call(to_path(self_url)))


def cmd_property(args):
    print(call(f"{to_path(args[0])}/properties/{args[1]}").get("value"))


def cmd_members(args):
    show(call(to_path(args[0])))


def cmd_object(args):
    print(json.dumps(call(to_path(args[0])), indent=1))


def cmd_layout(args):
    layout = call(f"{to_path(args[0])}/object-layout")
    for row in layout.get("row", []):
        for row_content in row.get("rowContents", []):
            col = row_content.get("col", {})
            parts = []
            for fs in col.get("fieldSet", []):
                parts.append("fieldSet[%s]%s" % (fs.get("name"),
                                                 [p.get("id") for p in fs.get("property", [])]))
            for coll in col.get("collection", []):
                parts.append("collection[%s]" % coll.get("id"))
            if col.get("domainObject") is not None:
                parts.append("domainObject")
            if parts:
                print("  span=%-4s %s" % (col.get("span"), "; ".join(parts)))


COMMANDS = {
    "invoke": cmd_invoke,
    "property": cmd_property,
    "members": cmd_members,
    "object": cmd_object,
    "layout": cmd_layout,
}

if len(sys.argv) < 3 or sys.argv[1] not in COMMANDS:
    print(__doc__)
    sys.exit(2)

try:
    COMMANDS[sys.argv[1]](sys.argv[2:])
except IndexError:
    print("missing argument — see usage below", file=sys.stderr)
    print(__doc__, file=sys.stderr)
    sys.exit(2)
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}: {e.read().decode()[:400]}", file=sys.stderr)
    sys.exit(1)
