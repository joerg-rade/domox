# Inspecting the running app over the RestfulObjects API

Loaded on demand. The recipe for reading a live instance when Wicket is not drivable: a Wicket page needs
a session, and the sign-in form is an Ajax form that curl cannot drive.

Use the RestfulObjects API with basic auth (users are in `shiro.ini`, e.g. `sven:pass`) — or better,
`scripts/ro.py`:

| Call | What it does |
|---|---|
| `ro.py invoke <service> <action> [k=v…] [--out FILE]` | runs an action; prints the returned object's members |
| `ro.py property <objectRef> <member>` | one property |
| `ro.py layout <objectRef>` | the page's top-level columns and their span — the fastest way to see whether a layout XML was actually applied |
| `ro.py members\|object <objectRef>` | the members / the object |

It reads `DOMOX_URL` (default `http://localhost:8080`) and `DOMOX_USER` / `DOMOX_PASS`.

## The four things that cost a cycle to learn

1. **An object-reference parameter is written `@<ref>`**:
   `ro.py invoke domox.Reviews findByCandidate candidate=@/restful/objects/domox.ClassCdd/6640`, or `@`
   plus the `self:` URL an earlier `invoke` printed. Without the `@` the value goes as a plain string and
   RO rejects it: `Expected a link (because this object's type is not a value) but found no 'href'`.
2. **`@` only *builds* the reference.** An **entity href is valid as an action parameter** — the server
   resolves the oid itself — but a **`GET` on an entity is not exposed** by this app's RO viewer:
   `GET /restful/objects/domox.ClassCdd/6640` → **404**
   `{"message":"Could not determine adapter for bookmark: 'domox.ClassCdd:6640'"}`. So do not try to
   *read* an entity over RO; do pass an entity href when an action declares an entity parameter.
3. **The body shape for an entity-parameter action is a nested `{"<param>":{"value":{"href":H}}}`** —
   not a bare string, and not a bare `{"href":H}`. Verified 2026-11-21 with the UML export:
   `POST /restful/services/domox.DomainModels/actions/generateUmlDiagram/invoke` with
   `{"domainModel":{"value":{"href":"…/objects/domox.DomainModel/16177"}}}` → **200** plus a
   `domox.UmlClassDiagram` view model whose one member is `d3Graph` — the D3 payload the browser draws.
   `showUmlDiagrams` (same body) returns `domox.UmlDiagramSet` instead: every diagram of the model, one
   per tab, with `wholeModelGraph` as its readable member. See `.clinerules/d3-diagram.md` for what the
   payload means.
4. **`--out FILE` writes the result to a file and prints its size** instead of dumping it; a scalar result
   prints as `result: …`. Use one of them before pasting a diagram payload into a transcript.

For rendered HTML/CSS questions, prefer `scripts/verify-ui-render.sh` over reasoning about CSS.
