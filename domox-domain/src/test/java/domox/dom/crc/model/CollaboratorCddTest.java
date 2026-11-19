package domox.dom.crc.model;
import domox.dom.rules.RuleMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollaboratorCddTest {

    private static final Map<AssociationType, String> EXPECTED_ARROWS =
            new EnumMap<>(AssociationType.class);

    static {
        EXPECTED_ARROWS.put(AssociationType.ASSOCIATION, "->");
        EXPECTED_ARROWS.put(AssociationType.GENERALIZATION, "|>-");
        EXPECTED_ARROWS.put(AssociationType.DEPENDENCY, ".>");
        EXPECTED_ARROWS.put(AssociationType.AGGREGATION, "*->");
        EXPECTED_ARROWS.put(AssociationType.COMPOSITION, "+->");
        EXPECTED_ARROWS.put(AssociationType.IMPLEMENTATION, "..|>");
    }

    private CollaboratorCdd associationWith(AssociationType type) {
        final ClassCdd source = new ClassCdd(
                "PaymentProcessor",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>());
        final ClassCdd target = new ClassCdd(
                "Gateway",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>());
        final CollaboratorCdd association = new CollaboratorCdd("pays", source, target);
        association.setType(type);
        return association;
    }

    @ParameterizedTest
    @EnumSource(value = AssociationType.class, mode = EnumSource.Mode.EXCLUDE, names = "SYNONYM")
    void mapsAssociationTypeToPlantUmlArrow(final AssociationType type) {
        // given — the arrow the type is expected to render
        final String expectedArrow = EXPECTED_ARROWS.get(type);

        // when
        final CollaboratorCdd association = associationWith(type);
        final String rendered = association.toPlantUmlString();

        // then — the arrow is present and sits between the source and target classes
        assertThat(rendered).as(type + " should render '" + expectedArrow + "' arrow").contains(expectedArrow);
        assertThat(rendered.indexOf("PaymentProcessor")).as("arrow must appear after the source class")
                .isLessThan(rendered.indexOf(expectedArrow));
        assertThat(rendered.indexOf(expectedArrow)).as("arrow must appear before the target class")
                .isLessThan(rendered.indexOf("Gateway"));
    }

    @Test
    void nullTypeDefaultsToAssociation() {
        // given
        final CollaboratorCdd association = associationWith(null);

        // when
        final String plantUml = association.toPlantUmlString();

        // then
        assertThat(plantUml.indexOf("..|>")).as("Association arrow should be used when type is null").isEqualTo(-1);
        assertThat(plantUml).contains("->");
    }

    /**
     * The edge label is the verb the association was extracted from, not the candidate's own name:
     * an edge labelled {@code Customer_Premium} states nothing about the relationship.
     */
    @Test
    void rendersTheRelationshipVerbAsTheEdgeLabel() {
        // given — an association whose rule match recorded "relationship.add(inventory (has) product)"
        final CollaboratorCdd association = associationWith(AssociationType.ASSOCIATION);
        final RuleMatch match = new RuleMatch();
        match.setRuleClassName("TDR16");
        match.setDescription("relationship.add(inventory (has) product)");
        association.addMatchingRule(match);

        // when / then
        assertThat(association.label()).isEqualTo("has");
        assertThat(association.toPlantUmlString()).endsWith(": has");
    }

    /** Without a rule match there is no verb to read — the candidate name keeps the edge labelled. */
    @Test
    void fallsBackToTheCandidateNameWhenNoRuleMatchRecordsAVerb() {
        final CollaboratorCdd association = associationWith(AssociationType.ASSOCIATION);

        assertThat(association.label()).isEqualTo("pays");
        assertThat(association.toPlantUmlString()).endsWith(": pays");
    }
}