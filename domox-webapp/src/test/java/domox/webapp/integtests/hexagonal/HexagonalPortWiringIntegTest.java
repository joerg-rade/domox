package domox.webapp.integtests.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;

import domox.adapter.diagram.KrokiDiagramAdapter;
import domox.adapter.nlp.StanfordCoreNlpAdapter;
import domox.dom.crc.CandidateDiagrams;
import domox.dom.nlp.Sentences;
import domox.dom.rqm.Corpora;
import domox.dom.rqm.Documents;
import domox.port.DiagramPort;
import domox.port.NlpPort;
import domox.webapp.integtests.ApplicationIntegTestAbstract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the hexagonal-architecture wiring: the webapp must provide Spring beans for the
 * domain-declared ports ({@link DiagramPort}, {@link NlpPort}), implemented by the adapter
 * module.
 * <p>
 * Regression guard for the failure where the Spring context failed to boot with
 * "<em>Parameter 0 of constructor in {@link CandidateDiagrams} required a bean of type
 * {@code domox.port.DiagramPort} that could not be found</em>": once {@code domox-domain}
 * stopped depending on {@code domox-adapter}, the webapp had to declare that dependency
 * explicitly (it used to arrive transitively). Booting the full context here is the
 * real-world check — a missing or mis-wired port aborts the context before any test body runs.
 */
@Transactional
class HexagonalPortWiringIntegTest extends ApplicationIntegTestAbstract {

    @Autowired
    private DiagramPort diagramPort;

    @Autowired
    private NlpPort nlpPort;

    @Autowired
    private CandidateDiagrams candidateDiagrams;

    @Autowired
    private Documents documents;

    @Autowired
    private Sentences sentences;

    @Autowired
    private Corpora corpora;

    @Test
    void diagramPort_isWiredToKrokiDiagramAdapter() {
        // the domain port must resolve to the adapter implementation, not be absent
        assertThat(diagramPort).isInstanceOf(KrokiDiagramAdapter.class);
    }

    @Test
    void nlpPort_isWiredToStanfordCoreNlpAdapter() {
        assertThat(nlpPort).isInstanceOf(StanfordCoreNlpAdapter.class);
    }

    @Test
    void domainServices_dependingOnPorts_areManagedBeans() {
        // CandidateDiagrams/Documents/Sentences/Corpora are the domain services that inject
        // the ports; if the wiring is broken the context would fail before these assertions.
        assertThat(candidateDiagrams).isNotNull();
        assertThat(documents).isNotNull();
        assertThat(sentences).isNotNull();
        assertThat(corpora).isNotNull();
    }

    }
