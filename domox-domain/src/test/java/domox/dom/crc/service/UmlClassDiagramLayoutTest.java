package domox.dom.crc.service;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.apache.causeway.applib.layout.component.ActionLayoutData;
import org.apache.causeway.applib.layout.component.CollectionLayoutData;
import org.apache.causeway.applib.layout.component.DomainObjectLayoutData;
import org.apache.causeway.applib.layout.component.FieldSet;
import org.apache.causeway.applib.layout.component.PropertyLayoutData;
import org.apache.causeway.applib.layout.grid.bootstrap.BSCol;
import org.apache.causeway.applib.layout.grid.bootstrap.BSGrid;
import org.apache.causeway.applib.layout.grid.bootstrap.BSRow;
import org.apache.causeway.applib.layout.grid.bootstrap.BSTab;
import org.apache.causeway.applib.layout.grid.bootstrap.BSTabGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page composition declared by {@code UmlClassDiagram.layout.xml}: three <em>tabs</em> of one
 * artifact, not three stacked rows.
 *
 * <p><b>Why a test and not a screenshot.</b>  Causeway <em>silently discards</em> a layout XML that
 * breaks its structural rules and renders the bundled span-4 fallback grid instead — the build stays
 * green and the app boots (see {@code CAUSEWAY-LAYOUT.md} §1).  A page-level check needs a browser
 * and a database; this unmarshals the shipped resource with the same JAXB model Causeway reads it
 * with, so it runs in milliseconds and fails the moment the file stops describing what the page
 * should be.</p>
 *
 * <p>The companion guardrail is {@code scripts/check-layout-xml.sh}, which checks the two markers
 * Causeway's own validator insists on; this test checks the <em>shape</em> those markers are embedded
 * in.  Both are needed — the script cannot tell a tab group from a stack.</p>
 *
 * <p><b>The regression this pins.</b>  The page used to be three full-width rows — the PDF, then the
 * D3 picture, then the PlantUML source — so it was roughly three screens tall and a reader who does
 * not scroll reported the page as having a single diagram.  Content below the horizon is content a
 * reader does not know exists; see {@code docs/causeway/causeway-ui-layout-guidelines.md}.</p>
 */
class UmlClassDiagramLayoutTest {

    /** The three views, in the order the page offers them.  The first is the default tab. */
    private static final List<String> TAB_NAMES = List.of("Diagram", "Diagram (D3)", "PlantUML Source");

    /** fieldSet id → the property ids it holds, per tab. */
    private static final Map<String, List<String>> PROPERTIES_BY_FIELDSET = Map.of(
            "content", List.of("diagram", "message"),
            "d3", List.of("d3Graph"),
            "source", List.of("plantUml"));

    private static BSGrid grid;

    @BeforeAll
    static void loadTheShippedGrid() throws Exception {
        try (InputStream xml = UmlClassDiagram.class.getResourceAsStream("UmlClassDiagram.layout.xml")) {
            assertThat(xml)
                    .as("UmlClassDiagram.layout.xml must be on the classpath — domox-domain declares "
                            + "src/main/java as a resource root; without the file Causeway falls back to "
                            + "its span-4 default grid")
                    .isNotNull();

            // The same JAXB model Causeway's GridService reads the file with.  Every type reachable
            // through an @XmlElementRef has to be registered, or context creation fails.
            final JAXBContext context = JAXBContext.newInstance(
                    BSGrid.class, BSRow.class, BSCol.class, BSTabGroup.class, BSTab.class,
                    FieldSet.class, PropertyLayoutData.class, ActionLayoutData.class,
                    CollectionLayoutData.class, DomainObjectLayoutData.class);
            final Unmarshaller unmarshaller = context.createUnmarshaller();
            grid = (BSGrid) unmarshaller.unmarshal(xml);
        }
    }

    @Test
    void thePageIsAHeaderRowAndOneContentRow() {
        // then — two rows: the object title/actions, then everything else
        assertThat(grid.getRows()).as("header row + content row").hasSize(2);
        assertThat(colsOf(grid.getRows().getFirst()))
                .as("the header column spans the page and absorbs the unreferenced members")
                .singleElement()
                .satisfies(header -> {
                    assertThat(header.getSpan()).isEqualTo(12);
                    assertThat(header.isUnreferencedActions()).isTrue();
                    assertThat(header.isUnreferencedCollections()).isTrue();
                    assertThat(header.getDomainObject()).isNotNull();
                });
    }

    @Test
    void theThreeViewsAreTabsInReadingOrder() {
        // when
        final BSTabGroup tabs = tabGroup();

        // then — one tab per view, in the order a reader meets them
        assertThat(tabs.getTabs()).extracting(BSTab::getName).containsExactlyElementsOf(TAB_NAMES);
    }

    @Test
    void theTabGroupReplacesTheStackOfRows() {
        // then — the content row holds the tab group and nothing else: a stray fieldSet here would be
        // the stacked layout growing back
        assertThat(colsOf(grid.getRows().get(1)))
                .as("the content column spans the page")
                .singleElement()
                .satisfies(content -> {
                    assertThat(content.getSpan()).isEqualTo(12);
                    assertThat(content.getTabGroups()).as("one tab group").hasSize(1);
                    assertThat(content.getFieldSets()).as("no fieldSet outside the tabs").isEmpty();
                    assertThat(content.getRows()).as("no nested row outside the tabs").isEmpty();
                    assertThat(content.getCollections()).isEmpty();
                });
    }

    @Test
    void everyTabCarriesItsOwnFullWidthFieldSet() {
        // then — a tab is not decoration: each holds its members in a span-12 column, because the width
        // of that column is the only thing that makes a diagram panel readable
        assertThat(tabGroup().getTabs()).allSatisfy(tab -> {
            assertThat(tab.getRows()).as("%s: one row", tab.getName()).hasSize(1);
            assertThat(colsOf(tab.getRows().getFirst()))
                    .as("%s: one span-12 column", tab.getName())
                    .singleElement()
                    .satisfies(col -> {
                        assertThat(col.getSpan()).isEqualTo(12);
                        assertThat(col.getFieldSets()).as("%s: one fieldSet", tab.getName()).hasSize(1);
                    });
        });
    }

    @Test
    void eachTabShowsTheMembersItIsNamedFor() {
        // when — fieldSet id → property ids, across all three tabs
        final Map<String, List<String>> actual = fieldSets().stream()
                .collect(Collectors.toMap(
                        FieldSet::getId,
                        fs -> fs.getProperties().stream().map(PropertyLayoutData::getId).toList(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        // then — exactly the three fieldsets, each holding exactly the properties it renders
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(PROPERTIES_BY_FIELDSET);
    }

    @Test
    void theMarkersCausewayRequiresAreStillPresent() {
        // then — without unreferencedCollections somewhere the whole grid is DISCARDED at boot and the
        // span-4 fallback is rendered instead; without unreferencedProperties the hidden memento
        // properties (domainModel, kind, subDomainName) have nowhere to go and the grid is rejected too
        final long collectionMarkers = allCols().stream().filter(BSCol::isUnreferencedCollections).count()
                + (tabGroup().isUnreferencedCollections() ? 1 : 0);
        assertThat(collectionMarkers)
                .as("exactly one column or tab group absorbs the unreferenced collections")
                .isEqualTo(1);

        assertThat(fieldSets()).filteredOn(FieldSet::isUnreferencedProperties)
                .as("exactly one fieldSet absorbs the unreferenced (hidden memento) properties")
                .hasSize(1);
        assertThat(fieldSets().stream().filter(FieldSet::isUnreferencedProperties)
                .map(FieldSet::getId).findFirst().orElseThrow())
                .as("the marker rides on the always-present tab, so the tab count cannot shift")
                .isEqualTo("content");
    }

    private static BSTabGroup tabGroup() {
        return colsOf(grid.getRows().get(1)).getFirst().getTabGroups().getFirst();
    }

    /** The single fieldSet each tab wraps, in tab order. */
    private static List<FieldSet> fieldSets() {
        return tabGroup().getTabs().stream()
                .map(tab -> colsOf(tab.getRows().getFirst()).getFirst().getFieldSets().get(0))
                .toList();
    }

    private static List<BSCol> colsOf(final BSRow row) {
        return row.getRowContents().stream()
                .filter(BSCol.class::isInstance)
                .map(BSCol.class::cast)
                .toList();
    }

    private static List<BSCol> allCols() {
        return grid.getRows().stream().flatMap(row -> colsOf(row).stream()).toList();
    }
}
