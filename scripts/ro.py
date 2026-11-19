#!/usr/bin/env python3
"""Minimal RestfulObjects client for live checks against a running instance.

Why: the Wicket pages need a session and the sign-in form is an Ajax form curl cannot drive, so the
RO API is the way to inspect a running instance.  Doing it by hand means re-writing the same urllib
boilerplate (basic auth, JSON body, self-link extraction, member dump) in a throwaway /tmp script
every time — eight of them in one session.

Usage:
  scripts/ro.py invoke   <service> <action> [name=value ...] [--out FILE]
      e.g. scripts/ro.py invoke domox.OntologyDiagrams searchOntologyAsD3Graph searchTerm=Pet
  scripts/ro.py property <objectRef> <member>  [--out FILE]
  scripts/ro.py members  <objectRef>           [--out FILE]
  scripts/ro.py layout   <objectRef>           [--out FILE]  # top-level columns, span, members
  scripts/ro.py object   <objectRef>           [--out FILE]

<objectRef> is a /restful/... path or a full URL (e.g. the self-link printed by `invoke`).

Argument values are sent as strings, which is wrong for a parameter whose type is another
object: prefix it with `@` and it is sent as an object reference (`{"href": …}`) instead —
  scripts/ro.py invoke domox.Reviews findByCandidate candidate=@/restful/objects/domox.ClassCdd/6640
(`@` + the `self:` URL an earlier `invoke` printed works too).  The reference still has to
resolve: this app's RO viewer exposes services and view models, not entities, so use the id the
action declares, or the `self:` link of a view model an `invoke` returned.

`--out FILE` writes the result to FILE and prints its size, instead of dumping it — the way to
look at a big diagram payload without pasting it into the transcript.

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


def take_out(args):
    """Split an optional trailing `--out FILE` off an argument list."""
    if "--out" not in args:
        return args, None
    i = args.index("--out")
    try:
        return args[:i] + args[i + 2:], args[i + 1]
    except IndexError:
        print("--out needs a FILE argument", file=sys.stderr)
        sys.exit(2)


def emit(rep, out):
    """Dump a representation to stdout, or to `out` (then print only its size)."""
    if out is None:
        show(rep)
        return
    text = json.dumps(rep, indent=1)
    with open(out, "w") as f:
        f.write(text)
    print(f"wrote {out} ({len(text)} bytes)")


def cmd_invoke(args):
    service, action = args[0], args[1]
    rest, out = take_out(args[2:])
    body = {}
    for kv in rest:
        k, _, v = kv.partition("=")
        # `@ref` is an object parameter: RO wants {"value": {"href": …}} — a bare {"href": …}
        # is rejected with `No 'value' key`.  Anything else is sent as a plain string value.
        body[k] = {"value": {"href": to_path(v[1:])}} if v.startswith("@") else {"value": v}
    rep = call(f"/restful/services/{service}/actions/{action}/invoke", "POST", body)
    print("invoke:", rep.get("title"))
    self_url = link(rep, "self")
    if not self_url:
        print(json.dumps(rep, indent=1)[:2000])
        return
    print("self:  ", self_url)
    obj = call(to_path(self_url))
    if out is None:
        print("--- object ---")
        show(obj)
    else:
        emit(obj, out)


def cmd_property(args):
    rest, out = take_out(args)
    value = call(f"{to_path(rest[0])}/properties/{rest[1]}").get("value")
    if out is not None:
        emit(value, out)
    elif isinstance(value, (dict, list)):
        print(json.dumps(value, indent=1))
    else:
        print("result:", value)


def cmd_members(args):
    rest, out = take_out(args)
    emit(call(to_path(rest[0])), out)


def cmd_object(args):
    rest, out = take_out(args)
    rep = call(to_path(rest[0]))
    if out is None:
        print(json.dumps(rep, indent=1))
    else:
        emit(rep, out)


def cmd_layout(args):
    rest, out = take_out(args)
    layout = call(f"{to_path(rest[0])}/object-layout")
    if out is not None:
        emit(layout, out)
        return
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
