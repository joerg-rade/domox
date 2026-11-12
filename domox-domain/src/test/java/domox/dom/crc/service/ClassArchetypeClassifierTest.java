package domox.dom.crc.service;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.NlpProperties;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail for {@link ClassArchetypeClassifier} — the post-extraction heuristic that suggests a
 * Coad archetype (pink/yellow/blue/green) for a {@link ClassCdd}.
 * <p>
 * The classifier's scores are private, so each test drives the public
 * {@link ClassArchetypeClassifier#suggestArchetype(ClassCdd)} entry point and asserts the
 * <em>outcome</em>: a signal (a keyword, a temporal property, an action count, a typed dependency)
 * must be strong enough to win against the {@code PARTY_PLACE_THING} baseline.  {@link NlpProperties}
 * is real (its keyword lists default to empty and are configured per test), so no mocking is needed.
 */
class ClassArchetypeClassifierTest {

    private final NlpProperties nlpProperties = new NlpProperties();
    private final ClassArchetypeClassifier classifier = new ClassArchetypeClassifier(nlpProperties);

    // ---------------------------------------------------------------- helpers

    private static ClassCdd clazz(final String name) {
        return clazz(name, List.of());
    }

    private static ClassCdd clazz(final String name, final List<AttributeCdd> attributes) {
        return new ClassCdd(name, new ArrayList<>(attributes), new ArrayList<>(), new ArrayList<>());
    }

    private static AttributeCdd attribute(final String name) {
        return new AttributeCdd(name, "String");
    }

    private static ResponsibilityCdd responsibility(final String name) {
        return new ResponsibilityCdd(name, List.of(), "void");
    }

    private static CollaboratorCdd collaborator(final String name) {
        return new CollaboratorCdd(name, null, null);
    }

    /** A {@link RuleMatch} whose typed dependency carries the given type (lemmas irrelevant here). */
    private static RuleMatch matchOfType(final TdType type) {
        final TypedDependency dependency = new TypedDependency();
        dependency.setType(type);
        dependency.setGovernorLemma("a");
        dependency.setDependentLemma("b");
        final RuleMatch match = new RuleMatch();
        match.setTypedDependency(dependency);
        return match;
    }

    private static void withMatches(final ClassCdd candidate, final RuleMatch... matches) {
        candidate.setRuleMatches(List.of(matches));
    }

    // ---------------------------------------------------------------- degenerate inputs

    @Test
    void aNullCandidateIsClassifiedAsPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(null)).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void aCandidateWithNoNameAndNoSignalsIsPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(clazz(null))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void aPlainNameWithNoSignalsDefaultsToPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(clazz("Widgets"))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void aNullKeywordListIsTreatedAsNoKeywords() {
        nlpProperties.setArchetypeMomentIntervalKeywords(null);

        // "Order" would otherwise be a moment-interval via the keyword list
        assertThat(classifier.suggestArchetype(clazz("Order"))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    // ---------------------------------------------------------------- MOMENT_INTERVAL

    @Test
    void aMomentIntervalKeywordWins() {
        nlpProperties.setArchetypeMomentIntervalKeywords(List.of("order", "sale"));

        assertThat(classifier.suggestArchetype(clazz("Order"))).isEqualTo(ClassType.MOMENT_INTERVAL);
    }

    @Test
    void temporalPropertyNamesSignalMomentInterval() {
        final ClassCdd booking = clazz("Booking", List.of(
                attribute("orderDate"), attribute("startTime"), attribute("duration")));

        assertThat(classifier.suggestArchetype(booking)).isEqualTo(ClassType.MOMENT_INTERVAL);
    }

    @Test
    void manyActionsSignalMomentInterval() {
        final ClassCdd purchase = clazz("Purchase");
        purchase.setResponsibilityList(new ArrayList<>(List.of(
                responsibility("submit"), responsibility("pay"), responsibility("confirm"),
                responsibility("ship"), responsibility("refund"))));

        assertThat(classifier.suggestArchetype(purchase)).isEqualTo(ClassType.MOMENT_INTERVAL);
    }

    @Test
    void momentIntervalTypedDependenciesContributeToTheScore() {
        final ClassCdd txn = clazz("Txn");
        withMatches(txn,
                matchOfType(TdType.NSUBJ), matchOfType(TdType.NSUBJ), matchOfType(TdType.OBJ),
                matchOfType(TdType.NSUBJ), matchOfType(TdType.OBJ));

        assertThat(classifier.suggestArchetype(txn)).isEqualTo(ClassType.MOMENT_INTERVAL);
    }

    // ---------------------------------------------------------------- ROLE

    @Test
    void aRoleKeywordWins() {
        nlpProperties.setArchetypeRoleKeywords(List.of("manager"));

        assertThat(classifier.suggestArchetype(clazz("Manager"))).isEqualTo(ClassType.ROLE);
    }

    @Test
    void possessiveDependenciesReinforceTheRoleArchetype() {
        nlpProperties.setArchetypeRoleKeywords(List.of("manager"));
        final ClassCdd manager = clazz("Manager");
        withMatches(manager, matchOfType(TdType.NMOD_POSS));

        assertThat(classifier.suggestArchetype(manager)).isEqualTo(ClassType.ROLE);
    }

    // ---------------------------------------------------------------- DESCRIPTION

    @Test
    void aDescriptionKeywordWins() {
        nlpProperties.setArchetypeDescriptionKeywords(List.of("type"));

        assertThat(classifier.suggestArchetype(clazz("Type"))).isEqualTo(ClassType.DESCRIPTION);
    }

    @Test
    void amodDependenciesSignalDescription() {
        final ClassCdd colour = clazz("Colour");
        withMatches(colour, matchOfType(TdType.AMOD), matchOfType(TdType.AMOD));

        assertThat(classifier.suggestArchetype(colour)).isEqualTo(ClassType.DESCRIPTION);
    }

    @Test
    void manyAttributesAndFewActionsSignalDescription() {
        nlpProperties.setArchetypeDescriptionKeywords(List.of("category"));
        final ClassCdd category = clazz("Category", List.of(
                attribute("code"), attribute("label"), attribute("rank"), attribute("notes")));

        assertThat(classifier.suggestArchetype(category)).isEqualTo(ClassType.DESCRIPTION);
    }

    @Test
    void beingATargetOfAssociationsReinforcesTheDescriptionArchetype() {
        nlpProperties.setArchetypeDescriptionKeywords(List.of("category"));
        final ClassCdd category = clazz("Category");
        category.setCollaboratorList(new ArrayList<>(List.of(collaborator("parent"))));

        assertThat(classifier.suggestArchetype(category)).isEqualTo(ClassType.DESCRIPTION);
    }

    // ---------------------------------------------------------------- PARTY_PLACE_THING

    @Test
    void placeKeywordsSignalPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(clazz("Warehouse"))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void organisationKeywordsSignalPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(clazz("Company"))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void thingKeywordsSignalPartyPlaceThing() {
        assertThat(classifier.suggestArchetype(clazz("Product"))).isEqualTo(ClassType.PARTY_PLACE_THING);
    }

    @Test
    void aModeratePropertyCountSignalsPartyPlaceThing() {
        final ClassCdd gadget = clazz("Gadget", List.of(
                attribute("colour"), attribute("weight"), attribute("size")));

        assertThat(classifier.suggestArchetype(gadget)).isEqualTo(ClassType.PARTY_PLACE_THING);
    }
}
