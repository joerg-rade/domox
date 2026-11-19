#!/usr/bin/env python3
"""A hash of the schema-relevant projection of the JPA entity sources.

Why this exists
---------------
`scripts/check-schema-drift.sh` compares the columns in `domox-webapp/create-tables.sql` (regenerated
by EclipseLink on every boot) against the live dev database.  EclipseLink only *creates* tables, never
ALTERs them, so a field added to an entity desyncs the dev DB silently — the check is there to catch
that before it becomes a runtime `column X does not exist`.

The check has to refuse to pass vacuously when the DDL predates the entity sources, and it used to
decide that by mtime alone (`find … -newer`).  That misfires on a **comment-only** edit: touching a
javadoc line bumps the mtime, the DDL looks stale, and the check exits 2 with a false alarm — so the
agent either boots the app for nothing or starts touching files.  A false alarm on a guardrail is
expensive in exactly the way the guardrail is supposed to be cheap.

What it does
------------
Prints the SHA-256 of the *projection* of the entity sources: only the lines that can change the
schema, with comments removed but line structure preserved (so adding a comment does not shift line
numbers and change the hash).  A comment-only edit leaves the hash untouched; adding, removing or
renaming a field or changing an annotation changes it.

The projection is deliberately INCLUSIVE.  A line the projection wrongly includes only causes a
harmless false "stale" (the safe direction); a line it wrongly excludes would let the drift check pass
against a stale DDL, which is the one outcome that must not happen.  Kept:

  * any line carrying a JPA mapping annotation (`@Entity`, `@Column`, `@JoinColumn`, …),
  * any type-declaration line (`class` / `interface` / `enum` / `record`, `extends`, `implements`) —
    inheritance changes the schema under TABLE_PER_CLASS and SINGLE_TABLE,
  * any line at brace depth <= 1 ending in `;` — the fields of the top-level type.  Method bodies sit
    deeper, so their contents are ignored.

Usage:
  scripts/schema-projection.py                  # the hash, on stdout
  scripts/schema-projection.py --print          # the projected lines instead of the hash
  scripts/schema-projection.py --root <dir>     # project a different source root

Exit codes: 0 ok, 2 the root does not exist.
"""
import argparse
import hashlib
import os
import re
import sys

DEFAULT_ROOT = os.path.join("domox-domain", "src", "main", "java")

# Every JPA annotation that can change the generated schema.  A superset is fine: an annotation that
# does not change the DDL still only moves the hash when the source moves.
SCHEMA_ANNOTATION = re.compile(
    r"@(Entity|Table|SecondaryTable|Column|JoinColumn|JoinColumns|JoinTable|OneToMany|ManyToOne"
    r"|OneToOne|ManyToMany|OrderColumn|OrderBy|MapKey|ElementCollection|Embedded|EmbeddedId"
    r"|IdClass|Basic|Id|Version|Enumerated|Lob|Temporal|SequenceGenerator|TableGenerator"
    r"|GeneratedValue|DiscriminatorColumn|DiscriminatorValue|Inheritance|AttributeOverride"
    r"|AttributeOverrides|AssociationOverride|AssociationOverrides|MapsId|Access|UniqueConstraint"
    r"|PrimaryKeyJoinColumn|NamedQuery|NamedQueries|Cacheable)\b")

TYPE_DECLARATION = re.compile(r"\b(class|interface|enum|record|extends|implements)\b")


def strip_comments(text):
    """Remove // and /* */ comments, keeping the line structure (each removed comment becomes newlines).

    Preserving the newlines matters: the projection is keyed by line number, and a comment edit must
    not shift the line numbers of the lines below it — that would change the hash and defeat the point.

    String and character literals are copied through untouched, so a `//` inside a literal (a URL, a
    path) is not mistaken for a comment: that would truncate the rest of the line, and a later change
    on the same line would then be missed — an exclusion, i.e. the unsafe direction.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        char = text[i]
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            if end == -1:                       # unterminated: comment to EOF
                out.append("\n" * text.count("\n", i))
                break
            out.append("\n" * text.count("\n", i, end + 2))
            i = end + 2
        elif text.startswith("//", i):
            end = text.find("\n", i)
            if end == -1:
                break
            i = end                            # the newline itself is copied by the loop
        elif char in ("\"", "'"):
            end = i + 1
            while end < n and text[end] != char:
                if text[end] == "\\":           # an escape sequence: skip the escaped character
                    end += 1
                end += 1
            end = min(end + 1, n)               # include the closing quote (if there is one)
            out.append(text[i:end])
            i = end
        else:
            out.append(char)
            i += 1
    return "".join(out)


def project_file(path, rel):
    """The projected lines of one source file, as "rel:line:text"."""
    with open(path, encoding="utf-8", errors="replace") as handle:
        text = strip_comments(handle.read())
    if not re.search(r"@Entity\b", text):       # not a JPA entity: cannot change the schema
        return []

    lines = []
    depth = 0
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        opens = raw.count("{")
        closes = raw.count("}")
        if line:
            is_field = (depth <= 1 and line.endswith(";")
                        and not TYPE_DECLARATION.search(line)
                        and not line.startswith(("import ", "package ", "static import ")))
            if SCHEMA_ANNOTATION.search(line) or TYPE_DECLARATION.search(line) or is_field:
                # Collapse internal whitespace: reformatting is not a schema change.
                lines.append(f"{rel}:{number}:" + " ".join(line.split()))
        depth += opens - closes
        if depth < 0:
            depth = 0
    return lines


def projection(root):
    """Every projected line under root, in a deterministic order (sorted walk, file order within a file)."""
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d != "target")
        for filename in sorted(filenames):
            if filename.endswith(".java"):
                path = os.path.join(dirpath, filename)
                out.extend(project_file(path, os.path.relpath(path, root)))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=DEFAULT_ROOT,
                        help=f"source root to project (default: {DEFAULT_ROOT})")
    parser.add_argument("--print", dest="show", action="store_true",
                        help="print the projected lines instead of their hash")
    args = parser.parse_args()

    if not os.path.isdir(args.root):
        print(f"no source root at {args.root}", file=sys.stderr)
        return 2

    lines = projection(args.root)
    if args.show:
        print("\n".join(lines))
        return 0

    print(hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest())
    return 0


if __name__ == "__main__":
    sys.exit(main())
