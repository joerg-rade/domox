package domox.dom.rdf;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for {@link UmlDiagramLayout} — the <b>force-simulated</b> D3 payload an
 * <em>assembled</em> diagram is drawn from.
 * <p>
 * This is the sibling of {@code UmlLayoutDiagramsTest}: that one pins the reconstruction, which reads
 * geometry an OntoUML export <em>stores</em>, while this one pins the diagram that has no geometry to
 * read — a list of class names and a list of relations, seeded onto a grid and handed to the client's
 * {@code d3-force} simulation.  The three things a caller can get wrong are all here: <em>where</em> the
 * seed puts a box (grid, bands, member-height), <em>what</em> a box carries (members, archetype) and what
 * a connector carries (no route at all, an arrowhead only on an is-a edge).
 * <p>
 * Assertions read the JSON through {@link #nodeValue} rather than by whole-string equality, so a test
 * names the one fact it pins.  The <em>exact</em> widened width belongs to {@code LabelLayoutTest} and
 * {@code LabelMetricsTest}; here it is enough that a long label widens its box and a short one does not.
 */
@DisplayName("UmlDiagramLayout — the force-simulated D3 payload")
class UmlDiagramLayoutTest {

    /** The fixed box width of {@code UmlDiagramLayout} — stated as data, so a change is a visible edit. */
    private static final int BOX_W = 180;

    /** The box height with no members. */
    private static final int BOX_H = 40;

    /** One node of the payload, as the tests build them. */
    private static UmlDiagramLayout.Node node(final String id, final String name, final String group) {
        return new UmlDiagramLayout.Node(id, name, group);
    }

    /** One node carrying members, with no archetype. */
    private static UmlDiagramLayout.Node node(final String id, final String name, final String group,
                                              final List<String> attributes, final List<String> methods) {
        return new UmlDiagramLayout.Node(id, name, group, attributes, methods, null, null);
    }

    /**
     * The JSON object of the node whose id is {@code id}, read out of the payload.
     * <p>
     * A node body holds no {@code }} of its own (the member arrays are strings), so the body can be
     * delimited by the first one.
     */
    private static String nodeBody(final String json, final String id) {
        final Matcher body = Pattern.compile("\\{\"id\":\"" + Pattern.quote(id) + "\"[^}]*}").matcher(json);
        assertThat(body.find()).as("the payload has a node with id %s", id).isTrue();
        return body.group();
    }

    /**
     * The {@code key} value of the node whose id is {@code id}, read out of the payload — an array or a
     * scalar, whichever {@code key} is.
     */
    private static String nodeValue(final String json, final String id, final String key) {
        final Matcher value = Pattern.compile("\"" + key + "\":(\\[[^\\]]*]|[^,}]*)")
                .matcher(nodeBody(json, id));
        assertThat(value.find()).as("node %s carries %s", id, key).isTrue();
        return value.group(1);
    }

    /** How many times {@code needle} occurs in the payload. */
    private static int count(final String json, final String needle) {
        int seen = 0;
        for (int at = json.indexOf(needle); at >= 0; at = json.indexOf(needle, at + needle.length())) {
            seen++;
        }
        return seen;
    }

    /**
     * The payload is the force flavour, and it carries a <em>seed</em>: a rectangle per box and
     * <b>no route</b>.  A route computed here would be stale the moment the client's first tick ran.
     */
    @Test
    void rendersTheForceFlavourWithASeedAndNoRoutes() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "one"), node("b", "Beta", "one")),
                List.of(new UmlDiagramLayout.Edge("a", "b", UmlDiagramLayout.GENERALIZATION, "is a")));

        assertThat(json).startsWith("{\"layout\":\"uml\",\"force\":true,");
        assertThat(json).contains("\"title\":\"Demo\"");
        assertThat(json).as("the connectors carry no points at all — only the box seed has geometry")
                .doesNotContain("\"points\"");
        assertThat(nodeValue(json, "a", "x")).isEqualTo("24");
        assertThat(nodeValue(json, "a", "y")).isEqualTo("24");
        assertThat(nodeValue(json, "a", "w")).isEqualTo("180");
        assertThat(nodeValue(json, "a", "h")).isEqualTo("40");
        assertThat(nodeValue(json, "a", "kind")).isEqualTo("\"Class\"");
    }

    /**
     * The seed grid: {@code ceil(sqrt(n))} columns {@code 252} units apart (box + gap), and <b>a change of
     * group starts a new band</b> one empty row below — which is what makes a model's sub-domains read as
     * separate blocks rather than as one interleaved soup.
     */
    @Test
    void seedsTheBoxesOnAGridAndStartsANewBandWhenTheGroupChanges() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "one"), node("b", "Beta", "one"), node("c", "Gamma", "two")),
                List.of());

        assertThat(nodeValue(json, "a", "x")).isEqualTo("24");              // first column, first band
        assertThat(nodeValue(json, "a", "y")).isEqualTo("24");
        assertThat(nodeValue(json, "b", "x")).isEqualTo("276");             // second column: 0 + 252
        assertThat(nodeValue(json, "b", "y")).isEqualTo("24");              // same row
        assertThat(nodeValue(json, "c", "x")).isEqualTo("24");              // a new band restarts the column
        assertThat(nodeValue(json, "c", "y")).as("one empty 40-unit row below the band's tallest box, +80")
                .isEqualTo("264");
        // 2 columns of 180 with a 252 step = 432 wide, 40 + 80 + 40 + 80 tall, plus the padding either side.
        assertThat(json).contains("\"canvas\":{\"width\":480,\"height\":328}");
    }

    /** A box is made tall enough for its compartments — and <b>no taller</b> when it holds nothing. */
    @Test
    void growsABoxToHoldItsMemberCompartments() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g"),
                        node("b", "Beta", "g", List.of("id: Long", "name: String"), List.of()),
                        node("c", "Gamma", "g", List.of(), List.of("approve()")),
                        node("d", "Delta", "g", List.of("id: Long"), List.of("approve()"))),
                List.of());

        assertThat(nodeValue(json, "a", "h")).as("no members: the plain 40-unit box").isEqualTo("40");
        assertThat(nodeValue(json, "b", "h")).as("8 + 2x12 of attributes below a 20-unit name").isEqualTo("52");
        assertThat(nodeValue(json, "c", "h")).as("one method: 20 + 8 + 12, still under the 40-unit minimum")
                .isEqualTo("40");
        assertThat(nodeValue(json, "d", "h")).as("both compartments: 20 + 20 + 20").isEqualTo("60");
        assertThat(nodeValue(json, "b", "attributes")).isEqualTo("[\"id: Long\",\"name: String\"]");
        assertThat(nodeValue(json, "b", "methods")).isEqualTo("[]");
        assertThat(nodeValue(json, "c", "attributes")).isEqualTo("[]");
        assertThat(nodeValue(json, "c", "methods")).isEqualTo("[\"approve()\"]");
    }

    /** A class name too wide for the fixed box widens it — to the right when there is room. */
    @Test
    void widensABoxToFitItsLabel() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "ServiceProviderNegotiationStrategy", "g")), List.of());

        assertThat(nodeValue(json, "a", "x")).as("there is room to the right, so the box does not move")
                .isEqualTo("24");
        assertThat(Double.parseDouble(nodeValue(json, "a", "w")))
                .as("the box grew past its fixed width to fit the label").isGreaterThan(BOX_W);
    }

    /** A short name leaves the box at its fixed width — widening is the exception, not the rule. */
    @Test
    void leavesAShortLabelledBoxAtItsFixedWidth() {
        final String json = UmlDiagramLayout.render("Demo", List.of(node("a", "A", "g")), List.of());

        assertThat(nodeValue(json, "a", "w")).isEqualTo(String.valueOf(BOX_W));
        assertThat(nodeValue(json, "a", "h")).isEqualTo(String.valueOf(BOX_H));
    }

    /**
     * A connector is an is-a edge or it is not: every generalization carries its own arrowhead (there is
     * no trunk for several to merge into, because there is no route), and an association carries none.
     * A missing kind reads as an association and a missing label as an empty string, so the client never
     * sees a {@code null}.
     */
    @Test
    void marksOnlyGeneralizationsWithAnArrowhead() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g"), node("b", "Beta", "g")),
                Arrays.asList(new UmlDiagramLayout.Edge("a", "b", UmlDiagramLayout.GENERALIZATION, "is a"),
                        new UmlDiagramLayout.Edge("a", "b", null, null),
                        null));

        assertThat(json).contains("\"links\":["
                + "{\"source\":\"a\",\"target\":\"b\",\"kind\":\"generalization\",\"label\":\"is a\","
                + "\"arrow\":true},"
                + "{\"source\":\"a\",\"target\":\"b\",\"kind\":\"association\",\"label\":\"\","
                + "\"arrow\":false}]");
    }

    /** An edge to a class the diagram does not draw is dropped, not drawn into empty space. */
    @Test
    void dropsAnEdgeWhoseEndpointIsNotDrawn() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g")),
                List.of(new UmlDiagramLayout.Edge("a", "ghost", UmlDiagramLayout.ASSOCIATION, "x")));

        assertThat(json).contains("\"links\":[]");
        assertThat(json).doesNotContain("ghost");
    }

    /**
     * A row holds {@code ceil(sqrt(n))} boxes and then wraps — the wrap is what keeps a large model's
     * canvas square-ish instead of one very long strip, so it is pinned rather than left to the arithmetic.
     */
    @Test
    void wrapsToANewRowWhenARowIsFull() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g"), node("b", "Beta", "g"), node("c", "Gamma", "g")), List.of());

        assertThat(nodeValue(json, "a", "x")).isEqualTo("24");               // first column
        assertThat(nodeValue(json, "b", "x")).isEqualTo("276");              // second column
        assertThat(nodeValue(json, "c", "x")).as("ceil(sqrt(3)) = 2 columns, so the third box wraps")
                .isEqualTo("24");
        assertThat(nodeValue(json, "c", "y")).as("one 40-unit row below the first, +80 clear")
                .isEqualTo("144");
    }

    /**
     * A band that ends <b>mid-row</b> closes its half-filled row before the empty row that separates the
     * bands — otherwise the next band would be seeded on top of the previous band's last boxes.
     */
    @Test
    void closesAHalfFilledRowWhenABandEndsMidRow() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "one"), node("b", "Beta", "two")), List.of());

        assertThat(nodeValue(json, "b", "x")).as("a new band restarts at the first column").isEqualTo("24");
        assertThat(nodeValue(json, "b", "y")).as("the half-filled row (40+80), then a whole one (40+80)")
                .isEqualTo("264");
    }

    /**
     * The boxes are emitted in label order, whatever order they were placed in — so a box's z-order (and
     * the order a reader scans them in) does not depend on how the model happened to list its classes.
     */
    @Test
    void emitsTheBoxesInLabelOrder() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("z", "Zulu", "g"), node("a", "Alpha", "g")), List.of());

        assertThat(json.indexOf("\"id\":\"a\"")).isLessThan(json.indexOf("\"id\":\"z\""));
    }

    /**
     * Two classes can share a display label, so the order has to be <em>total</em>: equal labels are broken
     * by id, which is what makes the emitted order reproducible rather than dependent on the sort being
     * stable.
     */
    @Test
    void breaksATieBetweenEqualLabelsByTheirId() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("b", "Same", "g"), node("a", "Same", "g")), List.of());

        assertThat(json.indexOf("\"id\":\"a\"")).as("same label, so the id decides").isLessThan(
                json.indexOf("\"id\":\"b\""));
    }

    /**
     * The legend explains the box and the two line kinds, and then <b>one row per archetype the diagram
     * actually draws</b> — so it can neither name an archetype no box uses nor omit one that is on screen,
     * and the swatch is the very fill the box carries.
     */
    @Test
    void addsOneLegendRowPerArchetypeActuallyDrawn() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(new UmlDiagramLayout.Node("a", "Alpha", "g", List.of(), List.of(), "Place", "#c8e6c9"),
                        node("b", "Beta", "g"),
                        new UmlDiagramLayout.Node("c", "Gamma", "g", List.of(), List.of(), "Party", "#ffe0b2"),
                        new UmlDiagramLayout.Node("d", "Delta", "g", List.of(), List.of(), "Party", "#ffe0b2")),
                List.of());

        assertThat(json).contains("\"legend\":[{\"kind\":\"node\",");
        assertThat(count(json, "\"kind\":\"archetype\"")).as("Party is drawn twice but explained once")
                .isEqualTo(2);
        assertThat(json).contains("{\"kind\":\"archetype\",\"color\":\"#c8e6c9\",\"label\":\"Place\","
                + "\"description\":\"a Coad/Lefebvre archetype, which colours the box\"}");
        assertThat(json).contains("{\"kind\":\"archetype\",\"color\":\"#ffe0b2\",\"label\":\"Party\","
                + "\"description\":\"a Coad/Lefebvre archetype, which colours the box\"}");
        assertThat(json.indexOf("\"label\":\"Place\"")).as("in the order the boxes are emitted")
                .isLessThan(json.indexOf("\"label\":\"Party\""));
    }

    /**
     * The two archetype keys are independent — the writer states what the caller gave it — but a
     * <b>legend row needs both</b>, because the client's swatch is the box's own fill: an archetype with no
     * colour would name a swatch no box carries, and a colour with no archetype would draw an unlabelled
     * one.  ({@code ClassType} always supplies the pair together, so this is the writer's total behaviour
     * on a half-stated detail rather than a state the model produces.)
     */
    @Test
    void givesALegendRowOnlyToAnArchetypeThatIsDrawable() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g"),
                        new UmlDiagramLayout.Node("b", "Beta", "g", List.of(), List.of(), "Party", null),
                        new UmlDiagramLayout.Node("c", "Gamma", "g", List.of(), List.of(), null, "#ffe0b2")),
                List.of());

        assertThat(nodeBody(json, "a")).doesNotContain("archetype").doesNotContain("color");
        assertThat(nodeValue(json, "b", "archetype")).as("the caller stated it, so it is serialised")
                .isEqualTo("\"Party\"");
        assertThat(nodeBody(json, "b")).as("but it has no colour to draw with").doesNotContain("color");
        assertThat(nodeValue(json, "c", "color")).isEqualTo("\"#ffe0b2\"");
        assertThat(nodeBody(json, "c")).as("and it has no archetype to name").doesNotContain("archetype");
        assertThat(count(json, "\"kind\":\"archetype\"")).as("neither is a legend row")
                .isZero();
    }

    /** The same id twice is <em>one</em> box — what PlantUML does with two classes of the same name. */
    @Test
    void drawsOneBoxForADuplicateId() {
        final String json = UmlDiagramLayout.render("Demo",
                List.of(node("a", "Alpha", "g"), node("a", "Other", "g")), List.of());

        assertThat(count(json, "\"id\":\"a\"")).isEqualTo(1);
        assertThat(nodeValue(json, "a", "name")).as("the first name given for an id wins")
                .isEqualTo("\"Alpha\"");
    }

    /** A thin model must not make a diagram action fail: an empty diagram is an empty, tiny canvas. */
    @Test
    void toleratesAnEmptyDiagram() {
        final String json = UmlDiagramLayout.render("Empty", List.of(), List.of());

        assertThat(json).contains("\"canvas\":{\"width\":48,\"height\":48}");
        assertThat(json).contains("\"nodes\":[],\"links\":[]");
        assertThat(count(json, "\"kind\":\"archetype\"")).isZero();
    }

    /** A null title and a null node are tolerated — the title is drawn blank and the node is skipped. */
    @Test
    void toleratesANullTitleAndANullNode() {
        final String json = UmlDiagramLayout.render(null, Arrays.asList(null, node("a", "Alpha", "g")), List.of());

        assertThat(json).contains("\"title\":\"\"");
        assertThat(count(json, "\"id\":\"")).isEqualTo(1);
        assertThat(nodeValue(json, "a", "name")).isEqualTo("\"Alpha\"");
    }
}
