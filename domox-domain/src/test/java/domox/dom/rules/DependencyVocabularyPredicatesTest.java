package domox.dom.rules;

import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.DependencyVocabularyPredicates;
import domox.dom.nlp.vocab.ActionVocabulary;
import domox.dom.nlp.vocab.BasicAttributeVocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DependencyVocabularyPredicates#isBasicAttributeA(TypedDependency)}
 * and {@link DependencyVocabularyPredicates#isBasicAttributeB(TypedDependency)}.
 *
 * <p>Every test case is drawn from the examples in RULES_EXAMPLES.md where
 * BasicAttrib is explicitly annotated.</p>
 */
class DependencyVocabularyPredicatesTest {

    private final Map<Integer, String> tokenTexts = new HashMap<>();

    @BeforeEach
    void setUp() {
        tokenTexts.clear();
        BasicAttributeVocabulary.reset();
        ActionVocabulary.reset();
    }

    // ----------------------------------------------------------------
    // isBasicAttributeB — dependent (B) side
    // ----------------------------------------------------------------

    /**
     * Every registered lemma counts as a basic attribute on the dependent (B) side.  The cases are
     * drawn from the {@code BasicAttrib}-annotated examples in RULES_EXAMPLES.md (TDR2, TDR4, …).
     */
    @ParameterizedTest
    @CsvSource({
            "stored,  name",
            "entered, date",
            "entered, address",
            "saved,   number",
            "has,     type",
            "set,     level",
            "records, time"
    })
    void isBasicAttributeB_recognisesRegisteredAttributes(final String governor, final String attribute) {
        final TypedDependency td = dependency(governor, attribute);
        BasicAttributeVocabulary.register(Set.of(attribute));
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isTrue();
    }

    @Test
    void email_is_not_basic_attribute_by_default() {
        // "email" is not registered, because BASIC_ATTRIB starts empty
        // (the set is populated at runtime by BasicAttributeCatalog from config).
        TypedDependency td = dependency("enter", "email");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }


    @Test
    void name_with_register_is_basic_attribute() {
        // Before registration — "email" is not yet a basic attribute
        TypedDependency td = dependency("enter", "email");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();

        // After the catalog registers extra lemmas, "email" counts as basic too
        BasicAttributeVocabulary.register(java.util.Set.of("email"));

        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isTrue();
    }

    @Test
    void user_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR1, Example 1  —  nsubj(creates, user)
        TypedDependency td = dependency("creates", "user");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void system_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR1, Example 2  —  nsubj(designed, system)
        TypedDependency td = dependency("designed", "system");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void document_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR3, Example 1  —  dobj(creates, document)
        TypedDependency td = dependency("creates", "document");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void request_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR3, Example 2  —  obj(process, request)
        TypedDependency td = dependency("process", "request");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void manager_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR9, Example 1  —  nmod:by(processed, manager)
        TypedDependency td = dependency("processed", "manager");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void recipient_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR8, Example 1  —  nmod:to(send, recipient)
        TypedDependency td = dependency("send", "recipient");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    @Test
    void article_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR10, Example 1  —  nmod:poss(author, article)
        TypedDependency td = dependency("author", "article");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    // ----------------------------------------------------------------
    // isBasicAttributeA — governor (A) side
    // ----------------------------------------------------------------

    @Test
    void owner_in_TDR6_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 1  —  nmod:of(owner, document)
        // A=owner (NN=BasicAttrib)
        TypedDependency td = dependency("owner", "document");
        BasicAttributeVocabulary.register(Set.of("owner"));
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isTrue();
    }

    @Test
    void organization_is_not_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 2  —  nmod:of(organization, department)
        TypedDependency td = dependency("organization", "department");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();
    }

    @Test
    void timestamp_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 3  —  nmod:of(timestamp, creation)
        TypedDependency td = dependency("timestamp", "creation");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();
    }

    @Test
    void name_in_TDR11_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR11, Example 1  —  amod(users, multiple)
        // A=users (NN=BasicAttrib) — only if "users" is in the set; it's not.
        TypedDependency td = dependency("users", "multiple");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();
    }

    @Test
    void name_in_TDR13_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR13, Example 1  —  nmod:and(name, email)
        TypedDependency td = dependency("name", "email");
        BasicAttributeVocabulary.register(Set.of("name"));
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isTrue();
    }

    @Test
    void null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("name");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();
    }

    @Test
    void null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("name");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
    }

    // ----------------------------------------------------------------
    // registerBasicAttributes
    // ----------------------------------------------------------------

    @Test
    void after_registration_new_attribute_is_recognized() {
        // The test starts with the original 7 — "email" is NOT in that set.
        TypedDependency td = dependency("enter", "email");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isFalse();
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();

        // Register "email" — now both sides should match.
        BasicAttributeVocabulary.register(java.util.Set.of("email"));

        assertThat(DependencyVocabularyPredicates.isBasicAttributeB(td)).isTrue();
        // A side is "enter" (verb), not "email" — still false.
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td)).isFalse();

        // A-side test with "email" as the governor
        TypedDependency td2 = dependency("email", "user");
        assertThat(DependencyVocabularyPredicates.isBasicAttributeA(td2)).isTrue();
    }

    // ----------------------------------------------------------------
    // isActionVerbA / isActionVerbB
    // ----------------------------------------------------------------

    @Test
    void action_verb_a_recognized_after_registration() {
        TypedDependency td = dependency("offer", "customer");
        assertThat(DependencyVocabularyPredicates.isActionVerbA(td)).isFalse(); // not registered yet

        ActionVocabulary.registerActionVerbs(java.util.Set.of("offer"));

        assertThat(DependencyVocabularyPredicates.isActionVerbA(td)).isTrue();
        // B side is "customer" — not an action verb
        assertThat(DependencyVocabularyPredicates.isActionVerbB(td)).isFalse();
    }

    @Test
    void action_verb_b_recognized_after_registration() {
        TypedDependency td = dependency("system", "validate");
        assertThat(DependencyVocabularyPredicates.isActionVerbB(td)).isFalse();

        ActionVocabulary.registerActionVerbs(java.util.Set.of("validate"));

        assertThat(DependencyVocabularyPredicates.isActionVerbB(td)).isTrue();
        assertThat(DependencyVocabularyPredicates.isActionVerbA(td)).isFalse(); // "system" is not an action verb
    }

    @Test
    void isActionVerbA_null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("offer");
        assertThat(DependencyVocabularyPredicates.isActionVerbA(td)).isFalse();
    }

    @Test
    void isActionVerbB_null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("offer");
        assertThat(DependencyVocabularyPredicates.isActionVerbB(td)).isFalse();
    }

    // ----------------------------------------------------------------
    // isServiceNounA / isServiceNounB
    // ----------------------------------------------------------------

    @Test
    void service_noun_b_recognized_after_registration() {
        TypedDependency td = dependency("provide", "grooming");
        assertThat(DependencyVocabularyPredicates.isServiceNounB(td)).isFalse();

        ActionVocabulary.registerServiceNouns(java.util.Set.of("grooming"));

        assertThat(DependencyVocabularyPredicates.isServiceNounB(td)).isTrue();
        assertThat(DependencyVocabularyPredicates.isServiceNounA(td)).isFalse(); // "provide" is not a service noun
    }

    @Test
    void service_noun_a_recognized_after_registration() {
        TypedDependency td = dependency("boarding", "facility");
        assertThat(DependencyVocabularyPredicates.isServiceNounA(td)).isFalse();

        ActionVocabulary.registerServiceNouns(java.util.Set.of("boarding"));

        assertThat(DependencyVocabularyPredicates.isServiceNounA(td)).isTrue();
        assertThat(DependencyVocabularyPredicates.isServiceNounB(td)).isFalse(); // "facility" is not a service noun
    }

    @Test
    void isServiceNounA_null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("grooming");
        assertThat(DependencyVocabularyPredicates.isServiceNounA(td)).isFalse();
    }

    @Test
    void isServiceNounB_null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("grooming");
        assertThat(DependencyVocabularyPredicates.isServiceNounB(td)).isFalse();
    }

    // ----------------------------------------------------------------
    // resetActionVocabularies
    // ----------------------------------------------------------------

    @Test
    void after_reset_action_vocabularies_is_empty() {
        ActionVocabulary.registerActionVerbs(java.util.Set.of("offer"));
        ActionVocabulary.registerServiceNouns(java.util.Set.of("grooming"));

        TypedDependency tdAction = dependency("offer", "customer");
        TypedDependency tdService = dependency("provide", "grooming");

        // Before reset — both predicates recognise the terms
        assertThat(DependencyVocabularyPredicates.isActionVerbA(tdAction)).isTrue();
        assertThat(DependencyVocabularyPredicates.isServiceNounB(tdService)).isTrue();

        // Reset — both vocabularies should be empty
        ActionVocabulary.reset();

        // After reset — no longer recognised
        assertThat(DependencyVocabularyPredicates.isActionVerbA(tdAction)).isFalse();
        assertThat(DependencyVocabularyPredicates.isServiceNounB(tdService)).isFalse();
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private TypedDependency dependency(String governor, String dependent) {
        tokenTexts.put(0, governor);
        tokenTexts.put(1, dependent);
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma(governor);
        td.setDependentLemma(dependent);
        return td;
    }
}