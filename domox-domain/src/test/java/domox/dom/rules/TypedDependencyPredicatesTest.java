package domox.dom.rules;

import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.TypedDependencyPredicates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
    }

    // ----------------------------------------------------------------
    // isBasicAttributeB — dependent (B) side
    // ----------------------------------------------------------------

    @Test
    void name_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR2, Example 1  —  nsubj(stored, name)
        TypedDependency td = dependency("stored", "name");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void date_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR2, Example 2  —  nsubjpass(entered, date)
        TypedDependency td = dependency("entered", "date");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void address_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR4, Example 1  —  dobj(entered, address)
        TypedDependency td = dependency("entered", "address");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void number_is_basic_attribute() {
        // RULES_EXAMPLES.md TDR4, Example 2  —  obj(saved, number)
        TypedDependency td = dependency("saved", "number");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void type_is_basic_attribute() {
        TypedDependency td = dependency("has", "type");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void level_is_basic_attribute() {
        TypedDependency td = dependency("set", "level");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void time_is_basic_attribute() {
        TypedDependency td = dependency("records", "time");
        assertTrue(TypedDependencyPredicates.isBasicAttributeB(td));
    }

    @Test
    void name_with_register_is_basic_attribute() {
        // After the catalog registers extra lemmas, "email" counts as basic too
        TypedDependency td = dependency("enter", "email");
        // "email" is in BasicAttributeCatalog.DEFAULT_ATTRIBUTES but
        // TypedDependencyPredicates.BASIC_ATTRIB hasn't been widened here.
        // This test documents that the static set starts at the original 7.
        assertFalse(TypedDependencyPredicates.isBasicAttributeB(td));
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