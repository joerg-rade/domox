# Class-Responsibility-Collaboration (CRC) Domain Modeling Guide

This guide provides practical heuristics and text-analysis instructions for identifying domain components from requirements, user stories, or domain descriptions using the **Class-Responsibility-Collaboration (CRC)** technique.

---

## Overview of CRC Elements

| CRC Element | Linguistic Indicator | Domain Modeling Equivalent |
| :--- | :--- | :--- |
| **Class** | Common Nouns / Noun Phrases | Entity / Aggregate Root / Value Object |
| **Attributes/Properties** | Adjectives, Possessive Nouns, Modifiers | Internal state / Characteristics |
| **Responsibilities (Actions)** | Verbs / Verb Phrases | Methods, Behaviors, Business Logic |
| **Collaborators (Relations)** | Verbs indicating interaction, Transitive Verbs | Associations, Dependencies, Aggregations |

---

## Step-by-Step Instructions for Extraction

### 1. Identifying Classes (Entities & Value Objects)

**Rule:** Look for **nouns** and **noun phrases** that represent key concepts, roles, places, or organizational structures in the domain.

*   **Heuristic / Instruction:**
    1. Highlight all nouns and noun phrases in the source text.
    2. Filter out trivial or boundary nouns (e.g., "system", "database", "user interface", "screen").
    3. Ask: *"Does this noun have its own lifecycle, distinct identity, or state that changes over time?"*
        *   If **Yes** $\rightarrow$ It is an **Entity Class**.
        *   If **No**, but it describes something else $\rightarrow$ It might be an **Attribute** or a **Value Object**.

*   **Prompt/Instruction Template:**
    > "List all nouns that describe domain concepts holding distinct identity or state. Exclude system components, technical terms, and purely display-related terms."

---

### 2. Identifying Attributes & Properties

**Rule:** Look for **adjectives**, **possessive nouns**, or nouns that describe a feature, state, or measure of a class.

*   **Heuristic / Instruction:**
    1. Look for phrases indicating ownership or characteristics (e.g., *"Customer's email address"*, *"Order status"*, *"Total amount"*).
    2. Check if a noun has value equality rather than conceptual identity.
    3. Ask: *"Does this concept exist only to describe another entity, without having its own independent behavior?"*
        *   If **Yes** $\rightarrow$ Model it as an **Attribute** on that class or as a **Value Object**.

*   **Prompt/Instruction Template:**
    > "Identify characteristics, identifiers, and descriptive qualities associated with each candidate class. Determine if they are simple attributes (primitive types) or value objects (descriptive data with no identity)."

---

### 3. Identifying Responsibilities (Actions & Behaviors)

**Rule:** Look for **active verbs** and **verb phrases** associated with a specific class.

*   **Heuristic / Instruction:**
    1. Highlight all main active verbs in the domain narrative.
    2. Map each verb to the entity that is primarily responsible for performing or enforcing that business rule.
    3. Ask: *"What active work, calculation, state transition, or knowledge-keeping must this entity perform?"*
        *   **Knowing Responsibilities:** Remembering state, relationships, or calculated values (e.g., *"Knows its total balance"*).
        *   **Doing Responsibilities:** Performing calculations, executing business rules, creating or updating other objects (e.g., *"Calculates tax"*, *"Validates payment"*).

*   **Prompt/Instruction Template:**
    > "For each class, list its core responsibilities divided into 'Knowing' (what it remembers or calculates) and 'Doing' (what actions or business rules it executes)."

---

### 4. Identifying Collaborators & Relations

**Rule:** Look for **transitive verbs** and **verbs describing interaction** between two or more entities.

*   **Heuristic / Instruction:**
    1. Identify actions where one class cannot complete its responsibility alone.
    2. Ask: *"What other entity does Class A need information from or need to notify in order to fulfill this responsibility?"*
        *   The helper entity is a **Collaborator**.
    3. Define the nature of the relationship:
        *   **Association:** *"Class A communicates with Class B."*
        *   **Aggregation / Composition:** *"Class A contains or is made up of Class B."*
        *   **Inheritance (Is-A):** *"Class A is a specialized type of Class B."*

*   **Prompt/Instruction Template:**
    > "For every responsibility assigned to a class, identify which other classes must be queried, updated, or created to complete the action. Record these as Collaborators on the CRC card."

---

## Practical Example: E-Commerce Order System

### Source Text
> "A Customer places an Order. An Order consists of multiple Order Items. Each Order Item references a Product and specifies a Quantity. The Order calculates the total price by adding up the costs of its items and applying a Discount Code if available. When the Order is completed, a Payment is processed by the Payment Gateway, and an Invoice is generated."

### Resulting CRC Extraction

#### 1. CRC Card: `Order`
*   **Class:** `Order`
*   **Attributes:** `orderDate`, `status`, `totalPrice`
*   **Responsibilities:**
    *   Add items to order *(Doing)*
    *   Calculate total price *(Doing)*
    *   Apply discount code *(Doing)*
*   **Collaborators:**
    *   `OrderItem` (to sum prices)
    *   `DiscountCode` (to get discount rules)
    *   `Payment` (to trigger settlement)

#### 2. CRC Card: `OrderItem`
*   **Class:** `OrderItem`
*   **Attributes:** `quantity`, `subtotal`
*   **Responsibilities:**
    *   Calculate item subtotal *(Doing)*
*   **Collaborators:**
    *   `Product` (to retrieve unit price)

---

## Summary Checklist for CRC Modeling

- [ ] **Nouns** extracted $\rightarrow$ Candidate **Classes** or **Attributes**.
- [ ] **Verbs** extracted $\rightarrow$ Candidate **Responsibilities**.
- [ ] **Interaction verbs** extracted $\rightarrow$ Candidate **Collaborators / Relations**.
- [ ] Technical implementation details (UI, SQL, API protocols) filtered out.
- [ ] Responsibilities properly categorized into *Knowing* vs *Doing*.