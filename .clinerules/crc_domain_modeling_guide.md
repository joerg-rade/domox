---
name: crc
description: "CRC (Class-Responsibility-Collaboration) domain modeling — extract entities, attributes, responsibilities, and collaborators from requirements text."
disable-model-invocation: true
---

# CRC Domain Modeling — Quick Reference

**Full guide with examples:** `docs/domain-modeling/crc-guide.md`

## Core mapping

| CRC Element | Look for | Domain equivalent |
|---|---|---|
| **Class** | Common nouns / noun phrases | Entity / Aggregate Root / Value Object |
| **Attributes** | Adjectives, possessive nouns | Internal state |
| **Responsibilities** | Active verbs / verb phrases | Methods, business logic |
| **Collaborators** | Transitive verbs (interaction) | Associations, dependencies |

## Quick checklist

- [ ] **Nouns** extracted → Candidate **Classes** or **Attributes**
- [ ] **Verbs** extracted → Candidate **Responsibilities**
- [ ] **Interaction verbs** extracted → Candidate **Collaborators**
- [ ] Technical details (UI, SQL, API) filtered out
- [ ] Responsibilities split into *Knowing* vs *Doing*
