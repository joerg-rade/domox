package domox.dom.rdf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import domox.dom.rdf.geometry.Box;
import domox.dom.rdf.geometry.Pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit-level guardrail for {@link PortLayout} — where each connector attaches on its box, and where an
 * inheritance trunk merges into its super-class.
 * <p>
 * The end-to-end {@code UmlLayoutDiagramsTest} sees only the two ports it asserts on, so the spreading
 * along a shared edge, the reservation a generalization makes, the trunk placement and the shortening
 * when a box is in the way are pinned here against plain {@link Box}es and {@link Link}s.
 */
@DisplayName("PortLayout — where connectors attach, and where the inheritance trunk lands")
class PortLayoutTest {

    private static final Box BOX = new Box(0, 0, 100, 60);

    private static Pt p(final double x, final double y) {
        return new Pt(x, y);
    }

    private static Link association(final String source, final String target, final Pt... points) {
        return new Link(source, target, Link.ASSOCIATION, "", List.of(points), false);
    }

    private static Link generalization(final String superId, final String subId) {
        return new Link(superId, subId, Link.GENERALIZATION, "", List.of(), false);
    }

    // -- portFor ---------------------------------------------------------------

    @Test
    @DisplayName("a spread port wins over the connector's own hint")
    void aSpreadPortWinsOverTheHint() {
        final Map<Integer, Pt> spread = Map.of(6, p(100, 20));

        assertThat(PortLayout.portFor(spread, 3, true, BOX, p(0, 30))).isEqualTo(p(100, 20));
    }

    @Test
    @DisplayName("a link's two ends have separate spread keys")
    void aLinksTwoEndsHaveSeparateSpreadKeys() {
        final Map<Integer, Pt> spread = Map.of(7, p(100, 40));

        assertThat(PortLayout.portFor(spread, 3, false, BOX, p(0, 30))).isEqualTo(p(100, 40));
    }

    @Test
    @DisplayName("without a spread port the hint is snapped to the middle of the edge it is nearest")
    void withoutASpreadPortTheHintIsSnappedToTheEdgeMiddle() {
        assertThat(PortLayout.portFor(Map.of(), 0, true, BOX, p(98, 5)))
                .as("the hint is nearest the right edge, so it takes that edge's middle")
                .isEqualTo(p(100, 30));
    }

    @Test
    @DisplayName("without a spread port or a hint the router picks the port itself")
    void withoutASpreadPortOrAHintThereIsNoPort() {
        assertThat(PortLayout.portFor(Map.of(), 0, true, BOX, null)).isNull();
    }

    // -- hasRoomToRise ---------------------------------------------------------

    @Test
    @DisplayName("a trunk below the port needs no room to rise")
    void aTrunkBelowThePortAlwaysHasRoom() {
        assertThat(PortLayout.hasRoomToRise(p(50, 100), p(50, 200))).isTrue();
        assertThat(PortLayout.hasRoomToRise(p(50, 100), p(50, 100)))
                .as("level with the port counts as no rise").isTrue();
    }

    @Test
    @DisplayName("a trunk exactly the departure stub above the port has room")
    void aTrunkExactlyTheStubAboveHasRoom() {
        assertThat(PortLayout.hasRoomToRise(p(50, 100), p(50, 80))).isTrue();
    }

    @Test
    @DisplayName("a trunk nearer than the departure stub leaves no room")
    void aTrunkNearerThanTheStubLeavesNoRoom() {
        assertThat(PortLayout.hasRoomToRise(p(50, 100), p(50, 81))).isFalse();
    }

    // -- trunkPoint ------------------------------------------------------------

    @Test
    @DisplayName("with nothing in the way the trunk reaches the full stub along the outward normal")
    void trunkReachesTheFullStubWhenNothingIsInTheWay() {
        assertThat(PortLayout.trunkPoint(BOX, p(50, 60), List.of()))
                .as("the bottom edge's normal points down").isEqualTo(p(50, 84));
        assertThat(PortLayout.trunkPoint(BOX, p(50, 0), List.of()))
                .as("the top edge's normal points up").isEqualTo(p(50, -24));
        assertThat(PortLayout.trunkPoint(BOX, p(0, 30), List.of()))
                .as("the left edge's normal points left").isEqualTo(p(-24, 30));
        assertThat(PortLayout.trunkPoint(BOX, p(100, 30), List.of()))
                .as("the right edge's normal points right").isEqualTo(p(124, 30));
    }

    @Test
    @DisplayName("a box in the way shortens the stub, leaving the gap clear")
    void aBoxInTheWayShortensTheStub() {
        // a box 10 below the bottom edge: the stub stops 4 short of it
        assertThat(PortLayout.trunkPoint(BOX, p(50, 60), List.of(new Box(0, 70, 100, 60))))
                .isEqualTo(p(50, 66));
    }

    @Test
    @DisplayName("a box so close that the gap would vanish leaves the minimum stub")
    void aBoxTooCloseLeavesTheMinimumStub() {
        // a box 3 below: 3 - 4 would be negative, so the stub floors at 2
        assertThat(PortLayout.trunkPoint(BOX, p(50, 60), List.of(new Box(0, 63, 100, 60))))
                .isEqualTo(p(50, 62));
    }

    @Test
    @DisplayName("a box the ray misses does not shorten the stub")
    void aBoxTheRayMissesDoesNotShortenTheStub() {
        // the box is to the side, outside the attach point's column
        assertThat(PortLayout.trunkPoint(BOX, p(50, 60), List.of(new Box(200, 70, 100, 60))))
                .isEqualTo(p(50, 84));
    }

    @Test
    @DisplayName("a box behind the attach point does not shorten the stub")
    void aBoxBehindTheAttachPointDoesNotShortenTheStub() {
        // above the bottom edge: the ray runs away from it
        assertThat(PortLayout.trunkPoint(BOX, p(50, 60), List.of(new Box(0, 0, 100, 40))))
                .isEqualTo(p(50, 84));
    }

    // -- facingEdgeMidpoint ----------------------------------------------------

    @Test
    @DisplayName("a super-class with its sub-classes below gets a trunk into its bottom edge")
    void subClassesBelowPutTheTrunkOnTheBottomEdge() {
        final Box superBox = new Box(0, 0, 100, 60);
        final Map<String, Box> boxes = Map.of("S", superBox, "C", new Box(0, 200, 100, 60));

        assertThat(PortLayout.facingEdgeMidpoint(superBox, List.of(generalization("S", "C")), "S", boxes))
                .isEqualTo(p(50, 60));
    }

    @Test
    @DisplayName("a super-class with its sub-classes above gets a trunk into its top edge")
    void subClassesAbovePutTheTrunkOnTheTopEdge() {
        final Box superBox = new Box(0, 200, 100, 60);
        final Map<String, Box> boxes = Map.of("S", superBox, "C", new Box(0, 0, 100, 60));

        assertThat(PortLayout.facingEdgeMidpoint(superBox, List.of(generalization("S", "C")), "S", boxes))
                .isEqualTo(p(50, 200));
    }

    @Test
    @DisplayName("sub-classes level with the box put the trunk on the side they are on")
    void subClassesLevelWithTheBoxPutTheTrunkOnTheSide() {
        final Box superBox = new Box(0, 0, 100, 60);

        assertThat(PortLayout.facingEdgeMidpoint(superBox, List.of(generalization("S", "C")), "S",
                Map.of("S", superBox, "C", new Box(400, 0, 100, 60))))
                .as("they are to the right").isEqualTo(p(100, 30));

        assertThat(PortLayout.facingEdgeMidpoint(superBox, List.of(generalization("S", "C")), "S",
                Map.of("S", superBox, "C", new Box(-400, 0, 100, 60))))
                .as("they are to the left").isEqualTo(p(0, 30));
    }

    @Test
    @DisplayName("a wide fan of sub-classes to one side still gets a vertical trunk")
    void aWideFanToTheSideStillGetsAVerticalTrunk() {
        // the horizontal offset is larger, but the sub-classes are clearly below: the vertical wins
        final Box superBox = new Box(0, 0, 100, 60);
        final Map<String, Box> boxes = Map.of(
                "S", superBox,
                "C1", new Box(600, 300, 100, 60),
                "C2", new Box(800, 300, 100, 60));

        assertThat(PortLayout.facingEdgeMidpoint(superBox,
                List.of(generalization("S", "C1"), generalization("S", "C2")), "S", boxes))
                .as("every sub-class is underneath, so the trunk enters the bottom edge")
                .isEqualTo(p(50, 60));
    }

    @Test
    @DisplayName("a sub-class with no box is ignored")
    void aSubClassWithoutABoxIsIgnored() {
        final Box superBox = new Box(0, 0, 100, 60);
        final Map<String, Box> boxes = Map.of("S", superBox, "C", new Box(0, 200, 100, 60));

        assertThat(PortLayout.facingEdgeMidpoint(superBox,
                List.of(generalization("S", "MISSING"), generalization("S", "C")), "S", boxes))
                .isEqualTo(p(50, 60));
    }

    @Test
    @DisplayName("a link that is not a generalization into this super-class is ignored")
    void linksThatAreNotGeneralizationsIntoTheSuperClassAreIgnored() {
        final Box superBox = new Box(0, 0, 100, 60);
        final Map<String, Box> boxes = Map.of("S", superBox, "C", new Box(0, 200, 100, 60));

        final List<Link> links = List.of(
                association("S", "C", p(0, 0), p(0, 0)),   // the right pair, the wrong kind
                generalization("OTHER", "C"),              // the right kind, the wrong super-class
                generalization("S", "C"));

        assertThat(PortLayout.facingEdgeMidpoint(superBox, links, "S", boxes)).isEqualTo(p(50, 60));
    }

    // -- spreadPorts -----------------------------------------------------------

    @Test
    @DisplayName("two associations sharing an edge are spread evenly along it")
    void twoAssociationsSharingAnEdgeAreSpreadAlongIt() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60), "B", new Box(300, 0, 100, 60));
        final List<Link> links = List.of(
                association("B", "A", p(100, 30), p(300, 30)),
                association("B", "A", p(100, 30), p(300, 30)));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(links, boxes);

        assertThat(ports.get(0)).as("the first link's start end").isEqualTo(p(100, 20));
        assertThat(ports.get(1)).as("the first link's end end").isEqualTo(p(300, 20));
        assertThat(ports.get(2)).as("the second link's start end").isEqualTo(p(100, 40));
        assertThat(ports.get(3)).as("the second link's end end").isEqualTo(p(300, 40));
    }

    @Test
    @DisplayName("three connectors on one edge are spread in the order the model gave them")
    void threeConnectorsOnOneEdgeAreSpreadInModelOrder() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60), "B", new Box(300, 0, 100, 60));
        final List<Link> links = List.of(
                association("B", "A", p(100, 30), p(300, 30)),
                association("B", "A", p(100, 30), p(300, 30)),
                association("B", "A", p(100, 30), p(300, 30)));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(links, boxes);

        assertThat(List.of(ports.get(0), ports.get(2), ports.get(4)))
                .as("A's right edge, in model order").containsExactly(p(100, 15), p(100, 30), p(100, 45));
        assertThat(List.of(ports.get(1), ports.get(3), ports.get(5)))
                .as("B's left edge, likewise").containsExactly(p(300, 15), p(300, 30), p(300, 45));
    }

    @Test
    @DisplayName("a lone association takes the middle of its edge")
    void aLoneAssociationTakesTheMiddleOfItsEdge() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60), "B", new Box(300, 0, 100, 60));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(
                List.of(association("B", "A", p(100, 30), p(300, 30))), boxes);

        assertThat(ports.get(0)).isEqualTo(p(100, 30));
        assertThat(ports.get(1)).isEqualTo(p(300, 30));
    }

    @Test
    @DisplayName("an end whose hint is nowhere near its box falls back to the edge facing the other box")
    void anEndWithAnOffBoxHintFallsBackToTheFacingEdge() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60), "B", new Box(300, 0, 100, 60));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(
                List.of(association("B", "A", p(1000, 1000), p(1000, 1000))), boxes);

        assertThat(ports.get(0)).as("A's right edge faces B").isEqualTo(p(100, 30));
        assertThat(ports.get(1)).as("B's left edge faces A").isEqualTo(p(300, 30));
    }

    @Test
    @DisplayName("an end with no stored path at all falls back to the edge facing the other box")
    void anEndWithNoStoredPathFallsBackToTheFacingEdge() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60), "B", new Box(300, 0, 100, 60));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(List.of(association("B", "A")), boxes);

        assertThat(ports.get(0)).isEqualTo(p(100, 30));
        assertThat(ports.get(1)).isEqualTo(p(300, 30));
    }

    @Test
    @DisplayName("an association on an edge a generalization reserved is pushed clear of the middle")
    void anAssociationOnAReservedEdgeIsPushedClearOfTheMiddle() {
        final Map<String, Box> boxes = Map.of(
                "S", new Box(0, 0, 100, 60),
                "C", new Box(-200, 200, 100, 60),
                "A", new Box(600, 0, 100, 60));

        // the generalization into S reserves the middle of S's bottom edge
        final List<Link> links = List.of(
                generalization("S", "C"),
                association("S", "A", p(600, 30), p(50, 60)));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(links, boxes);

        assertThat(ports).as("the generalization itself takes no port").doesNotContainKeys(0, 1);
        assertThat(ports.get(3).x()).as("the association's end is pushed off the middle")
                .isCloseTo(55, within(1e-9));
        assertThat(ports.get(3).y()).isEqualTo(60);
        assertThat(ports.get(2)).as("its end on A is unaffected").isEqualTo(p(600, 30));
    }

    @Test
    @DisplayName("without a generalization on that edge the association takes the middle")
    void withoutAReservationTheAssociationTakesTheMiddle() {
        final Map<String, Box> boxes = Map.of("S", new Box(0, 0, 100, 60), "A", new Box(600, 0, 100, 60));

        final Map<Integer, Pt> ports = PortLayout.spreadPorts(
                List.of(association("S", "A", p(600, 30), p(50, 60))), boxes);

        assertThat(ports.get(0)).as("the start end, on A's left edge").isEqualTo(p(600, 30));
        assertThat(ports.get(1)).as("nothing reserved the middle, so it lands there")
                .isEqualTo(p(50, 60));
    }

    @Test
    @DisplayName("a link whose boxes are not in the map contributes nothing")
    void aLinkWithNoBoxesContributesNothing() {
        final Map<String, Box> boxes = Map.of("A", new Box(0, 0, 100, 60));

        assertThat(PortLayout.spreadPorts(
                List.of(association("MISSING", "MISSING", p(0, 0), p(0, 0))), boxes)).isEmpty();
    }

    @Test
    @DisplayName("no connectors at all means no ports")
    void noConnectorsMeansNoPorts() {
        assertThat(PortLayout.spreadPorts(List.of(), Map.of("A", BOX))).isEmpty();
    }
}
