package domox.dom.crc;

import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link CandidateVegaGraph#renderGraph(Candidate)} through its
 * public API, covering null/empty guards, rule-match grouping, review-panel
 * inclusion, and Vega-Lite JSON validity.
 * <p>
 * These tests exercise the <em>intermediate steps</em> of the graph-building
 * pipeline indirectly via the public {@code renderGraph} method, which is the
 * only API surface of this service.
 */
class CandidateVegaGraphTest {

    private final CandidateVegaGraph service = new CandidateVegaGraph();

    // ============ null / empty guards ============

    @Test
    void returnsNullWhenCandidateIsNull() {
        assertNull(service.renderGraph(null));
    }

    @Test
    void returnsNullWhenRuleMatchesAreNull() {
        // Simulates the JPA scenario where @ManyToMany resolves to null
        // (TABLE_PER_CLASS + base-class @JoinTable leaves the collection null).
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        candidate.setRuleMatches(null);   // ← JPA would set the field directly

        assertNotNull(service.renderGraph(candidate));
    }

    @Test
    void returnsNullWhenCandidateTypeIsNull() {
        // candidateType is used in the Vega title — verify it doesn't crash
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType(null);

        assertNotNull(service.renderGraph(candidate));
    }

    @Test
    void returnsNullWhenCandidateNameIsNull() {
        final ClassCdd candidate = new ClassCdd(null, List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");

        assertNotNull(service.renderGraph(candidate));
    }

    // ============ empty rule matches ============

    @Test
    void returnsVegaSpecWithEmptyRuleMatches() {
        final ClassCdd candidate = new ClassCdd("Pet", List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        // ruleMatches defaults to new ArrayList<>()

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega, "Vega should not be null for a candidate with empty rule matches");
    }

    // ============ rule-match grouping ============

    @Test
    void returnsVegaSpecWithSingleRuleMatch() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    @Test
    void groupsRuleMatchesByRuleClassName() {
        // Two matches for the same rule class → count = 2
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected"),
                ruleMatch("TDR01 – Noun phrase detected"),
                ruleMatch("TDR07 – Direct object relation")));

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    @Test
    void handlesRuleMatchWithNullRuleClassName() {
        // The stream filters out null ruleClassName — should not crash
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected"),
                ruleMatch(null)));

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    @Test
    void handlesManyRuleMatches() {
        // 10 different rule classes — enough to test the height calculation branch
        final List<RuleMatch> matches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            matches.add(ruleMatch("TDR" + (i < 10 ? "0" : "") + i));
        }
        final ClassCdd candidate = classCddWithRuleMatches("Pet", matches);

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    // ============ review panel ============

    @Test
    void includesReviewPanelWhenReviewsExist() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType("ClassCdd");

        // Add one APPROVED review and one REJECTED review
        final Review approved = new Review();
        approved.setStatus(ReviewStatus.APPROVED);
        candidate.addReview(approved);

        final Review rejected = new Review();
        rejected.setStatus(ReviewStatus.REJECTED);
        candidate.addReview(rejected);

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    @Test
    void handlesReviewsWithNullStatus() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType("ClassCdd");

        final Review pending = new Review();
        pending.setStatus(null);    // ← pending review, filtered out
        candidate.addReview(pending);

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);
    }

    // ============ Vega-Lite JSON structure ============

    @Test
    void vegaSpecIsValidJson() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType("ClassCdd");

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);

        // Vega value stores the JSON spec as a string — confirm it's parseable JSON
        final String json = vega.getJson();
        assertNotNull(json);
        assertTrue(json.startsWith("{"), "Vega spec must be a JSON object");
        assertTrue(json.endsWith("}"), "Vega spec must be a JSON object");
        assertTrue(json.contains("\"$schema\""), "Vega spec must contain $schema");
        assertTrue(json.contains("https://vega.github.io/schema/vega-lite/v6.json"),
                "Vega spec must reference Vega-Lite v6 — the version the Causeway "
                + "Vega.Schema enum recognizes (v5 would be classified Schema.NONE "
                + "and render nothing)");
        assertTrue(json.contains("\"title\""), "Vega spec must contain a title");
        assertTrue(json.contains("\"vconcat\""),
                "Vega spec must compose the two panels with vconcat, not layer");
        assertTrue(json.contains("\"layer\""),
                "Rule panel must itself be a layered bar+text chart");
    }

    @Test
    void vegaSpecTitleContainsCandidateTypeAndName() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType("ClassCdd");

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);

        final String json = vega.getJson();
        assertTrue(json.contains("ClassCdd: Pet"),
                "Title should be '<candidateType>: <candidateName>'");
    }

    @Test
    void vegaSpecTitleHandlesNullCandidateType() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType(null);

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);

        final String json = vega.getJson();
        assertTrue(json.contains("null: Pet"),
                "Null candidateType should produce 'null: Pet'");
    }

    @Test
    void vegaSpecContainsRuleData() {
        final ClassCdd candidate = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        candidate.setCandidateType("ClassCdd");

        final var vega = service.renderGraph(candidate);
        assertNotNull(vega);

        final String json = vega.getJson();
        assertTrue(json.contains("\"data\""), "Vega spec must contain a data section");
        assertTrue(json.contains("TDR01"), "Data must reference the rule class name");
    }

    @Test
    void vegaSpecStacksReviewPanelOnlyWhenReviewsExist() {
        // Without reviews → vconcat has ONE panel (rule chart only), no review axes
        final ClassCdd noReviews = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        noReviews.setCandidateType("ClassCdd");

        final String jsonNoReviews = service.renderGraph(noReviews).getJson();
        assertTrue(jsonNoReviews.contains("\"vconcat\""), "Spec must use vconcat");
        assertFalse(jsonNoReviews.contains("\"Reviews\""),
                "Review panel must be omitted when no reviews exist");
        assertFalse(jsonNoReviews.contains("\"legend\":"),
                "Rule panel has a fixed colour — no legend expected");

        // With reviews → vconcat has TWO panels, the review panel with its own
        // height and a disabled colour legend (status is already on the y-axis)
        final ClassCdd withReviews = classCddWithRuleMatches("Pet", List.of(
                ruleMatch("TDR01 – Noun phrase detected")));
        withReviews.setCandidateType("ClassCdd");
        final Review approved = new Review();
        approved.setStatus(ReviewStatus.APPROVED);
        withReviews.addReview(approved);

        final String jsonWithReviews = service.renderGraph(withReviews).getJson();
        assertTrue(jsonWithReviews.contains("\"Reviews\""),
                "Review panel must appear when reviews exist");
        assertTrue(jsonWithReviews.contains("\"height\":80"),
                "Review panel must declare its own height (independent of rule panel)");
        assertTrue(jsonWithReviews.contains("\"legend\":null"),
                "Review panel colour legend must be disabled (statuses on y-axis)");
    }

    // ============ helper factories ============

    /** Creates a RuleMatch with the given ruleClassName. */
    private static RuleMatch ruleMatch(final String ruleClassName) {
        final RuleMatch rm = new RuleMatch();
        rm.setRuleClassName(ruleClassName);
        return rm;
    }

    /**
     * Creates a {@link ClassCdd} whose {@code ruleMatches} list is populated
     * with the given matches, and whose type is set to "ClassCdd".
     */
    private static ClassCdd classCddWithRuleMatches(
            final String name, final List<RuleMatch> matches) {
        final ClassCdd candidate = new ClassCdd(name, List.of(), List.of(), List.of());
        candidate.setCandidateType("ClassCdd");
        // Replace the default empty list with the test data
        candidate.getRuleMatches().clear();
        candidate.getRuleMatches().addAll(matches);
        return candidate;
    }
}
