package domox.webapp.unittests;

import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.webapp.config.ClassCddTableColumnOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the column order of the {@link ClassCdd} candidate tables (see
 * {@link ClassCddTableColumnOrder}) and, more importantly, pins that an id the service does not
 * know is <em>kept</em> rather than hidden — the SPI hides every column omitted from the returned
 * list, so a naive fixed list would silently drop a property added later.
 */
class ClassCddTableColumnOrderTest {

    private final ClassCddTableColumnOrder service = new ClassCddTableColumnOrder();

    /** The order the framework produces on its own (observed on the ClassCdd list page). */
    private static final List<String> FRAMEWORK_ORDER = List.of(
            "candidateName", "candidateType", "hopDepth", "subDomain",
            "ruleMatchCount", "reviewCount", "ruleNames", "approvalCount", "d3Graph");

    private static final List<String> REQUESTED_ORDER = List.of(
            "candidateName", "candidateType", "subDomain", "reviewCount", "approvalCount",
            "hopDepth", "d3Graph", "ruleMatchCount", "ruleNames");

    @Test
    void reordersTheFrameworkOrderToTheRequestedSequence() {
        assertEquals(REQUESTED_ORDER, service.orderStandalone(ClassCdd.class, FRAMEWORK_ORDER));
    }

    @Test
    void parentedCollectionsGetTheSameOrder() {
        assertEquals(REQUESTED_ORDER,
                service.orderParented(ClassCdd.class, "classList", ClassCdd.class, FRAMEWORK_ORDER));
    }

    @Test
    void keepsAnUnknownColumnInsteadOfHidingIt() {
        final var withNewProperty = List.of("candidateName", "brandNewProperty", "candidateType");

        assertEquals(List.of("candidateName", "candidateType", "brandNewProperty"),
                service.orderStandalone(ClassCdd.class, withNewProperty));
    }

    @Test
    void hasNoOpinionForOtherCandidateTypes() {
        assertNull(service.orderStandalone(ResponsibilityCdd.class, FRAMEWORK_ORDER));
    }
}
