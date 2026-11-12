package domox.dom.nlp.vocab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The "basic attribute" vocabulary — generic attribute lemmas that the TDR rules treat as
 * attributes rather than entities (see RULES_EXAMPLES.md, TDR1–TDR13).
 * <p>
 * Owns the registry that previously lived as a static field on
 * {@code TypedDependencyPredicates}.  It is populated from {@code domox.nlp.basic-attributes} by
 * {@code domox.dom.rules.BasicAttributeCatalog}.
 * <p>
 * Registration is <em>verbatim</em> (no case folding) — callers register already-lower-cased
 * lemmas, and the membership test is likewise case-sensitive.
 */
public final class BasicAttributeVocabulary {

    private static final Set<String> BASIC_ATTRIB = new HashSet<>();

    private BasicAttributeVocabulary() {
    }

    /** Reset to the empty set (for testing purposes). */
    public static void reset() {
        BASIC_ATTRIB.clear();
    }

    public static void register(final Collection<String> attributes) {
        if (attributes != null) {
            BASIC_ATTRIB.addAll(attributes);
        }
    }

    /** Verbatim membership test — the caller is responsible for case. */
    public static boolean contains(final String lemma) {
        return lemma != null && BASIC_ATTRIB.contains(lemma);
    }
}
