Yes, you can detect **events** using syntactic dependencies from NLP parsing. Events are typically represented by **verbs** (actions) and their arguments (who/what/when/where). Here are the most relevant Universal Dependencies (UD) for event detection:

---

### **Key Dependencies for Event Detection**
| Dependency       | Description                                                                 | Example (`"John eats an apple"`)       |
|------------------|-----------------------------------------------------------------------------|-----------------------------------------|
| **`nsubj`**      | Nominal subject (who performs the action)                                   | `John` → `eats` (nsubj)                 |
| **`dobj`**       | Direct object (what is acted upon)                                          | `apple` → `eats` (dobj)                 |
| **`iobj`**       | Indirect object (to whom/for whom)                                          | `Mary` → `gives` (iobj)                 |
| **`advcl`**      | Adverbial clause (when/why/how the action happens)                          | `while hungry` → `eats` (advcl)         |
| **`xcomp`**      | Open clausal complement (secondary action)                                  | `wants` → `eat` (xcomp)                 |
| **`ccomp`**      | Clausal complement (embedded action)                                        | `said` → `he eats` (ccomp)              |
| **`aux`**        | Auxiliary verb (tense/mood of the action)                                   | `is` → `eating` (aux)                   |
| **`neg`**        | Negation modifier (if the action is negated)                                | `not` → `eats` (neg)                    |
| **`advmod`**     | Adverbial modifier (how/when/where the action happens)                      | `quickly` → `eats` (advmod)             |
| **`obl`**        | Oblique nominal (prepositional phrases, e.g., "with a fork")                | `fork` → `eats` (obl)                   |
| **`root`**       | The main predicate (usually the verb representing the core event)           | `eats` (root)                           |

---

### **Example Rules for Event Detection**
1. **Basic Event Rule**:
    - **Trigger**: A verb with `root` dependency.
    - **Arguments**: `nsubj` (who), `dobj` (what), `advmod`/`obl` (when/where/how).
    - Example: `"John eats an apple"` → Event: `eat`, Actor: `John`, Object: `apple`.

2. **Temporal Events** (when something happens):
    - Look for `advcl` (adverbial clauses) or `obl` with temporal prepositions (`"after"`, `"before"`, `"during"`).
    - Example: `"After lunch, John eats an apple"` → Event: `eat`, Time: `after lunch`.

3. **Causal Events** (why something happens):
    - Look for `advcl` with causal conjunctions (`"because"`, `"since"`).
    - Example: `"John eats an apple because he is hungry"` → Event: `eat`, Cause: `hungry`.

4. **Negated Events**:
    - Check for `neg` dependency linked to the verb.
    - Example: `"John does not eat apples"` → Event: `eat`, Negated: `true`.

5. **Complex Events** (multiple actions):
    - Look for `xcomp` (secondary actions) or `ccomp` (embedded clauses).
    - Example: `"John wants to eat an apple"` → Primary Event: `want`, Secondary Event: `eat`.

---

### **How to Implement Rules**
1. **Filter dependencies** for the event trigger (e.g., `root` verb).
2. **Extract arguments** using the dependencies above.
3. **Combine into an event structure**:
   ```kotlin
   data class Event(
       val trigger: String,       // The verb (e.g., "eats")
       val subject: String?,      // nsubj (e.g., "John")
       val object: String?,       // dobj (e.g., "apple")
       val time: String?,         // advcl/obl (e.g., "after lunch")
       val cause: String?,        // advcl (e.g., "because hungry")
       val negated: Boolean       // neg (e.g., "not")
   )
   ```

---

### **Example Code**
Here’s how you might detect a basic event in Kotlin:
```kotlin
fun detectEvents(dependencies: List<ExtendedDependencyTO>): List<Event> {
    val root = dependencies.firstOrNull { it.dep == "root" } ?: return emptyList()
    val nsubj = dependencies.firstOrNull { it.governor == root.dependent && it.dep == "nsubj" }
    val dobj = dependencies.firstOrNull { it.governor == root.dependent && it.dep == "dobj" }
    val neg = dependencies.any { it.governor == root.dependent && it.dep == "neg" }

    return listOf(
        Event(
            trigger = root.dependentGloss,
            subject = nsubj?.dependentGloss,
            object = dobj?.dependentGloss,
            time = null,
            cause = null,
            negated = neg
        )
    )
}
```

---

### **When to Use This**
- **Event extraction** (e.g., for calendars, logs, or alerts).
- **Question answering** (e.g., "Who ate the apple?").
- **Summarization** (e.g., "John ate an apple").