package domox.dom.requirements;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * The <b>Corpus</b> aggregate's service: list, create and find the corpus that groups a requirements
 * suite.
 *
 * <p><b>One responsibility — aggregate CRUD.</b>  It changes when the {@link Corpus} entity or its
 * persistence does, and for no other reason.  Everything else that used to live here now has its own
 * service, each with its own axis of change:</p>
 * <ul>
 *   <li>the requirements-ingestion pipeline → {@link CorpusIngestion};</li>
 *   <li>the RDF/OWL ontology diagrams → {@link OntologyDiagrams} (SPARQL text in
 *       {@code domox.dom.rdf.OntologyQueries});</li>
 *   <li>the deletion cascade → {@link CorpusDeletion}.</li>
 * </ul>
 */
@Named(DomainModule.NAMESPACE + ".Corpora")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class Corpora {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;

    @Inject
    public Corpora(RepositoryService repositoryService,
                   FactoryService factoryService) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
    }

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @ActionLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Corpus create(String title) {
        final Corpus obj = factoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        obj.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> findByTitle(final String title) {
        List<Corpus> answer = new ArrayList<>();
        for (Corpus o : listAll()) {
            if (o.getTitle().equals(title)) {
                answer.add(o);
            }
        }
        return answer;
    }
}
