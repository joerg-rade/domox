package domox.dom.crc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssociationCddTest {

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

    private AssociationCdd associationWith(AssociationType type) {
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
        final AssociationCdd association = new AssociationCdd("pays", source, target);
        association.setType(type);
        return association;
    }

    @Test
    void mapsAssociationTypeToPlantUmlArrow() {
        // given
        final AssociationCdd association = associationWith(null);

        // when
        final String plantUml = association.toPlantUmlString();

        // then
        for (final Map.Entry<AssociationType, String> expected : EXPECTED_ARROWS.entrySet()) {
            final AssociationCdd typed = associationWith(expected.getKey());
            final String rendered = typed.toPlantUmlString();
            assertTrue(rendered.contains(expected.getValue()),
                    expected.getKey() + " should render '" + expected.getValue()
                            + "' arrow but was: " + rendered);
            assertTrue(rendered.indexOf("PaymentProcessor") < rendered.indexOf(expected.getValue()),
                    "arrow must appear after the source class: " + rendered);
            assertTrue(rendered.indexOf(expected.getValue()) < rendered.indexOf("Gateway"),
                    "arrow must appear before the target class: " + rendered);
        }
        // the fallback (null type) renders as a plain association
        assertTrue(plantUml.contains("->"));
    }

    @Test
    void nullTypeDefaultsToAssociation() {
        // given
        final AssociationCdd association = associationWith(null);

        // when
        final String plantUml = association.toPlantUmlString();

        // then
        assertEquals(-1, plantUml.indexOf("..|>"),
                "Association arrow should be used when type is null");
        assertTrue(plantUml.contains("->"));
    }
}