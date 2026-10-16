# Output Formatting Guidelines: Tree-Like Structures

Whenever you present multi-level relationships, file systems, plans, or data objects, you **MUST** format them in a hierarchical, tree-like fashion rather than flat lists.

## 1. File Systems & Component Architectures
Always use standard ASCII/Unicode tree characters (`├──`, `└──`, `│`) for directories and file structures:
- **Rule:** Never use flat comma-separated or unindented bullet lists for paths spanning multiple levels.
- **Example:**
  ```text
  src/
  ├── components/
  │   ├── Header/
  │   │   ├── Header.tsx
  │   │   └── Header.styles.ts
  │   └── Footer.tsx
  ├── services/
  │   └── api.ts
  └── App.tsx
  ```
  
## 2. Multi-Step Execution Plans & Workflows
Structure plans using nested, indented task trees with parent phases and child actions:

Rule: Use indented markdown checklists (- [ ]) to show dependencies and sub-tasks clearly.

Example:
  ```text
Markdown
- [ ] **1. Core Infrastructure**
    - [ ] 1.1 Setup database schema and migrations
    - [ ] 1.2 Configure environment variables and security keys
- [ ] **2. Feature Implementation**
    - [ ] 2.1 Build API routes
    - [ ] 2.2 Hook up frontend components
  ```

## 3. Data Flow & Logical Relationships
When explaining code relationships, execution flow, or state hierarchies, prefer visual branching or nested bullet trees:

Rule: Indent sub-elements logically to show parent-child ownership.

Example:
  ```text
Plaintext
Root Application State
├── AuthStore
│   ├── isAuthenticated (boolean)
│   └── currentUser (User | null)
└── SettingsStore
└── theme ('light' | 'dark')
  ```
