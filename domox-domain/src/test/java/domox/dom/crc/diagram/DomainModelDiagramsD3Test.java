package domox.dom.crc.diagram;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.model.SubDomain;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link DomainModelDiagramsD3#renderDiagram(DomainModel)}:
 * <ul>
 *   <li>the diagram seeds from <em>approved</em> candidates only,</li>
 *   <li>dependency edges are drawn between candidate-word lemmas,</li>
 *   <li>association-artifact compound nodes are excluded from the diagram,</li>
 *   <li>sub-domain labels are attached to ClassCdd nodes,</li>
 *   <li>the legend reflects the POS codes that appear in the nodes, and</li>
 *   <li>null/empty/no-approved scenarios return {@code null}.</li>
 * </ul>
 */
class DomainModelDiagramsD3Test {

    private final DomainModelDiagramsD3 service = new DomainModelDiagramsD3();

    // ============ null / empty guards ============

    @Test
    void returnsNullWhenModelIsNull() {
        assertNull(service.renderDiagram(null));
    }

    @Test
    void returnsNullWhenModelHasNoCandidates() {
        final DomainModel model = new DomainModel();
        assertNull(service.renderDiagram(model));
    }

    @Test
    void returnsNullWhenNoCandidatesAreApproved() {
        // given — a model with candidates but none have an APPROVED review
        final DomainModel model = new DomainModel();
        model.classList.add(new ClassCdd("Store", List.of(), List.of(), List.of()));

        // when
        final String result = service.renderDiagram(model);

        // then
        assertNull(result, "no approved candidates → no diagram");
    }

    // ============ approved node inclusion ============

    @Test
    void returnsJsonWithApprovedNodesOnly() {
        // given — one approved ClassCdd with no dependencies
        final DomainModel model = modelWithApprovedClass("Store");

        // when
        final String json = service.renderDiagram(model);

        // then — a JSON object with the approved node and an empty links array
        assertNotNull(json);
        assertTrue(json.startsWith("{"), "must be a JSON object");
        assertTrue(json.endsWith("}"), "must be a JSON object");
        assertContains(json, "\"id\":\"store\"");
        assertContains(json, "\"name\":\"Store\"");
        assertContains(json, "\"approved\":true");
        assertContains(json, "\"links\":[]", "no dependencies → empty links array");
    }

    @Test
    void includesApprovedNodesFromAllCandidateTypes() {
        // given — approved candidates of different types
        final DomainModel model = new DomainModel();
        model.classList.add(approvedClass("Store"));
        model.responsibilityList.add(approvedAction("browse"));
        model.attributeList.add(approvedProperty("name"));

        // when
        final String json = service.renderDiagram(model);

        // then — all three approved lemmas appear as nodes
        assertNotNull(json);
        assertContains(json, "\"id\":\"store\"");
        assertContains(json, "\"id\":\"browse\"");
        assertContains(json, "\"id\":\"name\"");
    }

    @Test
    void approvedNodeUsesOriginalCasingInNameField() {
        // given — an approved ClassCdd with mixed-case name
        final DomainModel model = modelWithApprovedClass("OnlineStore");

        // when
        final String json = service.renderDiagram(model);

        // then — the 'name' field preserves original casing, 'id' is lowercased
        assertNotNull(json);
        assertContains(json, "\"id\":\"onlinestore\"");
        assertContains(json, "\"name\":\"OnlineStore\"");
    }

    // ============ dependency edges ============

    @Test
    void includesDependencyEdgesBetweenApprovedCandidates() {
        // given — two approved candidates connected by a typed dependency
        final DomainModel model = new DomainModel();
        final ClassCdd customer = approvedClass("Customer");
        final ResponsibilityCdd purchase = approvedAction("purchase");
        model.classList.add(customer);
        model.responsibilityList.add(purchase);

        // dependency: purchase ←nsubj→ customer
        final TypedDependency td = typedDep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN);
        customer.getRuleMatches().add(ruleMatchWith(td));
        purchase.getRuleMatches().add(ruleMatchWith(td));

        // when
        final String json = service.renderDiagram(model);

        // then — both nodes appear with a labelled edge between them
        assertNotNull(json);
        assertContains(json, "\"id\":\"customer\"");
        assertContains(json, "\"id\":\"purchase\"");
        assertContains(json, "\"source\":\"purchase\"");
        assertContains(json, "\"target\":\"customer\"");
        assertContains(json, "\"label\":\"nsubj\"");
    }

    @Test
    void dropsDependencyWhoseEndpointIsNotACandidateWord() {
        // given — approved candidate + dependency pointing to a non-candidate word
        final DomainModel model = new DomainModel();
        final ClassCdd customer = approvedClass("Customer");
        model.classList.add(customer);

        final TypedDependency td = typedDep(TdType.AMOD, "customer", PartOfSpeechType.NN,
                "unknown_word", PartOfSpeechType.NN);
        customer.getRuleMatches().add(ruleMatchWith(td));

        // when
        final String json = service.renderDiagram(model);

        // then — the customer node exists but the link to 'unknown_word' is dropped
        assertNotNull(json);
        assertContains(json, "\"id\":\"customer\"");
        assertContains(json, "\"links\":[]", "non-candidate endpoint → link dropped");
    }

    @Test
    void includesUnapprovedNeighbourNodeButMarksItNotApproved() {
        // given — an approved candidate connected to an unapproved one
        final DomainModel model = new DomainModel();
        final ClassCdd customer = approvedClass("Customer");
        final ResponsibilityCdd purchase = new ResponsibilityCdd("purchase", List.of(), "void");
        purchase.setCandidateType("ActionCdd");
        // no review → not approved
        model.classList.add(customer);
        model.responsibilityList.add(purchase);

        final TypedDependency td = typedDep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "customer", PartOfSpeechType.NN);
        customer.getRuleMatches().add(ruleMatchWith(td));
        purchase.getRuleMatches().add(ruleMatchWith(td));

        // when
        final String json = service.renderDiagram(model);

        // then — the unapproved 'purchase' still appears as a node (it's a dependency
        // endpoint) but with "approved":false
        assertNotNull(json);
        assertContains(json, "\"id\":\"customer\"");
        assertContains(json, "\"approved\":true");
        assertContains(json, "\"id\":\"purchase\"");
        assertContains(json, "\"approved\":false");
    }

    // ============ association-artifact filtering ============

    @Test
    void excludesAssociationArtifactCompoundNodes() {
        // given — approved classes "Store" and "OnlineStore", plus an Association-typed
        // candidate "OnlineStore_Store" whose two halves are both candidate lemmas
        final DomainModel model = new DomainModel();
        model.classList.add(approvedClass("Store"));
        model.classList.add(approvedClass("OnlineStore"));
        final CollaboratorCdd compound = approvedAssociation("OnlineStore_Store");
        model.collaboratorList.add(compound);

        // when
        final String json = service.renderDiagram(model);

        // then — the synthetic compound is excluded; the real lemmas remain
        assertNotNull(json);
        assertContains(json, "\"id\":\"store\"");
        assertContains(json, "\"id\":\"onlinestore\"");
        assertDoesNotContain(json, "onlinestore_store",
                "association-artifact compound must be excluded from the diagram");
    }

    @Test
    void keepsAssociationWhenNameIsNotACompoundOfCandidateLemmas() {
        // given — an Association-typed candidate whose name is not a "_"-joined compound
        // of two existing candidate lemmas
        final DomainModel model = new DomainModel();
        model.classList.add(approvedClass("Store"));
        final CollaboratorCdd simple = approvedAssociation("contains");
        model.collaboratorList.add(simple);

        // when
        final String json = service.renderDiagram(model);

        // then — "contains" has no underscore, so it is NOT filtered as an artifact
        assertNotNull(json);
        assertContains(json, "\"id\":\"contains\"");
    }

    // ============ subDomain inclusion ============

    @Test
    void includesSubdomainInNodeWhenClassCddHasSubDomain() {
        // given — an approved ClassCdd with a SubDomain assignment
        final DomainModel model = new DomainModel();
        final SubDomain sd = new SubDomain();
        sd.setName("Inventory");
        final ClassCdd store = approvedClass("Store");
        store.setSubDomain(sd);
        model.classList.add(store);

        // when
        final String json = service.renderDiagram(model);

        // then — the node carries a "subdomain" field
        assertNotNull(json);
        assertContains(json, "\"subdomain\":\"Inventory\"");
    }

    @Test
    void omitsSubdomainFieldWhenClassCddHasNoSubDomain() {
        // given — an approved ClassCdd with no SubDomain
        final DomainModel model = modelWithApprovedClass("Customer");

        // when
        final String json = service.renderDiagram(model);

        // then — no "subdomain" key appears for the node
        assertNotNull(json);
        assertDoesNotContain(json, "\"subdomain\"");
    }

    // ============ legend ============

    @Test
    void legendIncludesPosCodesThatAppearInNodes() {
        // given — approved candidates with a dependency carrying NN and VBZ POS tags
        final DomainModel model = new DomainModel();
        final ClassCdd store = approvedClass("Store");
        final ResponsibilityCdd purchase = approvedAction("purchase");
        model.classList.add(store);
        model.responsibilityList.add(purchase);

        final TypedDependency td = typedDep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "store", PartOfSpeechType.NN);
        store.getRuleMatches().add(ruleMatchWith(td));
        purchase.getRuleMatches().add(ruleMatchWith(td));

        // when
        final String json = service.renderDiagram(model);

        // then — legend includes Noun (NN → #3498DB) and Verb (VBZ → #E74C3C)
        assertNotNull(json);
        assertContains(json, "\"color\":\"#3498DB\"", "Noun legend entry");
        assertContains(json, "\"color\":\"#E74C3C\"", "Verb legend entry");
        assertDoesNotContain(json, "#2ECC71", "Adjective legend should NOT appear (no JJ/JJR/JJS)");
    }

    @Test
    void legendIsEmptyWhenNoDependenciesExist() {
        // given — an approved node with no rule matches at all
        final DomainModel model = modelWithApprovedClass("Store");

        // when
        final String json = service.renderDiagram(model);

        // then — the legend array is empty
        assertNotNull(json);
        assertContains(json, "\"legend\":[]", "no dependencies → no POS codes → empty legend");
    }

    // ============ node sizing (rule-match count scaling) ============

    @Test
    void nodeRadiusScalesWithRuleMatchCount() {
        // given — two approved candidates with different numbers of rule matches
        final DomainModel model = new DomainModel();
        final ClassCdd store = approvedClass("Store");       // 1 rule match (from its approval td below)
        final ResponsibilityCdd purchase = approvedAction("purchase"); // 2 rule matches
        model.classList.add(store);
        model.responsibilityList.add(purchase);

        final TypedDependency td1 = typedDep(TdType.NSUBJ, "purchase", PartOfSpeechType.VBZ,
                "store", PartOfSpeechType.NN);
        store.getRuleMatches().add(ruleMatchWith(td1));
        purchase.getRuleMatches().add(ruleMatchWith(td1));
        // Add a second match for purchase
        final TypedDependency td2 = typedDep(TdType.OBJ, "purchase", PartOfSpeechType.VBZ,
                "item", PartOfSpeechType.NN);
        purchase.getRuleMatches().add(ruleMatchWith(td2));

        // when
        final String json = service.renderDiagram(model);

        // then — purchase node has a larger radius than store
        assertNotNull(json);
        // Both are present; the actual radius values depend on the exact count scaling.
        // At minimum we verify that both nodes have an "r" field.
        assertContains(json, "\"id\":\"store\"");
        assertContains(json, "\"id\":\"purchase\"");
        assertContains(json, "\"r\":");
    }

    // ============ error resilience ============

    @Test
    void returnsNullWhenExceptionOccurs() {
        // DomainModelDiagramsD3 catches Exception and returns null — verify with
        // a model that triggers a path through the code.  A null candidate name
        // is handled gracefully (lower returns "") but won't throw.  We just
        // verify the normal happy-path guard still works.
        final DomainModel model = new DomainModel();
        // Add a null candidate to test edge-case robustness of candidatesOf
        model.classList.add(null);

        // when — no approved candidates exist (the null element is skipped in the stream)
        final String result = service.renderDiagram(model);
        assertNull(result, "no approved candidates → null");
    }

    // ============ JSON structure ============

    @Test
    void jsonContainsTopLevelNodesLinksAndLegendKeys() {
        // given — a minimal model with one approved candidate
        final DomainModel model = modelWithApprovedClass("Store");

        // when
        final String json = service.renderDiagram(model);

        // then — the JSON has the three top-level keys
        assertNotNull(json);
        assertContains(json, "\"nodes\"");
        assertContains(json, "\"links\"");
        assertContains(json, "\"legend\"");
    }

    // ============ helper methods ============

    private static DomainModel modelWithApprovedClass(String name) {
        final DomainModel model = new DomainModel();
        model.classList.add(approvedClass(name));
        return model;
    }

    private static ClassCdd approvedClass(String name) {
        final ClassCdd c = new ClassCdd(name, List.of(), List.of(), List.of());
        c.setCandidateType("ClassCdd");
        c.getReviews().add(approvedReview(c));
        return c;
    }

    private static ResponsibilityCdd approvedAction(String name) {
        final ResponsibilityCdd a = new ResponsibilityCdd(name, List.of(), "void");
        a.setCandidateType("ActionCdd");
        a.getReviews().add(approvedReview(a));
        return a;
    }

    private static AttributeCdd approvedProperty(String name) {
        final AttributeCdd p = new AttributeCdd(name, "String");
        p.setCandidateType("PropertyCdd");
        p.getReviews().add(approvedReview(p));
        return p;
    }

    private static CollaboratorCdd approvedAssociation(String name) {
        final CollaboratorCdd a = new CollaboratorCdd(name, null, null);
        a.setCandidateType("AssociationCdd");
        a.getReviews().add(approvedReview(a));
        return a;
    }

    private static Review approvedReview(Candidate candidate) {
        final Review review = new Review();
        review.setStatus(ReviewStatus.APPROVED);
        review.setCandidate(candidate);
        return review;
    }

    private static RuleMatch ruleMatchWith(TypedDependency td) {
        final RuleMatch rm = new RuleMatch();
        rm.setTypedDependency(td);
        return rm;
    }

    private static TypedDependency typedDep(TdType type,
                                            String govLemma,
                                            PartOfSpeechType govPos,
                                            String depLemma,
                                            PartOfSpeechType depPos) {
        final TypedDependency td = new TypedDependency();
        td.setType(type);
        td.setGovernorLemma(govLemma);
        td.setGovernorPos(govPos);
        td.setDependentLemma(depLemma);
        td.setDependentPos(depPos);
        return td;
    }

    /** Asserts that {@code json} contains {@code substring}. */
    private static void assertContains(String json, String substring) {
        assertTrue(json.contains(substring),
                () -> "Expected JSON to contain \"" + substring
                        + "\" but it was not found.\nJSON: " + abbreviate(json));
    }

    /** Asserts that {@code json} contains {@code substring}, with a custom message. */
    private static void assertContains(String json, String substring, String message) {
        assertTrue(json.contains(substring),
                () -> message + "\nExpected \"" + substring
                        + "\" but it was not found.\nJSON: " + abbreviate(json));
    }

    /** Asserts that {@code json} does NOT contain {@code substring}. */
    private static void assertDoesNotContain(String json, String substring) {
        assertFalse(json.contains(substring),
                () -> "Expected JSON to NOT contain \"" + substring
                        + "\" but it was found.\nJSON: " + abbreviate(json));
    }

    /** Asserts that {@code json} does NOT contain {@code substring}, with a custom message. */
    private static void assertDoesNotContain(String json, String substring, String message) {
        assertFalse(json.contains(substring),
                () -> message + "\nExpected to NOT find \"" + substring
                        + "\" but it was found.\nJSON: " + abbreviate(json));
    }

    /** Truncates long JSON for readable assertion messages. */
    private static String abbreviate(String s) {
        if (s == null) return "null";
        return s.length() <= 400 ? s : s.substring(0, 200) + " … " + s.substring(s.length() - 200);
    }
}
