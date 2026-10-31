package domox.dom.crc.model;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@code ruleMatches} list on every candidate type
 * (ClassCdd, ResponsibilityCdd, AttributeCdd, CollaboratorCdd) is de-duplicated by the
 * same candidate + rule signature used when {@code RuleMatches} persists
 * matches, so the UI never shows two equivalent matches for one candidate.
 */
class CandidateTest {

    // --- ClassCdd (inherits addMatchingRule / deduplicateRuleMatches) ---

    @Test
    void addMatchingRule_keepsSingleInstanceForSameSignature() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final RuleMatch first = match("ClassCdd", "Customer", "TDR10", null, null, "an actor");
        final RuleMatch second = match("ClassCdd", "Customer", "TDR10", null, null, "an actor");

        // when — two distinct RuleMatch records with an identical signature
        candidate.addMatchingRule(first);
        candidate.addMatchingRule(second);

        // then — only one rule match is kept
        assertEquals(1, candidate.getRuleMatches().size());
        assertTrue(candidate.getRuleMatches().contains(first));
    }

    @Test
    void addMatchingRule_addsDistinctSignatures() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final RuleMatch first = match("ClassCdd", "Customer", "TDR10", "Order", null, "places");
        final RuleMatch second = match("ClassCdd", "Customer", "TDR15", "Order", null, "owns");

        // when — different ruleClassName (different rule firing)
        candidate.addMatchingRule(first);
        candidate.addMatchingRule(second);

        // then — both are kept
        assertEquals(2, candidate.getRuleMatches().size());
    }

    @Test
    void addMatchingRule_collapsesLegacyDuplicateMatchesAlreadyAttached() {
        // given — a candidate already carrying two signature-equal matches
        // (the pre-dedup legacy state) plus one genuinely distinct match
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final RuleMatch legacyA = match("ClassCdd", "Customer", "TDR10", null, null, null);
        final RuleMatch legacyB = match("ClassCdd", "Customer", "TDR10", null, null, null);
        final RuleMatch distinct = match("ClassCdd", "Customer", "TDR15", null, null, null);
        candidate.getRuleMatches().add(legacyA);
        candidate.getRuleMatches().add(legacyB);
        candidate.getRuleMatches().add(distinct);

        // when — a further match is added
        candidate.addMatchingRule(distinct);

        // then — legacy duplicates are collapsed, distinct ones remain
        assertEquals(2, candidate.getRuleMatches().size());
        assertTrue(candidate.getRuleMatches().contains(legacyA));
        assertTrue(candidate.getRuleMatches().contains(distinct));
    }

    @Test
    void deduplicateRuleMatches_collapsesDuplicateSignatures() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final RuleMatch a = match("ClassCdd", "Customer", "TDR10", null, null, "text");
        final RuleMatch b = match("ClassCdd", "Customer", "TDR10", null, null, "text");
        final RuleMatch c = match("ClassCdd", "Order", "TDR10", null, null, "text");
        candidate.getRuleMatches().add(a);
        candidate.getRuleMatches().add(b);
        candidate.getRuleMatches().add(c);

        // when
        candidate.deduplicateRuleMatches();

        // then — duplicate signature collapsed, distinct kept
        assertEquals(2, candidate.getRuleMatches().size());
        assertTrue(candidate.getRuleMatches().contains(a));
        assertTrue(candidate.getRuleMatches().contains(c));
    }

    // --- all candidate types share the same de-duplication ---

    @Test
    void addMatchingRule_deduplicatesAcrossAllCandidateTypes() {
        // ClassCdd
        final ClassCdd clazz = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        clazz.addMatchingRule(match("ClassCdd", "Customer", "TDR10", null, null, null));
        clazz.addMatchingRule(match("ClassCdd", "Customer", "TDR10", null, null, null));
        assertEquals(1, clazz.getRuleMatches().size(), "ClassCdd");

        // ResponsibilityCdd
        final ResponsibilityCdd action = new ResponsibilityCdd("process", new ArrayList<>(), "void");
        action.addMatchingRule(match("ActionCdd", "process", "TDR12", "Customer", null, null));
        action.addMatchingRule(match("ActionCdd", "process", "TDR12", "Customer", null, null));
        assertEquals(1, action.getRuleMatches().size(), "ActionCdd");

        // AttributeCdd
        final AttributeCdd property = new AttributeCdd("name", "String");
        property.addMatchingRule(match("PropertyCdd", "name", "TDR10", "Customer", null, null));
        property.addMatchingRule(match("PropertyCdd", "name", "TDR10", "Customer", null, null));
        assertEquals(1, property.getRuleMatches().size(), "PropertyCdd");

        // CollaboratorCdd
        final ClassCdd source = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ClassCdd target = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final CollaboratorCdd association = new CollaboratorCdd("places", source, target);
        association.addMatchingRule(match("AssociationCdd", "places", "TDR24", "Customer", "Order", null));
        association.addMatchingRule(match("AssociationCdd", "places", "TDR24", "Customer", "Order", null));
        assertEquals(1, association.getRuleMatches().size(), "AssociationCdd");
    }

    @Test
    void addMatchingRule_ignoresNull() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // when
        candidate.addMatchingRule(null);

        // then
        assertTrue(candidate.getRuleMatches().isEmpty());
    }

    // -- object type (CRC rename guardrail)

    /**
     * The CRC rename changed the Java class names but pinned {@code @Named} to the historical
     * values.  The D3/Vega diagram JSON exposes {@code objectType} so the client can build a
     * {@code /wicket/object/<objectType>:<id>} click-through link, so it must come from
     * {@link Candidate#getObjectType()} (i.e. {@code @Named}) and never from
     * {@code getClass().getSimpleName()}.
     */
    @Test
    void getObjectType_usesPinnedNamedValue_notTheRenamedClassName() {
        // given — class names follow CRC vocabulary, @Named keeps the historical names
        final ClassCdd classCdd = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ResponsibilityCdd responsibility = new ResponsibilityCdd();
        final AttributeCdd attribute = new AttributeCdd("name", "String");
        final CollaboratorCdd collaborator = new CollaboratorCdd("Customer_Order", classCdd, classCdd);
        final ResponsibilityParameter parameter = new ResponsibilityParameter("qty", "int");

        // when / then — objectType is the persisted (pinned) Causeway object type
        assertEquals("domox.ClassCdd", classCdd.getObjectType());
        assertEquals("domox.ActionCdd", responsibility.getObjectType());
        assertEquals("domox.PropertyCdd", attribute.getObjectType());
        assertEquals("domox.AssociationCdd", collaborator.getObjectType());
        assertEquals("domox.ParameterCdd", parameter.getObjectType());

        // and it is NOT derived from the (renamed) simple class name
        assertEquals("ResponsibilityCdd", responsibility.getClass().getSimpleName());
        assertTrue(!"domox.ResponsibilityCdd".equals(responsibility.getObjectType()));
    }

    // -- helper

    private static RuleMatch match(String candidateType, String candidateName, String ruleClassName,
                                   String relatedType, String relatedName, String description) {
        final RuleMatch match = new RuleMatch();
        match.setCandidateType(candidateType);
        match.setCandidateName(candidateName);
        match.setRuleClassName(ruleClassName);
        match.setRelatedCandidateType(relatedType);
        match.setRelatedCandidateName(relatedName);
        match.setDescription(description);
        return match;
    }
}