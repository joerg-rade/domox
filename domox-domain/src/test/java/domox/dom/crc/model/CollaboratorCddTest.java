package domox.dom.crc.model;
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
}