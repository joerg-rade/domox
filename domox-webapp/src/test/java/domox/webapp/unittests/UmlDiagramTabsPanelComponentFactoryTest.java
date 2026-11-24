package domox.webapp.unittests;

import domox.dom.crc.service.UmlClassDiagram;
import domox.dom.crc.service.UmlDiagramSet;
import domox.webapp.config.UmlDiagramTabsPanelComponentFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail for {@link UmlDiagramTabsPanelComponentFactory} — which object pages it takes over.
 *
 * <p><b>Why this needs pinning.</b>  The factory renders a page as one tab per sub-domain, and it applies
 * <em>exclusively</em>: if it applied to the wrong type, that type's page would silently stop showing its
 * own layout.  So both halves matter — the type it takes, and the types it leaves alone.  The
 * single-diagram view model is the one that must be left alone: it is the page the sub-domain's own action
 * opens.</p>
 *
 * <p>No mocks: the rule is a pure function of the object's class ({@code takesOver}), because
 * {@code ManagedObject} — the hop the factory reads it through — is a sealed interface and cannot be
 * mocked.</p>
 */
class UmlDiagramTabsPanelComponentFactoryTest {

    @Test
    void takesOverTheOnePageView() {
        assertThat(UmlDiagramTabsPanelComponentFactory.takesOver(UmlDiagramSet.class))
                .as("the tabbed page takes over the whole object page")
                .isTrue();
    }

    @Test
    void leavesEveryOtherPageAlone() {
        assertThat(UmlDiagramTabsPanelComponentFactory.takesOver(UmlClassDiagram.class))
                .as("the single-diagram page keeps its own layout")
                .isFalse();
        assertThat(UmlDiagramTabsPanelComponentFactory.takesOver(Object.class))
                .as("and so does everything else")
                .isFalse();
    }
}

