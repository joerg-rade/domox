package domox.webapp.integtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.xactn.TransactionService;

import domox.dom.requirements.Corpus;

/**
 * Guards {@link Corpus#getOntologyDiagram()} — the derived (transient) RDF/OWL → D3 property.
 * <p>
 * Proves the injected {@code Corpora} collaborator really is wired on a persisted entity (a
 * {@code null} service would silently yield the empty-graph fallback) and that the property renders
 * the bundled ontology (a UML/OntoUML export) rather than an empty graph.
 */
@Transactional
class CorpusOntologyDiagramIntegTest extends ApplicationIntegTestAbstract {

    @Inject private RepositoryService repositoryService;
    @Inject private TransactionService transactionService;

    @Test
    void ontologyDiagram_rendersTheBundledOntologyAsD3Json() {
        // given — a persisted corpus (Causeway injects @Inject fields on persist/load)
        final Corpus corpus = new Corpus();
        corpus.setTitle("Ontology Diagram");
        corpus.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        repositoryService.persistAndFlush(corpus);
        transactionService.flushTransaction();

        // when
        final String json = corpus.getOntologyDiagram();

        // then — a real D3 payload (not the empty fallback), drawn from the bundled ontology
        assertThat(json).startsWith("{").endsWith("}");
        assertThat(json).contains("\"nodes\":[");
        assertThat(json).contains("\"links\":[");
        assertThat(json).contains("\"legend\":[");
        // the bundled fixture is a UML/OntoUML model: it has real classes with human names
        assertThat(json).contains("\"pos\":\"Class\"");
        assertThat(json).contains("https://w3id.org/ontouml#Class");
        assertThat(json).contains("\"name\":\"Commitment\"");
    }
}
