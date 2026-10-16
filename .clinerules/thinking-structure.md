# Thinking & Planning Output Structure

Whenever you analyze a task, troubleshoot an issue, or prepare a response, you **MUST** structure your thinking output in a hierarchical, tree-like format using the following phases before taking action:

## 1. 🧠 Reasoning & Dependency Tree
Break down the problem logically into parent components and child dependencies:
- **Core Objective:** [One-sentence summary of the goal]
- **Analyzed Constraints:**
    - Constraint A (e.g., tech stack limitations)
    - Constraint B (e.g., performance or state handling)
- **Component Breakdown Tree:**
  ```text
  Target Feature
  ├── Sub-system / Module 1
  │   ├── File to modify: `src/auth/auth.ts`
  │   └── Changes required: Update token expiration logic
  └── Sub-system / Module 2
      ├── File to create: `src/components/LoginModal.tsx`
      └── Changes required: Build form layout
  ```
  