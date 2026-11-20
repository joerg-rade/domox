package domox.dom.crc.model;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(candidate.getRuleMatches()).hasSize(1);
        assertThat(candidate.getRuleMatches()).contains(first);
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
        assertThat(candidate.getRuleMatches()).hasSize(2);
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
        assertThat(candidate.getRuleMatches()).hasSize(2);
        assertThat(candidate.getRuleMatches()).contains(legacyA);
        assertThat(candidate.getRuleMatches()).contains(distinct);
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
        assertThat(candidate.getRuleMatches()).hasSize(2);
        assertThat(candidate.getRuleMatches()).contains(a);
        assertThat(candidate.getRuleMatches()).contains(c);
    }

    // --- all candidate types share the same de-duplication ---

    @Test
    void addMatchingRule_deduplicatesAcrossAllCandidateTypes() {
        // ClassCdd
        final ClassCdd clazz = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        clazz.addMatchingRule(match("ClassCdd", "Customer", "TDR10", null, null, null));
        clazz.addMatchingRule(match("ClassCdd", "Customer", "TDR10", null, null, null));
        assertThat(clazz.getRuleMatches()).as("ClassCdd").hasSize(1);

        // ResponsibilityCdd
        final ResponsibilityCdd action = new ResponsibilityCdd("process", new ArrayList<>(), "void");
        action.addMatchingRule(match("ResponsibilityCdd", "process", "TDR12", "Customer", null, null));
        action.addMatchingRule(match("ResponsibilityCdd", "process", "TDR12", "Customer", null, null));
        assertThat(action.getRuleMatches()).as("ResponsibilityCdd").hasSize(1);

        // AttributeCdd
        final AttributeCdd property = new AttributeCdd("name", "String");
        property.addMatchingRule(match("AttributeCdd", "name", "TDR10", "Customer", null, null));
        property.addMatchingRule(match("AttributeCdd", "name", "TDR10", "Customer", null, null));
        assertThat(property.getRuleMatches()).as("AttributeCdd").hasSize(1);

        // CollaboratorCdd
        final ClassCdd source = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ClassCdd target = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final CollaboratorCdd association = new CollaboratorCdd("places", source, target);
        association.addMatchingRule(match("CollaboratorCdd", "places", "TDR24", "Customer", "Order", null));
        association.addMatchingRule(match("CollaboratorCdd", "places", "TDR24", "Customer", "Order", null));
        assertThat(association.getRuleMatches()).as("CollaboratorCdd").hasSize(1);
    }

    @Test
    void addMatchingRule_ignoresNull() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // when
        candidate.addMatchingRule(null);

        // then
        assertThat(candidate.getRuleMatches()).isEmpty();
    }

    // -- object type (Causeway object type guardrail)

    /**
     * {@code objectType} comes from {@code @Named} — the framework's own type — because the
     * D3/Vega diagram JSON exposes it so the client can build a
     * {@code /wicket/object/<objectType>:<id>} click-through link.  Since the 2026-11
     * persisted-name migration (TODO-15) the annotation, the table/entity name and the class
     * name all agree, so the assertion is the identity it always should have been — but the
     * annotation, not {@code getClass().getSimpleName()}, remains the source of truth.
     */
    @Test
    void getObjectType_isTheNamedCausewayObjectType() {
        // given
        final ClassCdd classCdd = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ResponsibilityCdd responsibility = new ResponsibilityCdd();
        final AttributeCdd attribute = new AttributeCdd("name", "String");
        final CollaboratorCdd collaborator = new CollaboratorCdd("Customer_Order", classCdd, classCdd);
        final ResponsibilityParameter parameter = new ResponsibilityParameter("qty", "int");

        // when / then — objectType is the Causeway object type, namespace + simple class name
        assertThat(classCdd.getObjectType()).isEqualTo("domox.ClassCdd");
        assertThat(responsibility.getObjectType()).isEqualTo("domox.ResponsibilityCdd");
        assertThat(attribute.getObjectType()).isEqualTo("domox.AttributeCdd");
        assertThat(collaborator.getObjectType()).isEqualTo("domox.CollaboratorCdd");
        assertThat(parameter.getObjectType()).isEqualTo("domox.ResponsibilityParameter");

        // and it agrees with the class name — the divergence the 2026-10 rename introduced is gone
        assertThat(responsibility.getClass().getSimpleName()).isEqualTo("ResponsibilityCdd");
        assertThat(responsibility.getObjectType()).isEqualTo("domox.ResponsibilityCdd");
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