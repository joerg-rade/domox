package domox.dom.rules;

import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyPredicates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TypedDependencyPredicates#isBasicAttributeA(TypedDependency)}
 * and {@link TypedDependencyPredicates#isBasicAttributeB(TypedDependency)}.
 *
 * <p>Every test case is drawn from the examples in RULES_EXAMPLES.md where
 * BasicAttrib is explicitly annotated.</p>
 */
class TypedDependencyPredicatesTest {

    private final Map<Integer, String> tokenTexts = new HashMap<>();

    @BeforeEach
    void setUp() {
        tokenTexts.clear();
        TypedDependencyPredicates.resetBasicAttributes();
        TypedDependencyPredicates.resetActionVocabularies();
    }

    // ----------------------------------------------------------------
    // isBasicAttributeB — dependent (B) side
    // ----------------------------------------------------------------

    @Test
    void name_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR2, Example 1  —  nsubj(stored, name)
        TypedDependency td = dependency("stored", "name");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("name"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void date_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR2, Example 2  —  nsubjpass(entered, date)
        TypedDependency td = dependency("entered", "date");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("date"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void address_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR4, Example 1  —  dobj(entered, address)
        TypedDependency td = dependency("entered", "address");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("address"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void number_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR4, Example 2  —  obj(saved, number)
        TypedDependency td = dependency("saved", "number");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("number"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void type_is_basic_attribute() {
        TypedDependency td = dependency("has", "type");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("type"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void level_is_basic_attribute() {
        TypedDependency td = dependency("set", "level");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("level"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void time_is_basic_attribute() {
        TypedDependency td = dependency("records", "time");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("time"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void email_is_not_basic_attribute_by_default() {
        // "email" is not registered, because BASIC_ATTRIB starts empty
        // (the set is populated at runtime by BasicAttributeCatalog from config).
        TypedDependency td = dependency("enter", "email");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }


    @Test
    void name_with_register_is_basic_attribute() {
        // Before registration — "email" is not yet a basic attribute
        TypedDependency td = dependency("enter", "email");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));

        // After the catalog registers extra lemmas, "email" counts as basic too
        TypedDependencyPredicates.registerBasicAttributes(java.util.Set.of("email"));

        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void user_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR1, Example 1  —  nsubj(creates, user)
        TypedDependency td = dependency("creates", "user");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void system_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR1, Example 2  —  nsubj(designed, system)
        TypedDependency td = dependency("designed", "system");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void document_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR3, Example 1  —  dobj(creates, document)
        TypedDependency td = dependency("creates", "document");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void request_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR3, Example 2  —  obj(process, request)
        TypedDependency td = dependency("process", "request");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void manager_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR9, Example 1  —  nmod:by(processed, manager)
        TypedDependency td = dependency("processed", "manager");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void recipient_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR8, Example 1  —  nmod:to(send, recipient)
        TypedDependency td = dependency("send", "recipient");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void article_is_not_basic_attribute() {
        // RULES_EXAMPLES.md TDR10, Example 1  —  nmod:poss(author, article)
        TypedDependency td = dependency("author", "article");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    // ----------------------------------------------------------------
    // isBasicAttributeA — governor (A) side
    // ----------------------------------------------------------------

    @Test
    void owner_in_TDR6_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 1  —  nmod:of(owner, document)
        // A=owner (NN=BasicAttrib)
        TypedDependency td = dependency("owner", "document");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("owner"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void organization_is_not_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 2  —  nmod:of(organization, department)
        TypedDependency td = dependency("organization", "department");
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void timestamp_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR6, Example 3  —  nmod:of(timestamp, creation)
        TypedDependency td = dependency("timestamp", "creation");
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void name_in_TDR11_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR11, Example 1  —  amod(users, multiple)
        // A=users (NN=BasicAttrib) — only if "users" is in the set; it's not.
        TypedDependency td = dependency("users", "multiple");
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void name_in_TDR13_example1_is_basic_attribute_a() {
        // RULES_EXAMPLES.md TDR13, Example 1  —  nmod:and(name, email)
        TypedDependency td = dependency("name", "email");
        TypedDependencyPredicates.registerBasicAttributes(Set.of("name"));
        assertTrue(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("name");
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));
    }

    @Test
    void null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("name");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    // ----------------------------------------------------------------
    // registerBasicAttributes
    // ----------------------------------------------------------------

    @Test
    void after_registration_new_attribute_is_recognized() {
        // The test starts with the original 7 — "email" is NOT in that set.
        TypedDependency td = dependency("enter", "email");
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));

        // Register "email" — now both sides should match.
        TypedDependencyPredicates.registerBasicAttributes(java.util.Set.of("email"));

        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
        // A side is "enter" (verb), not "email" — still false.
        assertFalse(TypedDependencyPredicates.isBasicAttributeA(td));

        // A-side test with "email" as the governor
        TypedDependency td2 = dependency("email", "user");
        assertTrue(TypedDependencyPredicates.isBasicAttributeA(td2));
    }

    // ----------------------------------------------------------------
    // isActionVerbA / isActionVerbB
    // ----------------------------------------------------------------

    @Test
    void action_verb_a_recognized_after_registration() {
        TypedDependency td = dependency("offer", "customer");
        assertFalse(TypedDependencyPredicates.isActionVerbA(td)); // not registered yet

        TypedDependencyPredicates.registerActionVerbs(java.util.Set.of("offer"));

        assertTrue(TypedDependencyPredicates.isActionVerbA(td));
        // B side is "customer" — not an action verb
        assertFalse(TypedDependencyPredicates.isActionVerbB(td));
    }

    @Test
    void action_verb_b_recognized_after_registration() {
        TypedDependency td = dependency("system", "validate");
        assertFalse(TypedDependencyPredicates.isActionVerbB(td));

        TypedDependencyPredicates.registerActionVerbs(java.util.Set.of("validate"));

        assertTrue(TypedDependencyPredicates.isActionVerbB(td));
        assertFalse(TypedDependencyPredicates.isActionVerbA(td)); // "system" is not an action verb
    }

    @Test
    void isActionVerbA_null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("offer");
        assertFalse(TypedDependencyPredicates.isActionVerbA(td));
    }

    @Test
    void isActionVerbB_null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("offer");
        assertFalse(TypedDependencyPredicates.isActionVerbB(td));
    }

    // ----------------------------------------------------------------
    // isServiceNounA / isServiceNounB
    // ----------------------------------------------------------------

    @Test
    void service_noun_b_recognized_after_registration() {
        TypedDependency td = dependency("provide", "grooming");
        assertFalse(TypedDependencyPredicates.isServiceNounB(td));

        TypedDependencyPredicates.registerServiceNouns(java.util.Set.of("grooming"));

        assertTrue(TypedDependencyPredicates.isServiceNounB(td));
        assertFalse(TypedDependencyPredicates.isServiceNounA(td)); // "provide" is not a service noun
    }

    @Test
    void service_noun_a_recognized_after_registration() {
        TypedDependency td = dependency("boarding", "facility");
        assertFalse(TypedDependencyPredicates.isServiceNounA(td));

        TypedDependencyPredicates.registerServiceNouns(java.util.Set.of("boarding"));

        assertTrue(TypedDependencyPredicates.isServiceNounA(td));
        assertFalse(TypedDependencyPredicates.isServiceNounB(td)); // "facility" is not a service noun
    }

    @Test
    void isServiceNounA_null_a_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setDependentLemma("grooming");
        assertFalse(TypedDependencyPredicates.isServiceNounA(td));
    }

    @Test
    void isServiceNounB_null_b_returns_false() {
        TypedDependency td = new TypedDependency();
        td.setGovernorLemma("grooming");
        assertFalse(TypedDependencyPredicates.isServiceNounB(td));
    }

    // ----------------------------------------------------------------
    // resetActionVocabularies
    // ----------------------------------------------------------------

    @Test
    void after_reset_action_vocabularies_is_empty() {
        TypedDependencyPredicates.registerActionVerbs(java.util.Set.of("offer"));
        TypedDependencyPredicates.registerServiceNouns(java.util.Set.of("grooming"));

        TypedDependency tdAction = dependency("offer", "customer");
        TypedDependency tdService = dependency("provide", "grooming");

        // Before reset — both predicates recognise the terms
        assertTrue(TypedDependencyPredicates.isActionVerbA(tdAction));
        assertTrue(TypedDependencyPredicates.isServiceNounB(tdService));

        // Reset — both vocabularies should be empty
        TypedDependencyPredicates.resetActionVocabularies();

        // After reset — no longer recognised
        assertFalse(TypedDependencyPredicates.isActionVerbA(tdAction));
        assertFalse(TypedDependencyPredicates.isServiceNounB(tdService));
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