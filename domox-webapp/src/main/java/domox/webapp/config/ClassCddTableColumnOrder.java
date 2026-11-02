package domox.webapp.config;

import domox.dom.crc.model.ClassCdd;
import org.apache.causeway.applib.services.tablecol.TableColumnOrderForCollectionTypeAbstract;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Column order for tables that list {@link ClassCdd} candidates — the standalone
 * <i>List All Class Candidates</i> page and the parented <i>Classes</i> collection on a
 * {@code DomainModel} page.
 *
 * <p><b>Why an SPI and not {@code @PropertyLayout(sequence = …)}:</b> the table column order is
 * <em>not</em> derived from the property sequence alone.  In this metamodel the properties live in
 * different layout fieldsets (the D3 graph sits in the {@code content} fieldset of the
 * "Dependency Diagram" tab), and that grouping wins over {@code sequence} — which is why
 * {@code d3Graph} rendered last regardless of its {@code sequence = "3"}.  Causeway's supported way
 * to control this is {@link org.apache.causeway.applib.services.tablecol.TableColumnOrderService}.</p>
 *
 * <p><b>Defensive reordering.</b> The SPI hides any column whose id is <em>not</em> in the returned
 * list, so returning a fixed list would silently drop a property added later.  This implementation
 * therefore <em>sorts</em> the ids the framework supplies: known ids in the order below, unknown
 * ids after them, keeping their relative framework order.  A new property can never disappear.</p>
 */
@Component
public class ClassCddTableColumnOrder
        extends TableColumnOrderForCollectionTypeAbstract<ClassCdd> {

    /**
     * The requested column sequence:
     * <ol>
     *   <li>Candidate Name</li>
     *   <li>Candidate Type</li>
     *   <li>Sub-domain</li>
     *   <li>Review Count</li>
     *   <li>Approval Count</li>
     *   <li>Diagram Hop Depth</li>
     *   <li>Lexical Graph</li>
     *   <li>Rule Match Count</li>
     *   <li>Rule Names</li>
     * </ol>
     */
    private static final List<String> COLUMN_ORDER = List.of(
            "candidateName",    // Candidate Name
            "candidateType",    // Candidate Type
            "subDomain",        // Sub-domain
            "reviewCount",      // Review Count
            "approvalCount",    // Approval Count
            "hopDepth",         // Diagram Hop Depth
            "d3Graph",          // Lexical Graph
            "ruleMatchCount",   // Rule Match Count
            "ruleNames");       // Rule Names

    public ClassCddTableColumnOrder() {
        super(ClassCdd.class);
    }

    @Override
    protected List<String> orderStandalone(final List<String> associationIds) {
        return reorder(associationIds);
    }

    @Override
    protected List<String> orderParented(
            final Class<?> parentType, final String collectionId, final List<String> associationIds) {
        return reorder(associationIds);
    }

    /**
     * Stable sort by {@link #COLUMN_ORDER}; ids not listed keep their relative framework order and
     * are placed last.
     */
    private static List<String> reorder(final List<String> associationIds) {
        return associationIds.stream()
                .sorted(Comparator.comparingInt(ClassCddTableColumnOrder::rank))
                .toList();
    }

    private static int rank(final String associationId) {
        final int index = COLUMN_ORDER.indexOf(associationId);
        return index < 0 ? COLUMN_ORDER.size() : index;
    }
}
