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
        // PlantUML's generalization arrow — `|>-` is NOT parseable by PlantUML and made every
        // diagram that contains an inheritance fail to render (see AssociationType's javadoc).
        EXPECTED_ARROWS.put(AssociationType.GENERALIZATION, "--|>");
        EXPECTED_ARROWS.put(AssociationType.DEPENDENCY, ".>");
        EXPECTED_ARROWS.put(AssociationType.AGGREGATION, "*->");
        EXPECTED_ARROWS.put(AssociationType.COMPOSITION, "+->");
        EXPECTED_ARROWS.put(AssociationType.IMPLEMENTATION, "..|>");
        // Semantic equivalence.  Headless, and therefore the one symbol PlantUML can mis-read — see
        // `padsTheArrowSoAPlantUmlDirectionLetterCannotSwallowTheTargetsFirstCharacter`.
        EXPECTED_ARROWS.put(AssociationType.SYNONYM, "==");
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

    /** An association between the two named classes, labelled with its own candidate name. */
    private CollaboratorCdd association(String sourceName, String targetName, AssociationType type) {
        final ClassCdd source = new ClassCdd(sourceName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ClassCdd target = new ClassCdd(targetName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final CollaboratorCdd association =
                new CollaboratorCdd(sourceName + "_" + targetName, source, target);
        association.setType(type);
        return association;
    }

    @ParameterizedTest
    @EnumSource(AssociationType.class)
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

    /**
     * The arrow is padded, so PlantUML cannot read the target's first letter as a direction.
     *
     * <p>This is the defect that made the PlantUML-rendered diagram disagree with the D3 view about
     * the model's classes: PlantUML reads {@code d}/{@code l}/{@code r}/{@code u}/{@code x} after a
     * <em>headless</em> arrow as a direction specifier, so the unpadded
     * {@code Address==Recipient} lost the {@code R} and rendered a phantom class {@code ecipient} —
     * a class the D3 view does not have, with the association aimed at it.</p>
     */
    @Test
    void padsTheArrowSoAPlantUmlDirectionLetterCannotSwallowTheTargetsFirstCharacter() {
        // given — the model's real case: a SYNONYM whose target begins with PlantUML's "right" letter
        final CollaboratorCdd synonym = association("Address", "Recipient", AssociationType.SYNONYM);

        // when / then
        assertThat(synonym.toPlantUmlString()).isEqualTo("Address == Recipient: Address_Recipient");
    }

    /** Every symbol is padded, because the hazard belongs to PlantUML's grammar rather than to one arrow. */
    @ParameterizedTest
    @EnumSource(AssociationType.class)
    void neverButtsAnArrowAgainstTheTargetsName(final AssociationType type) {
        for (final String initial : new String[]{"d", "l", "r", "u", "x", "D", "L", "R", "U", "X"}) {
            // given
            final CollaboratorCdd association = association("Address", initial + "ecipient", type);

            // when
            final String rendered = association.toPlantUmlString();

            // then — the arrow stands alone between two spaces, and the name survives whole
            assertThat(rendered).as(type + " must render a padded arrow").contains(" " + type.symbol + " ");
            assertThat(rendered).as("the target's first letter must not touch the arrow")
                    .contains(type.symbol + " " + initial);
        }
    }

    /** The multiplicities are padded too, so a quoted one cannot butt against the arrow either. */
    @Test
    void padsTheMultiplicitiesAroundTheArrow() {
        // given
        final CollaboratorCdd association = association("Customer", "Care", AssociationType.ASSOCIATION);
        association.setSourceCardinality("6");

        // when / then — `Customer"6"->Care` unpadded; the same relation PlantUML is expected to read
        assertThat(association.toPlantUmlString()).isEqualTo("Customer \"6\" -> Care: Customer_Care");
    }
}