---
name: formatting
description: "Output formatting conventions — use scannable lists with bold lead-ins, ✅/⚠/❌ status icons, Markdown tables, and Mermaid diagrams."
disable-model-invocation: true
---

# Output Formatting Rules

**Full reference with examples:** `docs/formatting/readability-guide.md`

## Core rules

- **Structure:** Use clear header hierarchies (`##`, `###`) to separate sections.
- **Scannability:** Bold the first 2–3 words of bullet points to enable quick scanning.
- **Icons:** Use visual emojis (✅, ⚠️, ❌, 💡, 🚀) as section badges or status indicators.
- **Tables:** Structure comparisons, settings, or structured data into Markdown tables.
- **Diagrams:** Whenever explaining workflows, architecture, or sequence flows, include a `mermaid` diagram block.
- **Code Snippets:** Always specify language identifiers on code blocks for syntax highlighting.

## Thinking & structure rules

- **Reason before acting:** Structure analysis as a dependency tree — start with a one-sentence **Core Objective**, then **Analyzed Constraints**, then a **Component Breakdown Tree** of sub-systems → files → required changes. Show this plan before executing.
- **Tree syntax:** Render file systems, multi-step plans, and component hierarchies with ASCII tree characters (`├──`, `└──`, `│`), never flat comma lists; indent sub-elements to show parent-child ownership.
- **Nested plans:** Use indented checklists (`- [ ]`) for multi-phase work, with sub-items under their parent phase.
- **Data flow:** Prefer visual branching trees over prose for logical ownership/flow relationships.