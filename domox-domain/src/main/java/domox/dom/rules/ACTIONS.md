Let me read the remaining action-indicating rules to give you a comprehensive answer.

Now let me check the base class to understand `getA()` / `getB()` semantics:

Good question — this comes down to how the rules encode the dependency grammar and what they do with it. Here's the breakdown based on the actual rule sources.

## The A/B convention

Every rule operates on a `TypedDependency` of the form `type(governor=A, dependent=B)`. The **grammatical role** of B is *not* defined by the rule — it's implied entirely by the dependency `type`:

| Dependency type | A (governor) | B (dependent) |
|---|---|---|
| `nsubj` | verb | **subject** (actor) |
| `nsubjpass` | verb | passive subject (the "patient") |
| `dobj` / `obj` | verb | **direct object** |
| `iobj` / `obl` | verb | indirect object |
| `nmod:by` / `agent` | verb | agent (semantic subject in passive) |
| `nmod:to/for/from/as` | verb | prepositional object (recipient, etc.) |
| `mark` | verb | subordinator (`if`) |
| `xcomp` | verb | clausal complement |

So a rule firing on `nsubj` is implicitly saying "B is the subject"; the same rule firing on `dobj` is saying "B is the direct object." The rules themselves rarely make that distinction explicit.

## Which rules indicate an action

**TDR27–TDR37** (Group 4: Operation/Action rules) are the ones that indicate actions. But they fall into two distinct flavors:

### 1. Rules that record the action (the verb) itself

| Rule | Output | Verb (A) set | How subject/object is used |
|---|---|---|---|
| **TDR32** | `User_Action.add(verb)` / `System_Actions.add(verb)` | user-input verbs vs system-output verbs | **B is read as the actor**: if B ≠ "system" → User_Action; if B == "system" → System_Actions (on `nsubj`, B is the subject; on `nmod:by`, the agent) |
| **TDR33** | `User_Action.add(verb)` | receive/accept/get/obtain/acquire/redeem | B is the actor but is **ignored** — both branches produce the same output |
| **TDR35** | `System_Actions.add("if/then/else" + B + A)` | conditional keywords (if/then/else) | Fires on `advcl`/`mark`/`advmod`; A is the verb of the clause, B is the condition/marker |
| **TDR36** | `System_Actions.add(B + A)` | "validate" only | B **is the subject** (`nsubj(validate, system)` → `system validate`) |
| **TDR37** | `System_Action.add(A + B)` | continue/restart/go/repeat | B is the subject on `nsubj`, or the complement on `xcomp`; the spec wants `nummod.B`/`dobj.B` but the implementation falls back to `currentTd.getB()` |

### 2. Rules that record data (objects), not the verb

| Rule | Output | Verb (A) set | Role of B |
|---|---|---|---|
| **TDR27** | `Input_Data.add(B...)` | input/enter/fill/click/select/add/record/process/validate | The verb only gates the rule; `then()` collects **all attribute B's** in the sentence (skipping nsubj/dobj/etc.) — i.e., the *data being input*, not the subject |
| **TDR28** | `Output_Data.add(B...)` | display/output/retrieve/show/view/print | Same pattern — collects attribute B's as output data |
| **TDR29** | `Output_Data` if B=="system", else `Input_Data` | get/send/prepare | B **is used as a routing key**: "system" → output, any basic attribute → input |
| **TDR30** | `Input_Data.add(B...)` | past-tense input verbs (entered, filled, ...) on `nmod:by/agent/with` | same data-collection pattern |
| **TDR31** | `Output_Data.add(B...)` | past-tense output verbs (displayed, printed, ...) on `nmod:by/agent/with` | same data-collection pattern |

Also technically action-adjacent: **TDR34** records `Exceptions.add(B + A)` (fires on `xcomp`/`amod`/`neg` with error terms), but it models an exception condition, not an action.

## Direct answers to your questions

- **Which rules indicate an action?** TDR27–TDR37. TDR32/TDR33/TDR35/TDR36/TDR37 surface the **verb as the action**; TDR27–TDR31 surface the **data** and only use the verb to decide whether it's input or output.
- **Do they indicate the subject?** Only incidentally. TDR32 and TDR33 semantically treat B as the actor (user vs. system) — for `nsubj` that means the subject; for `nmod:by` it's the agent. TDR36 and TDR37 fire on `nsubj`/`xcomp`, so B is the subject there too, but they don't label it as such — they just concatenate it into the result string.
- **Do they indicate objects?** Mostly **no**. TDR27–TDR31 fire on `dobj`/`iobj`/`pobj` in `when()`, so the matched dependency's B *is* often the direct object — but the `then()` logic then scans the *whole sentence* for basic attributes and adds those, discarding the object identity. TDR37's comment says it should use `nummod.B`/`dobj.B` as the object, but the implementation ignores that and just uses `currentTd.getB()`.

So the short version: **the rules encode the action verb (A) and a participant (B), but they don't systematically model "subject" vs. "object" roles** — the role is whatever the dependency type implies, and only TDR32/TDR33/TDR29 actually *interpret* B (as actor or as system) rather than blindly type-checking it.

# Actions missed
Short answer: **yes — and in fact essentially *all* of the primary action verbs in that file are outside the vocabularies of rules 27–37.** Rules 27–37 only fire on small, closed verb sets built around IT-style operations (data input/output, CRUD, validation, conditional/control flow). The pet-shop text is full of business-level verbs (`offer`, `provide`, `sell`, `train`, …) that appear in none of those sets, so none of the 15 use cases would produce an action detection from these rules.

## Why

The verb whitelists are strict string `equalsIgnoreCase` checks, so the action verb (A of the matched dependency) must literally be one of:

| Rule | Verb set |
|---|---|
| TDR27 | input, enter, fill, click, select, add, record, process, validate |
| TDR28 | display, output, retrieve, show, view, print |
| TDR29 | get, send, prepare |
| TDR30 | inputted, entered, filled, clicked, selected, added, recorded, processed, validated |
| TDR31 | displayed, outputted, retrieved, showed, viewed, printed |
| TDR32 | input, enter, fill, click, select, add, submit, choose · display, output, retrieve, show, view, print, calculate, process, update, delete, search, modify, edit, remove |
| TDR33 | receive, accept, get, obtain, acquire, redeem |
| TDR34 | (exception terms) error, fail, wrong, invalid, incorrect, not |
| TDR35 | (keywords) if, then, else |
| TDR36 | validate |
| TDR37 | continue, restart, go, repeat |

Now scanning the file: **not a single one** of those verbs occurs in the pet-shop text (no `enter`, `select`, `process`, `display`, `get`, `validate`, `if/then/else`, `go`/`repeat`, etc.).

## Per-use-case audit of the main actions

| # | Use case | Action verb(s) in text | Detected? |
|---|---|---|---|
| 1 | Selling pet products | sell / offer | ❌ |
| 2 | Grooming services | provide (grooming, bathing, haircuts, nail trimming) | ❌ |
| 3 | Pet training | offer, train, help | ❌ |
| 4 | Pet adoption | facilitate, connect | ❌ |
| 5 | Veterinary services | have, provide | ❌ |
| 6 | Pet boarding | offer, care | ❌ |
| 7 | Pet daycare | provide, allow, leave | ❌ |
| 8 | Online pet store | operate, allow, purchase, deliver | ❌ |
| 9 | Accessories & fashion | offer | ❌ |
| 10 | Health & wellness | provide, maintain | ❌ |
| 11 | Pet photography | offer, capture | ❌ |
| 12 | Pet socialization | organize, interact | ❌ |
| 13 | Events & workshops | host, attract, provide | ❌ |
| 14 | Pet insurance | partner, offer | ❌ |
| 15 | Pet-themed merchandise | sell | ❌ |

Also note: even the *nominals* used to name these use cases (grooming, boarding, daycare, adoption, training, photography, insurance, delivery) are services/entity concepts — there is no "Service" category in rules 27–37 at all, so they'd only be picked up (if at all) as entities/attributes by rules 1–13, never as actions.

## Caveats

- Only the *verb lemma* matters for detection; however, even if a verb were present, some sentences here would still be risky due to grammatical form — e.g., modal constructions ("can offer", "may offer"), gerunds ("Providing daycare…", "Hosting events…", "Operating an online store"), passives ("have them delivered"), and periphrastic verbs ("allow … to leave", "help owners train"). TDR30/31 only handle past participles, and TDR27/28/32 mostly expect finite present-tense heads, so modal + infinitive (`can offer`) or `xcomp` chains wouldn't reliably match even for covered verbs.
- TDR34/TDR35 can't help here — no exception terms and no if/then/else markers appear.

## Bottom line

Rules 27–37 would detect **zero** of the actions described in this file. To capture them you'd need to extend the vocabularies (e.g., add `offer`, `provide`, `sell`, `purchase`, `deliver`, `train`, `groom`, `board`, `adopt`, `organize`, `host`, `capture`, `care`, `maintain`), and/or introduce a new output category such as `Service` for nouns like grooming/boarding/daycare — plus handle the modal/gerund grammatical patterns those sentences use.

# Suggested to fix "Bottom Line" findings
I'll take a quick look at how rule matches and candidate types are consumed downstream, so the remedy classes fit the existing architecture.Here's a concrete, additive remedy. The idea mirrors what you already did for basic attributes: instead of hardcoding more verb lists into rules 27–37, introduce an **extendable `ActionCatalog`** (like `BasicAttributeCatalog`) that seeds `TypedDependencyPredicates` with business-action vocabularies, plus **two new rules** that consume them:

- **TDR38** – detects *any* registered business/customer action verb (`offer`, `provide`, `sell`, `train`, `purchase`, …) on `nsubj`/`dobj`/`xcomp`/… and records it as `User_Action` or `System_Actions`, with the actor (subject) captured in the related-candidate fields.
- **TDR39** – detects *service concepts* (`grooming`, `boarding`, `daycare`, `adoption`, `insurance`, `photography`, …) appearing as objects or compounds, recorded as `Service`.

Both vocabularies can be extended at deployment time via properties, so the next `PetShop`-style corpus doesn't require recompiling.

---

## 1. New file: `ActionCatalog.java`

```java
package domox.dom.rules;

import domox.dom.nlp.TypedDependencyPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Vocabulary of business-level action verbs and service concepts that the
 * closed sets of TDR27-TDR37 never cover ("offer", "provide", "sell",
 * "train", "grooming", "boarding", "daycare", ...).
 *
 * <p>Extendable at deployment time through the
 * {@code domox.nlp.action-verbs} and {@code domox.nlp.service-nouns}
 * configuration properties, mirroring {@link BasicAttributeCatalog}.</p>
 */
@Service
public class ActionCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(ActionCatalog.class);

    /** Verbs typically performed by the business / system side. */
    private static final Set<String> DEFAULT_BUSINESS_VERBS = Set.of(
            "offer", "provide", "sell", "deliver", "train", "groom", "board",
            "organize", "host", "maintain", "facilitate", "connect", "partner",
            "operate", "care", "capture", "attract", "allow", "leave", "interact",
            "run", "manage", "supply", "serve", "promote");

    /** Verbs typically performed by the customer / external actor. */
    private static final Set<String> DEFAULT_CUSTOMER_VERBS = Set.of(
            "purchase", "buy", "adopt", "order", "book", "request", "enroll");

    /** Service concepts (often gerunds or plain nouns) offered as products. */
    private static final Set<String> DEFAULT_SERVICE_NOUNS = Set.of(
            "grooming", "boarding", "daycare", "adoption", "training",
            "photography", "insurance", "delivery", "socialization",
            "fashion", "workshop", "event", "class", "service");

    private final Set<String> businessVerbs = new HashSet<>();
    private final Set<String> customerVerbs = new HashSet<>();
    private final Set<String> serviceNouns = new HashSet<>();

    public ActionCatalog(
            @Value("${domox.nlp.action-verbs:}") List<String> configuredActionVerbs,
            @Value("${domox.nlp.service-nouns:}") List<String> configuredServiceNouns) {
        businessVerbs.addAll(DEFAULT_BUSINESS_VERBS);
        customerVerbs.addAll(DEFAULT_CUSTOMER_VERBS);
        serviceNouns.addAll(DEFAULT_SERVICE_NOUNS);

        addAll(businessVerbs, configuredActionVerbs);
        addAll(customerVerbs, configuredActionVerbs);
        addAll(serviceNouns, configuredServiceNouns);

        TypedDependencyPredicates.registerActionVerbs(businessVerbs);
        TypedDependencyPredicates.registerActionVerbs(customerVerbs);
        TypedDependencyPredicates.registerServiceNouns(serviceNouns);

        LOG.info("ActionCatalog initialised: {} business verbs, {} customer verbs, {} service nouns",
                businessVerbs.size(), customerVerbs.size(), serviceNouns.size());
    }

    public boolean isBusinessVerb(String lemma) {
        return lemma != null && businessVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isCustomerVerb(String lemma) {
        return lemma != null && customerVerbs.contains(lemma.toLowerCase(Locale.ROOT));
    }

    public boolean isActionVerb(String lemma) {
        return isBusinessVerb(lemma) || isCustomerVerb(lemma);
    }

    public boolean isServiceNoun(String lemma) {
        return lemma != null && serviceNouns.contains(lemma.toLowerCase(Locale.ROOT));
    }

    private static void addAll(Set<String> target, List<String> configured) {
        if (configured != null) {
            configured.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .forEach(target::add);
        }
    }
}
```


---

## 2. Modified: `TypedDependencyPredicates.java`

Add the `Locale` import:

```java
package domox.dom.nlp;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
// ... existing code ...
```


Add the action-vocabulary region right after the `BASIC_ATTRIB` region:

```java
// ... existing code ...
        public static boolean isBasicAttributeB(TypedDependency td) {
            return td.getB() != null && BASIC_ATTRIB.contains(td.getB());
        }
        // end region

        //region ACTION_VERBS & SERVICE_NOUNS
        private static final Set<String> ACTION_VERBS = new HashSet<>(Set.of(
                "offer", "provide", "sell", "purchase", "buy", "adopt", "order",
                "deliver", "train", "groom", "board", "organize", "host",
                "maintain", "capture", "facilitate", "connect", "partner",
                "operate", "care", "attract", "allow", "leave", "book"));

        private static final Set<String> SERVICE_NOUNS = new HashSet<>(Set.of(
                "grooming", "boarding", "daycare", "adoption", "training",
                "photography", "insurance", "delivery", "socialization",
                "fashion", "workshop", "event", "class", "service"));

        /** Reset to the default action vocabularies (for testing purposes). */
        public static void resetActionVocabularies() {
            ACTION_VERBS.clear();
            ACTION_VERBS.addAll(Set.of(
                    "offer", "provide", "sell", "purchase", "buy", "adopt", "order",
                    "deliver", "train", "groom", "board", "organize", "host",
                    "maintain", "capture", "facilitate", "connect", "partner",
                    "operate", "care", "attract", "allow", "leave", "book"));
            SERVICE_NOUNS.clear();
            SERVICE_NOUNS.addAll(Set.of(
                    "grooming", "boarding", "daycare", "adoption", "training",
                    "photography", "insurance", "delivery", "socialization",
                    "fashion", "workshop", "event", "class", "service"));
        }

        public static void registerActionVerbs(Collection<String> verbs) {
            if (verbs != null) {
                verbs.forEach(v -> {
                    if (v != null) ACTION_VERBS.add(v.toLowerCase(Locale.ROOT));
                });
            }
        }

        public static void registerServiceNouns(Collection<String> nouns) {
            if (nouns != null) {
                nouns.forEach(n -> {
                    if (n != null) SERVICE_NOUNS.add(n.toLowerCase(Locale.ROOT));
                });
            }
        }

        public static boolean isActionVerbA(TypedDependency td) {
            return td.getA() != null && ACTION_VERBS.contains(td.getA().toLowerCase(Locale.ROOT));
        }

        public static boolean isActionVerbB(TypedDependency td) {
            return td.getB() != null && ACTION_VERBS.contains(td.getB().toLowerCase(Locale.ROOT));
        }

        public static boolean isServiceNounA(TypedDependency td) {
            return td.getA() != null && SERVICE_NOUNS.contains(td.getA().toLowerCase(Locale.ROOT));
        }

        public static boolean isServiceNounB(TypedDependency td) {
            return td.getB() != null && SERVICE_NOUNS.contains(td.getB().toLowerCase(Locale.ROOT));
        }
        // endregion

        public static boolean isNounA(TypedDependency td) {}
// ... existing code ...
```


---

## 3. New file: `TDR38.java`

```java
package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import java.util.Locale;
import java.util.Set;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR38 — Business Action Detection.
 *
 * <p>Remedy for the "bottom line" gap of TDR27-TDR37: those rules only match
 * closed, IT-flavoured verb sets (input/output/validate/...), so business-level
 * actions such as "offer", "provide", "sell" or "train" are never detected.</p>
 *
 * <p>This rule fires on any dependency whose governor (A) is a verb from the
 * registered action vocabulary (see {@link ActionCatalog}, extendable via
 * {@code domox.nlp.action-verbs}). When the dependency type exposes the
 * actor (subject/agent) as B, it is recorded as the related candidate.</p>
 */
@RuleBean
@Rule(order = 38)
public class TDR38 extends TypedDependencyRule {

    /** Actors on the customer side; everything else is treated as business/system. */
    private static final Set<String> CUSTOMER_ACTORS = Set.of(
            "user", "customer", "owner", "person", "people", "shopper",
            "client", "buyer", "visitor");

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Dependencies that expose a verb as the governor (A)
        if (!(isNsubj(currentTd) || isNsubjPass(currentTd) || dobj(currentTd) ||
                iobj(currentTd) || pobj(currentTd) || xcomp(currentTd) ||
                nmodWith(currentTd) || nmodTo(currentTd) || nmodFor(currentTd))) {
            return false;
        }
        return isVerbA(currentTd) && isActionVerbA(currentTd);
    }

    @Override
    @Then
    public void then() {
        String verb = currentTd.getA();
        String actor = currentTd.getB();

        boolean customerAction = actor != null &&
                CUSTOMER_ACTORS.contains(actor.toLowerCase(Locale.ROOT));
        String candidateType = customerAction ? "User_Action" : "System_Actions";
        result = candidateType + ".add(" + verb + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    candidateType,
                    capitalizeFirstLetter(verb),
                    // Actor (subject/agent) when the dependency exposes one
                    actor != null ? "Actor" : null,
                    actor != null ? capitalizeFirstLetter(actor) : null,
                    result);
        }
    }
}
```


---

## 4. New file: `TDR39.java`

```java
package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR39 — Service Concept Detection.
 *
 * <p>Use cases such as "grooming services", "boarding", "daycare",
 * "adoption", "insurance" or "photography" are services rather than CRUD
 * actions. This rule records them as candidate type "Service" whenever the
 * service term appears as an object or as part of a compound noun.</p>
 */
@RuleBean
@Rule(order = 39)
public class TDR39 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        if (!(dobj(currentTd) || iobj(currentTd) || pobj(currentTd) ||
                isCompound(currentTd) || nmodOf(currentTd) || nmodIn(currentTd) ||
                nmodTo(currentTd) || nmodFor(currentTd) || nmodWith(currentTd))) {
            return false;
        }
        return isServiceNounA(currentTd) || isServiceNounB(currentTd);
    }

    @Override
    @Then
    public void then() {
        String service = isServiceNounB(currentTd) ? currentTd.getB() : currentTd.getA();
        result = "Services.add(" + service + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "Service",
                    capitalizeFirstLetter(service),
                    null,
                    null,
                    result);
        }
    }
}
```


---

## 5. Configuration (optional, no recompile needed)

Add to your application properties (e.g. `domox-domain/src/main/resources`):

```properties
# extra action verbs for TDR38 (space- or comma-separated)
domox.nlp.action-verbs=bath, clip, vaccinate, microchip, adopt

# extra service nouns for TDR39
domox.nlp.service-nouns=bathing, haircut, nail-trimming, dental-care, consultation
```


## How this addresses the finding

| Pet-shop action | Rule | Result |
|---|---|---|
| "A pet shop can **offer**…" (`nsubj(offer, shop)`) | TDR38 | `System_Actions.add(offer)` |
| "**provide** grooming services" / "**sell** products" | TDR38 | `System_Actions.add(provide)` / `.add(sell)` |
| "customers … **purchase** pet products" | TDR38 | `User_Action.add(purchase)` (actor = customer) |
| "**train** their pets" / "**facilitate** adoptions" | TDR38 | action detected via vocabulary |
| "grooming **services**", "pet **daycare**", "**boarding**", "**adoption**", "**insurance**", "**photography**" | TDR39 | `Services.add(grooming)` etc. (compound/object head) |
| "**hosting** events" (gerund) | TDR38/TDR39 | verb `host` → action; noun `event` → service |

A couple of notes:

- **Actor/subject info** – TDR38 stores the subject/agent (`nsubj`/`nsubjpass` B) in `relatedCandidateName`, since `RuleMatch` supports it but rules 27–37 never used it. TDR38 records the *object* when firing on `dobj` (B is the direct object then).
- **Unknown candidate types** (`User_Action`, `System_Actions`, `Service`, …) are stored as `RuleMatch` rows and visible in the UI via `listAll()`, exactly like the existing categories — `createCandidateFromMatch` simply doesn't promote them to `ClassCdd`/`PropertyCdd` yet; you can extend that dispatch later if you want a `Service`/`Action` domain object.
- **Tests** – follow the `TypedDependencyPredicatesTest` pattern: add `@BeforeEach` calls to `TypedDependencyPredicates.resetActionVocabularies()` (or a test-side registration), then assert e.g. `isActionVerbA(dependency("offer", "shop"))` and `isServiceNounB(dependency("services", "grooming"))`.