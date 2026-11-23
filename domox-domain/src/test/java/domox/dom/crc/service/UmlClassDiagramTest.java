package domox.dom.crc.service;

import domox.dom.crc.model.DomainModel;
import org.apache.causeway.applib.value.Blob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The view model the diagram actions return: the rendered picture, the PlantUML it was rendered
 * from, and what the page shows when the render failed.
 *
 * <p>The re-derivation fallback ({@code ensureDerived}, which needs Causeway's service injection)
 * is not exercised here — a unit test cannot inject {@link DomainModels} into the view model.</p>
 */
class UmlClassDiagramTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    @Test
    void titleSaysWhichDiagramOfWhichModelItIs() {
        // given
        final DomainModel model = new DomainModel();

        // when / then
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_MODEL, null).title())
                .as("the whole model").contains("whole model");
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_SUB_DOMAIN, "Inventory").title())
                .as("one sub-domain, by name").contains("sub-domain Inventory");
        assertThat(UmlClassDiagram.of(model, UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW, null).title())
                .as("the overview").contains("sub-domain overview");
    }

    @Test
    void titleFallsBackWhenThereIsNoModel() {
        assertThat(UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null).title())
                .isEqualTo("UML class diagram: Domain model — whole model");
    }

    @Test
    void carriesTheRenderedDiagramAndTheSource() {
        // given
        final Blob pdf = new Blob("domain-model.pdf", "application/pdf", PDF);

        // when
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null)
                .withSource("@startuml\n@enduml")
                .rendered(pdf);

        // then
        assertThat(diagram.getDiagram()).isSameAs(pdf);
        assertThat(diagram.getPlantUml()).isEqualTo("@startuml\n@enduml");
        assertThat(diagram.getMessage()).as("nothing to report").isNull();
        assertThat(diagram.hideMessage()).isTrue();
    }

    @Test
    void keepsTheSourceWhenTheRenderFails() {
        // when
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null)
                .withSource("@startuml\n@enduml")
                .failed("Kroki unreachable");

        // then — the picture is gone, the source is not, and the reason is shown
        assertThat(diagram.getDiagram()).isNull();
        assertThat(diagram.getPlantUml()).isEqualTo("@startuml\n@enduml");
        assertThat(diagram.getMessage()).isEqualTo("Kroki unreachable");
        assertThat(diagram.hideMessage()).isFalse();
    }

    @Test
    void changingTheKindInvalidatesTheRenderedDiagram() {
        // given
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, UmlClassDiagram.KIND_MODEL, null)
                .withSource("@startuml\n@enduml")
                .rendered(new Blob("d.pdf", "application/pdf", PDF));

        // when
        diagram.setKind(UmlClassDiagram.KIND_SUB_DOMAIN_OVERVIEW);

        // then — the cached picture must not outlive the key it was rendered for
        assertThat(diagram.getDiagram()).isNull();
        assertThat(diagram.getPlantUml()).isNull();
    }

    @Test
    void aFreshlyMementoedViewModelRendersNothingAndSaysNothing() {
        // given — what a page reload reconstructs: the memento keys only, no picture
        final UmlClassDiagram diagram = UmlClassDiagram.of(null, null, null);

        // when / then — no diagram, no source, no message (and no null-pointer on the way)
        assertThat(diagram.getDiagram()).isNull();
        assertThat(diagram.getPlantUml()).isNull();
        assertThat(diagram.getMessage()).isNull();
        assertThat(diagram.hideMessage()).isTrue();
        assertThat(diagram.title()).as("an unset kind reads as the whole model").contains("whole model");
    }
}
