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

    // -- rationale (the reason an MCP client gives for changing a candidate)

    /**
     * {@code rationale} is the free text an MCP client supplies as the reason for a change, so the
     * entity's job is only to normalise it — the "a reason must be given" rule lives at the MCP
     * boundary ({@code McpReason}), which is what lets the UI edit the same property without one.
     */
    @Test
    void setRationale_trimsAndKeepsTheText() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // when
        candidate.setRationale("   real class: named by TDR10   ");

        // then
        assertThat(candidate.getRationale()).isEqualTo("real class: named by TDR10");
    }

    @Test
    void setRationale_mapsBlankToNull() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // when
        candidate.setRationale("   ");

        // then
        assertThat(candidate.getRationale()).isNull();
    }

    @Test
    void setRationale_truncatesAnOverLongReasonInsteadOfRejectingIt() {
        // given — one character past what the column stores
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final String tooLong = "x".repeat(RationaleText.MAX_LENGTH + 50);

        // when
        candidate.setRationale(tooLong);

        // then — clamped, so a verbose agent cannot fail the write with a database error
        assertThat(candidate.getRationale()).hasSize(RationaleText.MAX_LENGTH);
        assertThat(candidate.getRationale()).isEqualTo(tooLong.substring(0, RationaleText.MAX_LENGTH));
    }

    /**
     * The length boundary: a reason of exactly {@link RationaleText#MAX_LENGTH} is kept verbatim,
     * and one character more is clamped.  Clamped, not rejected — a verbose agent must not fail the
     * write with a database error.
     * <p>
     * Note for the mutation report: the pitest {@code ConditionalsBoundaryMutator} survives inside
     * {@code RationaleText#normalise}, and it is <em>equivalent</em> — {@code >} mutated to
     * {@code >=} differs only when the length equals the maximum, where
     * {@code substring(0, length())} returns the same string.  This test pins the contract, not
     * that mutant.
     */
    @Test
    void setRationale_keepsTheBoundaryAndClampsOneCharacterOver() {
        // given
        final ClassCdd candidate = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final String exactlyMax = "y".repeat(RationaleText.MAX_LENGTH);

        // when
        candidate.setRationale(exactlyMax);

        // then — untouched, not one character short
        assertThat(candidate.getRationale()).isEqualTo(exactlyMax);

        // and when — one character over what the column stores
        candidate.setRationale(exactlyMax + "y");

        // then — clamped rather than rejected
        assertThat(candidate.getRationale()).hasSize(RationaleText.MAX_LENGTH);
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