Let me first explore how `BASIC_ATTRIB` is used across the project to understand the full impact.

Now let me look at how the rules actually use `BASIC_ATTRIB` to understand if it could be made more generic:

Now I have a thorough understanding. Let me analyze how `BASIC_ATTRIB` is used across the codebase.

The `BASIC_ATTRIB` set is a hardcoded list of attribute names: `"name", "number", "type", "address", "level", "date", "time"`. It's used in rules TDR1-TDR13 to decide whether a noun should be treated as an **entity** (class) or a **property** (attribute). The concept is: if a noun is in this list, it's a "basic, generic attribute" rather than a domain entity.

The question asks whether this can be made more generic. Let me see what information might be available to make it data-driven rather than hardcoded.

Now I have a comprehensive understanding of the issue. Let me also check the `TypedDependencyRule` base class and a few more TDRs that use `BASIC_ATTRIB`:

Yes — `BASIC_ATTRIB` is a closed, hard-coded list of 7 lemmas, and it drives a binary Entity-vs-Attribute decision in 13+ rules (TDR1–TDR13). Any common attribute word outside that set (e.g. `email`, `phone`, `status`, `description`, `title`, `code`, `id`, `amount`, `quantity`, `comment`) currently gets misclassified as an **entity**, so widening it is worthwhile.

A few ways to make it more generic, in increasing order of sophistication:

1. **Bigger curated vocabulary + normalization** – same mechanism, more words, lowercase-normalized matching (lemmas are already singular via `getA()/getB()`).
2. **Configurable catalog (recommended)** – move the list into an injectable service seeded from defaults *plus* an `application.properties` entry, so analysts can extend it at deployment time without recompiling or touching rules.
3. **Pure heuristic (NOT recommended here)** – e.g. “common noun (NN/NNS), not proper noun (NNP) ⇒ attribute”. This won’t work in this pipeline because lemmas are lowercased (`user`, `system` are NN but are *entities*), so the proper-noun signal is lost. A curated, extensible list is the right level of genericity.

Below is the recommended implementation: the rules stay unchanged, and the vocabulary becomes configurable.

## 1. New `BasicAttributeCatalog` service

```java
package domox.dom.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary of "generic" attribute lemmas treated as attributes rather than entities
 * by the TDR rules (see RULES_EXAMPLES.md, TDR1-TDR13).
 *
 * <p>The vocabulary is the union of a built-in default set and any lemmas from the
 * {@code domox.nlp.basic-attributes} configuration property, so it can be extended at
 * deployment time without recompiling the rules.</p>
 */
@Service
public class BasicAttributeCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(BasicAttributeCatalog.class);

    private static final Set<String> DEFAULT_ATTRIBUTES = Set.of(
            // original vocabulary
            "name", "number", "type", "address", "level", "date", "time",
            // additional generic attribute lemmas that were previously misclassified as entities
            "id", "code", "status", "description", "title", "label", "value",
            "amount", "quantity", "size", "color", "email", "phone", "username",
            "password", "comment", "note", "version", "category", "priority");

    private final Set<String> attributes = new HashSet<>();

    public BasicAttributeCatalog(
            @Value("${domox.nlp.basic-attributes:}") List<String> configured) {
        attributes.addAll(DEFAULT_ATTRIBUTES);
        if (configured != null) {
            configured.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .forEach(attributes::add);
        }
        TypedDependencyPredicates.registerBasicAttributes(attributes);
        LOG.info("BasicAttributeCatalog initialised with {} attribute lemmas", attributes.size());
    }

    public boolean contains(String lemma) {
        return lemma != null && attributes.contains(lemma.toLowerCase(Locale.ROOT));
    }

    /** Programmatic extension, e.g. from SeedService or tests. */
    public boolean add(String lemma) {
        boolean added = lemma != null && attributes.add(lemma.toLowerCase(Locale.ROOT));
        if (added) {
            TypedDependencyPredicates.registerBasicAttributes(Set.of(lemma));
        }
        return added;
    }
}
```


## 2. Widen `TypedDependencyPredicates` (keeps the static API, so no rule changes)

```java
// ... existing code ...
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
// ... existing code ...

    //region BASIC_ATTRIB
    /**
     * Generic attribute lemmas shared across domains (see RULES_EXAMPLES.md).
     * The built-in defaults can be widened at runtime via config
     * ({@code domox.nlp.basic-attributes}) or programmatically through BasicAttributeCatalog.
     */
    private static final Set<String> BASIC_ATTRIB = new HashSet<>(Set.of(
            "name", "number", "type", "address", "level", "date", "time"));

    public static boolean isBasicAttribute(String lemma) {
        return lemma != null && BASIC_ATTRIB.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public static boolean isBasicAttributeA(TypedDependency td) {
        return isBasicAttribute(td.getA());
    }

    public static boolean isBasicAttributeB(TypedDependency td) {
        return isBasicAttribute(td.getB());
    }

    /** Called by BasicAttributeCatalog at startup to widen the vocabulary. */
    static void registerBasicAttributes(Collection<String> lemmas) {
        if (lemmas != null) {
            for (String lemma : lemmas) {
                BASIC_ATTRIB.add(lemma.toLowerCase(Locale.ROOT));
            }
        }
    }
    // end region
// ... existing code ...
```


Keeping the original 7 as a static baseline matters: `TDR2Test`/`TypedDependencyRulesTest` likely construct rules outside a Spring context, so the predicates still work without the catalog.

## 3. Configuration

Add to your application configuration (e.g. `domox-webapp/src/main/resources/application.properties`):

```properties
# Extra generic attribute lemmas, comma-separated; merged with the built-in defaults
domox.nlp.basic-attributes=ip,port,hostname,reference
```


## Things to be aware of

- **Semantic flip**: the new defaults change rule behavior — e.g. TDR1/TDR3 skip anything matching `isBasicAttributeB` when creating entities, so a requirement like *“the system stores the value”* would now produce an **Attribute** `value` instead of an Entity/class `Value`. If that’s undesirable for your domain, keep `DEFAULT_ATTRIBUTES` at the original 7 and only add words via configuration.
- **Tests**: review `TDR2Test` and `TypedDependencyRulesTest`, which may pin expectations for words now classified as attributes.
- **Related hard-coded vocab** (same pattern, optional follow-up): `determineType()` in `TypedDependencyRule` and the `isBlockedVerb(...)` lists in TDR3/TDR4 could be externalized the same way (e.g. a `domox.nlp.blocked-verbs` property) so the whole Entity/Attribute/verb heuristic set is configurable.

If you’d prefer full dependency injection instead of the static bridge, the cleaner (but larger) alternative is converting `TypedDependencyPredicates` to a `@Service` with injected `BasicAttributeCatalog` and instance methods — that touches the ~30 rule files that currently use `import static ... TypedDependencyPredicates.*`.

Want me to apply the recommended version (catalog + predicate widening), or the full DI conversion?